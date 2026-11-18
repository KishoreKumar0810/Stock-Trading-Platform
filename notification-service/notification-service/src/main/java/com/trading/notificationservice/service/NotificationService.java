package com.trading.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "order.executed")
    public void consumeOrderExecuted(@Payload Map<String, Object> payload) {
        try {
            String userId = payload.get("userId").toString();
            ;
            String symbol = payload.get("symbol").toString();
            String orderType = payload.get("orderType").toString();
            Integer quantity = Integer.valueOf(payload.get("quantity").toString());
            BigDecimal price = new BigDecimal(payload.get("price").toString());
            BigDecimal totalAmount = new BigDecimal(payload.get("totalAmount").toString());

            sendNotification(userId,
                    orderType.equals("BUY") ? "✅ Order Executed — Buy" : "✅ Order Executed — Sell",
                    String.format("%s %s shares of %s at ₹%s. " + "Total: ₹%s", orderType, quantity, symbol, price, totalAmount));
        } catch (Exception e) {
            log.error("Error while sending order notification", e);
        }
    }

    @KafkaListener(topics =  "order.flagged")
    public void consumeOrderFlagged(@Payload Map<String, Object> payload) {
        try{
            String userId = payload.get("userId").toString();
            String symbol = payload.get("symbol").toString();
            String reason = payload.get("reason").toString();

            sendNotification(userId, "🚨 Order Flagged by AI",
                    String.format("Your order for %s was flagged as suspicious. Reason: %s. " +
                                    "Please contact support if this was a legitimate trade.", symbol, reason)
            );
        } catch (Exception e) {
            log.error("Error sending fraud notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.failed")
    public void consumeOrderFailed(@Payload Map<String, Object> payload) {
        try {
            String userId = (String) payload.get("userId");
            String symbol = (String) payload.get("symbol");
            String reason = (String) payload.get("reason");

            sendNotification(userId, "❌ Order Failed",
                    String.format("Your order for %s failed. Reason: %s", symbol, reason));
        } catch (Exception e) {
            log.error("Error sending failure notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "user.registered")
    public void consumeUserRegistered(@Payload Map<String, Object> payload) {
        try {
            String userId = (String) payload.get("userId");
            String firstName = (String) payload.get("firstName");
            Object walletBalance = payload.get("walletBalance");

            sendNotification(userId, "🎉 Welcome to Stock Trading Platform!",
                    String.format("Welcome %s! Your account is ready. ₹%s has been added to your wallet. Start trading now!",
                            firstName, walletBalance)
            );
        } catch (Exception e) {
            log.error("Error sending welcome notification: {}", e.getMessage());
        }
    }

    private void sendNotification(String userId, String title, String message) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("NOTIFICATION SENT");
        log.info("To User : {}", userId);
        log.info("Title   : {}", title);
        log.info("Message : {}", message);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
