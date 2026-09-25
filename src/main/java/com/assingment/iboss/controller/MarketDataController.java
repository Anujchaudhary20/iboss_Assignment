package com.assingment.iboss.controller;

import com.assingment.iboss.model.okx.OkxOrderBookData;
import com.assingment.iboss.model.okx.OkxResponse;
import com.assingment.iboss.model.okx.OkxTicker;
import com.assingment.iboss.service.MarketDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Endpoints for market data tickers and order book snapshots")
@SecurityRequirement(name = "Bearer Authentication")
public class MarketDataController {

    private final MarketDataProvider marketDataProvider;

    @Operation(summary = "Fetch top 20 spot trading pairs sorted by 24h volume",
            description = "Returns the top 20 crypto pairs sorted descending by 24h quote volume in USDT. Output adheres to OKX v5 format.")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/tickers")
    public ResponseEntity<OkxResponse<OkxTicker>> getTop20Tickers() {
        OkxResponse<OkxTicker> tickers = marketDataProvider.getTop20Tickers();
        return ResponseEntity.ok(tickers);
    }

    @Operation(summary = "Get order book snapshot for a pair",
            description = "Returns depth-5 snapshot with asks and bids for the specified pair (e.g. BTC-USDT).")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/orderbook")
    public ResponseEntity<OkxResponse<OkxOrderBookData>> getOrderBookSnapshot(
            @RequestParam(defaultValue = "BTC-USDT") String pair) {
        OkxOrderBookData orderBook = marketDataProvider.getOrderBookSnapshot(pair);
        return ResponseEntity.ok(OkxResponse.<OkxOrderBookData>builder()
                .code("0")
                .msg("")
                .data(List.of(orderBook))
                .build());
    }

    @Operation(summary = "List supported trading pairs")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/pairs")
    public ResponseEntity<List<String>> getSupportedPairs() {
        return ResponseEntity.ok(marketDataProvider.getSupportedPairs());
    }
}
