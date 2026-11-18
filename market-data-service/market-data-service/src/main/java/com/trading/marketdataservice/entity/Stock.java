package com.trading.marketdataservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Id;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.GeneratorType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    @Column(nullable = false, unique = true)
    private String symbol;
    
    @Column(nullable = false, unique = true)
    private String companyName;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "price_change", nullable = false)
    private BigDecimal change;

    @Column(nullable = false)
    private BigDecimal changePercent;

    @Column(nullable = false)
    private BigDecimal high;

    @Column(nullable = false)
    private BigDecimal low;

    @Column(nullable = false)
    private BigDecimal open;

    @Column(nullable = false)
    private long volume;

    @Column(nullable = false)
    @CurrentTimestamp
    private LocalDateTime timestamp;
}
