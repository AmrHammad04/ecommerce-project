package com.perfumestore.service;

import com.perfumestore.dto.request.ReviewRequest;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.dto.response.ReviewResponse;

public interface ReviewService {

    PageResponse<ReviewResponse> list(
            Long productId,
            int page,
            int size);

    ReviewResponse create(
            Long userId,
            Long productId,
            ReviewRequest request);
}