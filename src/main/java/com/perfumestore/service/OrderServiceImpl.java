package com.perfumestore.service;

import com.perfumestore.aop.LogStoreAction;
import com.perfumestore.dao.CartRepository;
import com.perfumestore.dao.OrderRepository;
import com.perfumestore.dao.ProductRepository;
import com.perfumestore.dto.response.OrderResponse;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.entity.Cart;
import com.perfumestore.entity.CartItem;
import com.perfumestore.entity.Order;
import com.perfumestore.entity.OrderItem;
import com.perfumestore.entity.Product;
import com.perfumestore.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;


@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final DtoMapper dtoMapper;
    private final EntityManager entityManager;

    public OrderServiceImpl(
            CartRepository cartRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            DtoMapper dtoMapper,
            EntityManager entityManager) {

        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.dtoMapper = dtoMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    @LogStoreAction
    public OrderResponse checkout(Long userId) {
        Cart cart = cartRepository.findLockedByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Cart not found."));

        if (cart.getItems().isEmpty()) {
            throw ApiException.conflict("Cannot checkout an empty cart.");
        }

        Set<Long> productIds = new TreeSet<>();

        for (CartItem item : cart.getItems()) {
            productIds.add(item.getProduct().getId());
        }

        Map<Long, Product> lockedProducts = new LinkedHashMap<>();

        for (Long productId : productIds) {
            Product product = productRepository.findLockedById(productId)
                    .orElseThrow(() -> ApiException.notFound("Product not found: " + productId));

            entityManager.refresh(product,LockModeType.PESSIMISTIC_WRITE);
            lockedProducts.put(productId, product);
        }

        for (CartItem item : cart.getItems()) {
            Product product = lockedProducts.get(item.getProduct().getId());

            if (!product.isActive()) {
                throw ApiException.conflict("Product is no longer available: "+ product.getName());
            }

            if (item.getQuantity() < 1|| item.getQuantity() > product.getStockQuantity()) {
                throw ApiException.conflict("Insufficient stock for: " + product.getName());
            }
        }

        Order order = new Order();
        order.setUser(cart.getUser());

        BigDecimal totalAmount = new BigDecimal("0.00");

        for (CartItem item : cart.getItems()) {
            Product product = lockedProducts.get(
                    item.getProduct().getId()
            );

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setQuantity(item.getQuantity());

            order.getItems().add(orderItem);

            BigDecimal lineTotal = product.getPrice().multiply(
                    BigDecimal.valueOf(item.getQuantity())
            );

            totalAmount = totalAmount.add(lineTotal);

            product.setStockQuantity(
                    product.getStockQuantity() - item.getQuantity()
            );
        }

        order.setTotalAmount(totalAmount);

        orderRepository.saveAndFlush(order);

        cart.getItems().clear();
        cartRepository.flush();

        return dtoMapper.order(order);
    }

    @Override
    public PageResponse<OrderResponse> history(
            Long userId,
            int page,
            int size) {

        Pageable pageable = orderPage(page, size);

        Page<OrderResponse> result = orderRepository
                .findByUser_Id(userId, pageable)
                .map(dtoMapper::order);

        return PageResponse.from(result);
    }

    @Override
    public OrderResponse getOwn(Long userId, Long orderId) {
        Order order = orderRepository
                .findByIdAndUser_Id(orderId, userId)
                .orElseThrow(() -> ApiException.notFound(
                        "Order not found."
                ));

        return dtoMapper.order(order);
    }

    @Override
    public PageResponse<OrderResponse> all(int page, int size) {
        Pageable pageable = orderPage(page, size);

        Page<OrderResponse> result = orderRepository
                .findAll(pageable)
                .map(dtoMapper::order);

        return PageResponse.from(result);
    }

    @Override
    public OrderResponse getAny(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound(
                        "Order not found."
                ));

        return dtoMapper.order(order);
    }

    private Pageable orderPage(int page, int size) {
        return PageSupport.request(
                page,
                size,
                "createdAt",
                "desc",
                Set.of("createdAt")
        );
    }
}