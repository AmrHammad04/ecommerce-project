package com.perfumestore.dto.response;

import java.time.Instant;

public class ReviewResponse {

    private Long id;
    private Long productId;
    private Long authorId;
    private String authorName;
    private int rating;
    private String comment;
    private Instant createdAt;

    public ReviewResponse() {
    }

    public ReviewResponse(
            Long id,
            Long productId,
            Long authorId,
            String authorName,
            int rating,
            String comment,
            Instant createdAt) {

        this.id = id;
        this.productId = productId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}