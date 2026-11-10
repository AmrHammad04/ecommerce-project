package com.perfumestore.controller;

import com.perfumestore.dto.request.CartItemRequest;
import com.perfumestore.dto.request.UpdateCartItemRequest;
import com.perfumestore.dto.response.CartResponse;
import com.perfumestore.service.AccountService;
import com.perfumestore.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final AccountService accountService;

    public CartController(CartService cartService,AccountService accountService) {

        this.cartService = cartService;
        this.accountService = accountService;
    }

    @GetMapping()
    public CartResponse getCart(Authentication authentication) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        return cartService.get(userId);
    }

    @PostMapping("/items")
    public CartResponse addItem(Authentication authentication,@Valid @RequestBody CartItemRequest request) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        return cartService.add(userId, request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(Authentication authentication,@PathVariable Long productId,@Valid @RequestBody UpdateCartItemRequest request) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        return cartService.update(userId, productId, request);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(Authentication authentication,@PathVariable Long productId) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        cartService.remove(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication authentication) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        cartService.clear(userId);

        return ResponseEntity.noContent().build();
    }
}