package com.perfumestore.service;

import com.perfumestore.dto.request.CartItemRequest;
import com.perfumestore.dto.request.UpdateCartItemRequest;
import com.perfumestore.dto.response.CartResponse;

public interface CartService {

    CartResponse get(Long userId);
    CartResponse add(Long userId, CartItemRequest request);
    CartResponse update(
            Long userId,
            Long productId,
            UpdateCartItemRequest request);
            
    void remove(Long userId, Long productId);
    void clear(Long userId);
}