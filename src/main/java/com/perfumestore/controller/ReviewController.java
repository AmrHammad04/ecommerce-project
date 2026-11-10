package com.perfumestore.controller;

import com.perfumestore.dto.request.ReviewRequest;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.dto.response.ReviewResponse;
import com.perfumestore.service.AccountService;
import com.perfumestore.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final AccountService accountService;

    public ReviewController(ReviewService reviewService,AccountService accountService) {

        this.reviewService = reviewService;
        this.accountService = accountService;
    }

    @GetMapping
    public PageResponse<ReviewResponse> getReviews(@PathVariable Long productId,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size) {

        return reviewService.list(productId, page, size);
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(Authentication authentication,@PathVariable Long productId,@Valid @RequestBody ReviewRequest request) {

        Long userId = accountService.getByEmail(authentication.getName()).getId();
        ReviewResponse response = reviewService.create(userId,productId,request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}