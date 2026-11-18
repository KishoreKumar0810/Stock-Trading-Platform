package com.trading.userservice.dto;

import com.trading.userservice.entity.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class UserResponseDto {
    private String id;

    private String firstName;

    private String lastName;

    private String email;

    private BigDecimal walletBalance;

    private UserStatus status;

    private LocalDateTime createdAt;
}
