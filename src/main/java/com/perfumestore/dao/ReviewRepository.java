package com.perfumestore.dao;

import com.perfumestore.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByUser_IdAndProduct_Id(Long userId,Long productId);

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProduct_Id(Long productId,Pageable pageable);
}