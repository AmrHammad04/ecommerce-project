package com.perfumestore.service;

import com.perfumestore.dto.response.CartItemResponse;
import com.perfumestore.dto.response.CartResponse;
import com.perfumestore.dto.response.CategoryResponse;
import com.perfumestore.dto.response.OrderItemResponse;
import com.perfumestore.dto.response.OrderResponse;
import com.perfumestore.dto.response.ProductResponse;
import com.perfumestore.dto.response.ReviewResponse;
import com.perfumestore.dto.response.UserResponse;
import com.perfumestore.entity.Cart;
import com.perfumestore.entity.CartItem;
import com.perfumestore.entity.Category;
import com.perfumestore.entity.Order;
import com.perfumestore.entity.OrderItem;
import com.perfumestore.entity.Product;
import com.perfumestore.entity.Review;
import com.perfumestore.entity.User;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class DtoMapper {

    public UserResponse user(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    public CategoryResponse category(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }

    public ProductResponse product(Product product) {
        List<Category> sortedCategories =
                new ArrayList<>(product.getCategories());

        sortedCategories.sort(
                Comparator.comparing(Category::getId)
        );

        List<CategoryResponse> categories = new ArrayList<>();

        for (Category category : sortedCategories) {
            categories.add(category(category));
        }

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getSizeMl(),
                product.getConcentration(),
                product.isActive(),
                categories
        );
    }

    public CartResponse cart(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();

        BigDecimal totalAmount = new BigDecimal("0.00");

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();

            BigDecimal unitPrice = product.getPrice();

            BigDecimal lineTotal = unitPrice.multiply(
                    BigDecimal.valueOf(item.getQuantity())
            );

            boolean available =
                    product.isActive()
                    && product.getStockQuantity() >= item.getQuantity();

            CartItemResponse response = new CartItemResponse(
                    product.getId(),
                    product.getName(),
                    unitPrice,
                    item.getQuantity(),
                    lineTotal,
                    available
            );

            items.add(response);

            totalAmount = totalAmount.add(lineTotal);
        }

        return new CartResponse(
                cart.getId(),
                items,
                totalAmount
        );
    }

    public OrderResponse order(Order order) {
        List<OrderItemResponse> items = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            BigDecimal lineTotal = item.getUnitPrice().multiply(
                    BigDecimal.valueOf(item.getQuantity())
            );

            OrderItemResponse response = new OrderItemResponse(
                    item.getProduct().getId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    lineTotal
            );

            items.add(response);
        }

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getCreatedAt(),
                order.getTotalAmount(),
                items
        );
    }

    public ReviewResponse review(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getUser().getId(),
                review.getUser().getName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}