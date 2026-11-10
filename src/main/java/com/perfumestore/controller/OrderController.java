package com.perfumestore.controller;

import com.perfumestore.dto.response.OrderResponse;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.service.AccountService;
import com.perfumestore.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final AccountService accountService;

    public OrderController(
            OrderService orderService,
            AccountService accountService) {

        this.orderService = orderService;
        this.accountService = accountService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(Authentication authentication) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();

        OrderResponse response = orderService.checkout(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public PageResponse<OrderResponse> getOrders(Authentication authentication,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        return orderService.history(userId, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(Authentication authentication,@PathVariable Long orderId) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        return orderService.getOwn(userId, orderId);
    }
}