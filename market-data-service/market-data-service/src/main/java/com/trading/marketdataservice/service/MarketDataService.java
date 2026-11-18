package com.trading.marketdataservice.service;

import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final MarketDataRepository marketDataRepository;

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final Random random = new Random();

    private static final String PRICE_KEY_PREFIX = "stock:price:";

    @Transactional
    @Scheduled(fixedRate = 2000)
    public void updatingStockPrice(){
        List<Stock> stocks = marketDataRepository.findAll();
        stocks.forEach(stock -> {
            String symbol = stock.getSymbol();
            BigDecimal oldPrice = stock.getPrice();

            // Random price movement - up or down by 0.25%
            double changePercent = (random.nextDouble() - 0.5) * 0.5;
            BigDecimal priceChange = oldPrice.multiply(BigDecimal.valueOf(changePercent / 100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newPrice = oldPrice.add(priceChange)
                    .setScale(2, RoundingMode.HALF_UP);

            if(newPrice.compareTo(BigDecimal.valueOf(1)) < 0) {
                newPrice = BigDecimal.valueOf(1);
            }

            BigDecimal changeAmount = newPrice.subtract(stock.getOpen())
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal changeAmountPercent = changeAmount.divide(stock.getOpen(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newHigh = newPrice.compareTo(stock.getHigh()) > 0 ? newPrice : stock.getHigh();
            BigDecimal newLow = newPrice.compareTo(stock.getLow()) < 0 ? newPrice : stock.getLow();

            stock.setPrice(newPrice);
            stock.setChange(changeAmount);
            stock.setChangePercent(changeAmountPercent);
            stock.setHigh(newHigh);
            stock.setLow(newLow);
            stock.setTimestamp(LocalDateTime.now());

            // 1. Cache in Redis
            redisTemplate.opsForValue().set(PRICE_KEY_PREFIX + stock.getSymbol(), stock);

            // 2. Broadcast via WebSocket → all subscribed clients
            messagingTemplate.convertAndSend("/topic/prices/" + stock.getSymbol(), stock);

            // 3. Publish to Kafka → Order Service needs current prices
            Map<String, Object> priceEvent = new HashMap<>();
            priceEvent.put("symbol", stock.getSymbol());
            priceEvent.put("price", newPrice);
            priceEvent.put("timestamp", LocalDateTime.now().toString());

            kafkaTemplate.send("stock.price.updated", symbol, priceEvent);
        });

        marketDataRepository.saveAll(stocks);

        log.debug("Prices updated for {} stocks", stocks.size());
}

public Stock addStock(Stock request) {
    if(marketDataRepository.existsBySymbol(request.getSymbol())) {
        throw new RuntimeException("Stock with the symbol "+ request.getSymbol() +" already exists");
    }

    Stock stock = Stock.builder()
            .symbol(request.getSymbol())
            .companyName(request.getCompanyName())
            .price(request.getPrice())
            .change(BigDecimal.ZERO)
            .changePercent(BigDecimal.ZERO)
            // new stock don't have high and low so we set the price as their high and low
            .high(request.getPrice())
            .low(request.getPrice())
            .open(request.getPrice())
            .volume(request.getVolume())
            .build();
    return marketDataRepository.save(stock);
}

public Stock updateStock(Stock request) {
    if(!marketDataRepository.existsBySymbol(request.getSymbol())) {
        throw new RuntimeException("Stock with the symbol "+ request.getSymbol() +" does not exist");
    }

    Stock stock = marketDataRepository.findBySymbol(request.getSymbol())
            .orElseThrow(() -> new RuntimeException("Stock with the symbol "+ request.getSymbol() +" does not exist"));
    stock.setCompanyName(request.getCompanyName());
    stock.setPrice(request.getPrice());
    stock.setChange(request.getChange());
    stock.setChangePercent(request.getChangePercent());
    stock.setHigh(request.getHigh());
    stock.setLow(request.getLow());
    stock.setOpen(request.getOpen());
    stock.setVolume(request.getVolume());

    return marketDataRepository.save(stock);
}

public String deleteStock(String symbol) {
    if(!marketDataRepository.existsBySymbol(symbol)) {
        throw new RuntimeException("Stock with the symbol "+ symbol +" does not exist");
    }
    marketDataRepository.deleteById(symbol);
    return "Stock with the symbol "+ symbol +" has been deleted successfully";
}

public Stock getStock(String symbol) {
    Optional<Stock> stockOptional = marketDataRepository.findBySymbol(symbol);
    if(stockOptional.isEmpty())
        throw new RuntimeException("Stock with the symbol "+ symbol +" does not exist");

    return stockOptional.get();
}

public List<Stock> getAllStocks() {
    return marketDataRepository.findAll();
}
}