package com.perfumestore.dao;

import com.perfumestore.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUser_Id(Long userId,Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByIdAndUser_Id(Long id,Long userId);

    @Override
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findById(Long id);
}