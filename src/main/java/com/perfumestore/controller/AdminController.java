package com.perfumestore.controller;

import java.math.BigDecimal;
import com.perfumestore.dto.response.OrderResponse;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.dto.response.ProductResponse;
import com.perfumestore.service.OrderService;
import com.perfumestore.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final OrderService orderService;
    private final ProductService productService;

    public AdminController(
            OrderService orderService,
            ProductService productService) {

        this.orderService = orderService;
        this.productService = productService;
    }

    @GetMapping("/orders")
    public PageResponse<OrderResponse> getAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return orderService.all(page, size);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(
            @PathVariable Long orderId) {

        return orderService.getAny(orderId);
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {

        return productService.list(
                search,
                categoryId,
                minPrice,
                maxPrice,
                inStock,
                page,
                size,
                sort,
                direction,
                true
        );
    }
}