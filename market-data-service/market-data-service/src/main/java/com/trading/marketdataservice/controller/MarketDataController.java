package com.trading.marketdataservice.controller;

import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    @PostMapping("/add")
    public ResponseEntity<Stock> addStock(@RequestBody Stock request) {
        return ResponseEntity.ok(marketDataService.addStock(request));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Stock> updateStock(@RequestBody Stock request) {
        return ResponseEntity.ok(marketDataService.updateStock(request));
    }

    @DeleteMapping("/delete/{symbol}")
    public ResponseEntity<String> deleteStock(@PathVariable String symbol) {
        return ResponseEntity.ok(marketDataService.deleteStock(symbol));
    }

    @GetMapping("/stock/{symbol}")
    public ResponseEntity<Stock> getStock(@PathVariable String symbol) {
        return ResponseEntity.ok(marketDataService.getStock(symbol.toUpperCase()));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Stock>> getAllStocks() {
        return ResponseEntity.ok(marketDataService.getAllStocks());
    }

}
