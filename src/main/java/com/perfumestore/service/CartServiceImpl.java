package com.perfumestore.service;

import com.perfumestore.dao.CartRepository;
import com.perfumestore.dao.ProductRepository;
import com.perfumestore.dto.request.CartItemRequest;
import com.perfumestore.dto.request.UpdateCartItemRequest;
import com.perfumestore.dto.response.CartResponse;
import com.perfumestore.entity.Cart;
import com.perfumestore.entity.CartItem;
import com.perfumestore.entity.Product;
import com.perfumestore.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final DtoMapper dtoMapper;

    public CartServiceImpl(
            CartRepository cartRepository,
            ProductRepository productRepository,
            DtoMapper dtoMapper) {

        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public CartResponse get(Long userId) {
        Cart cart = cartRepository.findByUser_Id(userId)
                .orElseThrow(() -> ApiException.notFound(
                        "Cart not found."
                ));

        return dtoMapper.cart(cart);
    }

    @Override 
    @Transactional
    public CartResponse add(Long userId, CartItemRequest request) {
        Cart cart = requireLockedCart(userId);
        Product product = requireActiveProduct(request.getProductId());

        CartItem existingItem = null;

        for (CartItem item : cart.getItems()) {
            if (item.getProduct().getId().equals(product.getId())) {
                existingItem = item;
                break;
            }
        }

        long quantity = request.getQuantity();

        if (existingItem != null) {
            quantity = quantity + existingItem.getQuantity();
        }

        validateQuantity(product, quantity);

        if (existingItem == null) {
            existingItem = new CartItem();
            existingItem.setCart(cart);
            existingItem.setProduct(product);

            cart.getItems().add(existingItem);
        }

        existingItem.setQuantity((int) quantity);
        cartRepository.saveAndFlush(cart);
        return dtoMapper.cart(cart);
    }

    @Override
    @Transactional
    public CartResponse update(
            Long userId,
            Long productId,
            UpdateCartItemRequest request) {

        Cart cart = requireLockedCart(userId);
        CartItem item = requireItem(cart, productId);

        validateQuantity(
                item.getProduct(),
                request.getQuantity()
        );

        item.setQuantity(request.getQuantity());

        return dtoMapper.cart(cart);
    }

    @Override
    @Transactional
    public void remove(Long userId, Long productId) {
        Cart cart = requireLockedCart(userId);
        CartItem item = requireItem(cart, productId);

        cart.getItems().remove(item);
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        Cart cart = requireLockedCart(userId);

        cart.getItems().clear();
    }

    private Cart requireLockedCart(Long userId) {
        return cartRepository.findLockedByUserId(userId)
                .orElseThrow(() -> ApiException.notFound(
                        "Cart not found."
                ));
    }

    private Product requireActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound(
                        "Product not found: " + productId
                ));

        if (!product.isActive()) {
            throw ApiException.notFound("Product not found: " + productId);
        }

        return product;
    }

    private CartItem requireItem(Cart cart, Long productId) {
        for (CartItem item : cart.getItems()) {
            if (item.getProduct().getId().equals(productId)) {
                return item;
            }
        }

        throw ApiException.notFound("Product is not in your cart.");
    }

    private void validateQuantity(Product product, long quantity) {
        if (quantity < 1 || quantity > 1000) {
            throw ApiException.badRequest("Quantity must be between 1 and 1000.");
        }

        if (!product.isActive()) {
            throw ApiException.conflict("Product is no longer available: " + product.getName());
        }

        if (quantity > product.getStockQuantity()) {
            throw ApiException.conflict("Insufficient stock for: " + product.getName());
        }
    }
}