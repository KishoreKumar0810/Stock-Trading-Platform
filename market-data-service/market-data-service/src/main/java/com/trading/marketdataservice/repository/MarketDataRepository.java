package com.trading.marketdataservice.repository;

import com.trading.marketdataservice.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketDataRepository extends JpaRepository<Stock, String> {
        boolean existsBySymbol(String symbol);
        Optional<Stock> findBySymbol(String symbol);
}
