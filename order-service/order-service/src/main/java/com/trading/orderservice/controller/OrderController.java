package com.trading.orderservice.controller;

import com.trading.orderservice.dto.OrderRequestDto;
import com.trading.orderservice.model.Order;
import com.trading.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Place buy or sell order.
     * userId comes from X-User-Id header — set by API Gateway
     * after JWT validation. User cannot fake their userId.
     */
    @PostMapping("/create")
    public ResponseEntity<Order> createOrder(@RequestHeader("X-User-Id") String userId,
                                             @Valid @RequestBody OrderRequestDto request) {
            request.setUserId(userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @GetMapping("/my/orders")
    public ResponseEntity<List<Order>> getMyOrders(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(orderService.getUserOrders(userId));
    }

    @GetMapping("{userId}")
    public ResponseEntity<List<Order>> getAllUserOrders(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(orderService.getUserOrders(userId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Order>> getAllUserOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

}
