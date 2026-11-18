package com.trading.orderservice.repository;

import com.trading.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime createdAt);

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Order> findById(String userId);
}
