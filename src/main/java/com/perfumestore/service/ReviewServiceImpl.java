package com.perfumestore.service;

import com.perfumestore.dao.ProductRepository;
import com.perfumestore.dao.ReviewRepository;
import com.perfumestore.dao.UserRepository;
import com.perfumestore.dto.request.ReviewRequest;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.dto.response.ReviewResponse;
import com.perfumestore.entity.Product;
import com.perfumestore.entity.Review;
import com.perfumestore.entity.User;
import com.perfumestore.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DtoMapper dtoMapper;

    public ReviewServiceImpl(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            DtoMapper dtoMapper) {

        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PageResponse<ReviewResponse> list(
            Long productId,
            int page,
            int size) {

        requireActiveProduct(productId);

        Pageable pageable = PageSupport.request(
                page,
                size,
                "createdAt",
                "desc",
                Set.of("createdAt")
        );

        Page<ReviewResponse> result = reviewRepository
                .findByProduct_Id(productId, pageable)
                .map(dtoMapper::review);

        return PageResponse.from(result);
    }

    @Override
    @Transactional
    public ReviewResponse create(
            Long userId,
            Long productId,
            ReviewRequest request) {

        Product product = requireActiveProduct(productId);

        if (reviewRepository.existsByUser_IdAndProduct_Id(
                userId,
                productId)) {

            throw ApiException.conflict(
                    "You have already reviewed this product."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(
                        "Account not found."
                ));

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.getRating());
        review.setComment(request.getComment().trim());

        reviewRepository.saveAndFlush(review);

        return dtoMapper.review(review);
    }

    private Product requireActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + productId));

        if (!product.isActive()) {
            throw ApiException.notFound("Product not found: " + productId);
        }

        return product;
    }
}