package com.perfumestore.service;

import com.perfumestore.dto.response.OrderResponse;
import com.perfumestore.dto.response.PageResponse;

public interface OrderService {

    OrderResponse checkout(Long userId);
    PageResponse<OrderResponse> history(
            Long userId,
            int page,
            int size);

    OrderResponse getOwn(Long userId, Long orderId);
    PageResponse<OrderResponse> all(int page, int size);
    OrderResponse getAny(Long orderId);
}