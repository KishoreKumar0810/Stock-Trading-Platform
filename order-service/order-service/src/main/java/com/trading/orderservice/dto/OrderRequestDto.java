package com.trading.orderservice.dto;

import com.trading.orderservice.model.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderRequestDto {
    @NotBlank
    private String userId;

    @NotBlank
    private String symbol;

    @NotNull
    private OrderType orderType;

    @Min(1)
    private int quantity;


}
