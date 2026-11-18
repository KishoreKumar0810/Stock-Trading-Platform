package com.trading.orderservice.service;

import com.trading.orderservice.client.AiServiceClient;
import com.trading.orderservice.client.MarketDataClient;
import com.trading.orderservice.client.UserServiceClient;
import com.trading.orderservice.dto.OrderRequestDto;
import com.trading.orderservice.model.Order;
import com.trading.orderservice.model.OrderStatus;
import com.trading.orderservice.model.OrderType;
import com.trading.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;
    private final MarketDataClient marketDataClient;
    private final AiServiceClient aiServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Order createOrder(OrderRequestDto request) {
        log.info("Placing {} order: {} x {} for user: {}",
                request.getOrderType(), request.getQuantity(),
                request.getSymbol(), request.getUserId());

        // Step 1: Get the stock and the current price
        Map<String, Object> stock = marketDataClient.getStock(request.getSymbol());
        BigDecimal price = new BigDecimal(stock.get("price").toString());

        // Step 2: Calculate the total amount
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(request.getQuantity()));

        // Step 3: Create Order
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setSymbol(request.getSymbol());
        order.setType(request.getOrderType());
        order.setPrice(price);
        order.setTotalAmount(totalAmount);
        order.setQuantity(request.getQuantity());
        order.setStatus(OrderStatus.AI_CHECK);

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved: {} status: {}", savedOrder.getId(), order.getStatus());

        // Step 4: Ai fraud check
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", savedOrder.getId());
        orderData.put("userId", request.getUserId());
        orderData.put("symbol", request.getSymbol());
        orderData.put("type", request.getOrderType());
        orderData.put("quantity", request.getQuantity());
        orderData.put("totalAmount", totalAmount);
        orderData.put("price", price);

        // Getting user's order history for Ai context
        long recentOrderCount = orderRepository.countByUserIdAndCreatedAtAfter(
                request.getUserId(), LocalDateTime.now().minusHours(1));

        orderData.put("recentOrderCount", recentOrderCount);

        try{
            Map<String, Object> aiResult = aiServiceClient.checkFraud(orderData);
            boolean isSuspicious = (boolean) aiResult.getOrDefault("suspicious",false);
            String aiReason = (String) aiResult.getOrDefault("reason","");
            double fraudScore = Double.parseDouble(aiResult.getOrDefault("fraudScore", "0.0").toString());

            log.info("AI fraud check — orderId: {} suspicious: {} score: {}",
                    savedOrder.getId(), isSuspicious, fraudScore);

            if(isSuspicious) { // fraud order don't execute
                savedOrder.setStatus(OrderStatus.FLAGGED);
                savedOrder.setFlaggedByAI(true);
                savedOrder.setAiReason(aiReason);
                savedOrder.setFailureReason(aiReason);
                orderRepository.save(savedOrder);

                publishOrderEvent("order.flagged", savedOrder, aiReason);
                log.warn("Order flagged by Ai for the reason: {}", aiReason);

                return savedOrder;
            }
        } catch (Exception ex) {
            log.warn("Ai service is unavailable: {} proceeding without check ", ex.getMessage());
        }

        // Step 5:
        try {
            if(request.getOrderType().equals(OrderType.BUY)) { // Buy order so deduct funds
                userServiceClient.deductFunds(request.getUserId(), totalAmount);
            }
            else { // Sell order so credit funds
                userServiceClient.creditFunds(request.getUserId(), totalAmount);
            }
            savedOrder.setStatus(OrderStatus.EXECUTED);
            savedOrder.setExecutedAt(LocalDateTime.now());
            orderRepository.save(savedOrder);
            log.info("Order EXECUTED: {} {} x {} at ₹{}",
                    request.getOrderType(), request.getQuantity(), request.getSymbol(), stock);

            publishOrderEvent("order.executed", savedOrder, null);
        }  catch (Exception ex) {
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason(ex.getMessage());
            orderRepository.save(order);
            log.error("Order failed {} for the reason: {}", savedOrder.getId(), ex.getMessage());

            publishOrderEvent("order.failed", savedOrder, ex.getMessage());
        }
        return savedOrder;
    }

    // Get all orders for a user.
    public List<Order> getUserOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // Get order by ID
    public Order getOrder(String orderId) {
        Optional<Order> orderOptional =  orderRepository.findById(orderId);

        if(orderOptional.isEmpty()) {
            throw new RuntimeException("Order not found for the Id: " + orderId);
        }

        return orderOptional.get();
    }

    // Get all orders
    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAll();

        if(orders.isEmpty()) {
            throw new RuntimeException("No orders found");
        }
        return orders;
    }

    private void publishOrderEvent(String topic, Order order, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", order.getId());
        event.put("userId", order.getUserId());
        event.put("symbol", order.getSymbol());
        event.put("orderType", order.getType().name());
        event.put("quantity", order.getQuantity());
        event.put("price", order.getPrice());
        event.put("totalAmount", order.getTotalAmount());
        event.put("status", order.getStatus().name());

        if (reason != null) event.put("reason", reason);

        kafkaTemplate.send(topic, order.getId(), event);
        log.info("Event published: {} for order: {}", topic, order.getId());
    }

}
