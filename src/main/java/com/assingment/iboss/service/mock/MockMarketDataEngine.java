package com.assingment.iboss.service.mock;

import com.assingment.iboss.model.okx.OkxOrderBookData;
import com.assingment.iboss.model.okx.OkxResponse;
import com.assingment.iboss.model.okx.OkxTicker;
import com.assingment.iboss.model.okx.OkxWsMessage;
import com.assingment.iboss.service.MarketDataProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

@Slf4j
@Service
@EnableScheduling
public class MockMarketDataEngine implements MarketDataProvider {

    public interface OrderBookBroadcastListener extends BiConsumer<String, OkxWsMessage> {
    }

    private final List<OrderBookBroadcastListener> broadcastListeners = new CopyOnWriteArrayList<>();

    // Internal state for top 20 tickers
    private final Map<String, TickerState> tickerStates = new ConcurrentHashMap<>();
    private final List<String> pairRankings = new ArrayList<>();

    // Subscriptions: pair -> Set<sessionId>
    private final Map<String, Set<String>> pairSubscribers = new ConcurrentHashMap<>();
    // Subscriptions: sessionId -> Set<pair>
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    private static class TickerState {
        String instId;
        double currentPrice;
        double openPrice;
        double highPrice;
        double lowPrice;
        double volBase;
        double volQuote;
        int priceDecimals;
        int sizeDecimals;
        long seqId = 1000L;
    }

    public void addBroadcastListener(OrderBookBroadcastListener listener) {
        broadcastListeners.add(listener);
    }

    @PostConstruct
    public void init() {
        // Seed top 20 pairs ranked strictly descending by 24h quote volume
        seedTicker("BTC-USDT", 64250.0, 1850000000.0, 1, 4);
        seedTicker("ETH-USDT", 3450.5, 1220000000.0, 2, 3);
        seedTicker("SOL-USDT", 154.20, 840000000.0, 2, 2);
        seedTicker("BNB-USDT", 585.40, 430000000.0, 2, 2);
        seedTicker("DOGE-USDT", 0.12450, 385000000.0, 5, 1);
        seedTicker("XRP-USDT", 0.5890, 340000000.0, 4, 1);
        seedTicker("ADA-USDT", 0.4180, 290000000.0, 4, 1);
        seedTicker("AVAX-USDT", 28.40, 260000000.0, 2, 2);
        seedTicker("SUI-USDT", 1.8250, 240000000.0, 4, 2);
        seedTicker("LINK-USDT", 12.35, 210000000.0, 2, 2);
        seedTicker("NEAR-USDT", 5.150, 190000000.0, 3, 2);
        seedTicker("DOT-USDT", 4.820, 170000000.0, 3, 2);
        seedTicker("LTC-USDT", 67.80, 155000000.0, 2, 2);
        seedTicker("PEPE-USDT", 0.00000952, 145000000.0, 8, 0);
        seedTicker("APT-USDT", 8.420, 135000000.0, 3, 2);
        seedTicker("SHIB-USDT", 0.00001850, 120000000.0, 8, 0);
        seedTicker("MATIC-USDT", 0.4120, 110000000.0, 4, 1);
        seedTicker("UNI-USDT", 7.580, 105000000.0, 3, 2);
        seedTicker("ICP-USDT", 8.850, 95000000.0, 3, 2);
        seedTicker("FET-USDT", 1.420, 88000000.0, 3, 2);

        log.info("MockMarketDataEngine initialized with {} spot crypto pairs.", tickerStates.size());
    }

    private void seedTicker(String instId, double price, double quoteVol, int priceDecimals, int sizeDecimals) {
        TickerState state = new TickerState();
        state.instId = instId;
        state.currentPrice = price;
        state.openPrice = price * (1.0 + (ThreadLocalRandom.current().nextDouble(-0.04, 0.04)));
        state.highPrice = Math.max(state.currentPrice, state.openPrice)
                * (1.0 + ThreadLocalRandom.current().nextDouble(0.01, 0.03));
        state.lowPrice = Math.min(state.currentPrice, state.openPrice)
                * (1.0 - ThreadLocalRandom.current().nextDouble(0.01, 0.03));
        state.volQuote = quoteVol;
        state.volBase = quoteVol / price;
        state.priceDecimals = priceDecimals;
        state.sizeDecimals = sizeDecimals;

        tickerStates.put(instId, state);
        pairRankings.add(instId);
    }

    @Override
    public OkxResponse<OkxTicker> getTop20Tickers() {
        List<OkxTicker> tickers = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (String pair : pairRankings) {
            TickerState state = tickerStates.get(pair);
            if (state == null)
                continue;

            double spread = state.currentPrice * 0.0002; // 0.02% spread
            double ask = state.currentPrice + (spread / 2.0);
            double bid = state.currentPrice - (spread / 2.0);

            tickers.add(OkxTicker.builder()
                    .instrumentType("SPOT")
                    .instrumentId(state.instId)
                    .lastPrice(format(state.currentPrice, state.priceDecimals))
                    .lastSize(format(ThreadLocalRandom.current().nextDouble(0.1, 2.5), state.sizeDecimals))
                    .askPrice(format(ask, state.priceDecimals))
                    .askSize(format(ThreadLocalRandom.current().nextDouble(0.5, 5.0), state.sizeDecimals))
                    .bidPrice(format(bid, state.priceDecimals))
                    .bidSize(format(ThreadLocalRandom.current().nextDouble(0.5, 5.0), state.sizeDecimals))
                    .openPrice24h(format(state.openPrice, state.priceDecimals))
                    .highPrice24h(format(state.highPrice, state.priceDecimals))
                    .lowPrice24h(format(state.lowPrice, state.priceDecimals))
                    .volume24h(format(state.volBase, 2))
                    .volumeCurrency24h(format(state.volQuote, 2))
                    .startOfDayUtc0(format(state.openPrice, state.priceDecimals))
                    .startOfDayUtc8(format(state.openPrice, state.priceDecimals))
                    .timestamp(String.valueOf(now))
                    .build());
        }

        // Strictly verify sorted descending by 24h quote volume
        tickers.sort(
                (a, b) -> Double.compare(Double.parseDouble(b.getVolumeCurrency24h()), Double.parseDouble(a.getVolumeCurrency24h())));

        return OkxResponse.<OkxTicker>builder()
                .code("0")
                .msg("")
                .data(tickers)
                .build();
    }

    @Override
    public OkxOrderBookData getOrderBookSnapshot(String pair) {
        TickerState state = tickerStates.get(pair);
        if (state == null) {
            // Default fallback if unknown pair
            state = tickerStates.get("BTC-USDT");
        }
        return generateOrderBook(state);
    }

    @Override
    public void subscribe(String pair, String sessionId) {
        pairSubscribers.computeIfAbsent(pair, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(pair);
        log.debug("Session '{}' subscribed to '{}'", sessionId, pair);
    }

    @Override
    public void unsubscribe(String pair, String sessionId) {
        Set<String> sessions = pairSubscribers.get(pair);
        if (sessions != null) {
            sessions.remove(sessionId);
        }
        Set<String> pairs = sessionSubscriptions.get(sessionId);
        if (pairs != null) {
            pairs.remove(pair);
        }
        log.debug("Session '{}' unsubscribed from '{}'", sessionId, pair);
    }

    @Override
    public void unsubscribeAll(String sessionId) {
        Set<String> subscribedPairs = sessionSubscriptions.remove(sessionId);
        if (subscribedPairs != null) {
            for (String pair : subscribedPairs) {
                Set<String> sessions = pairSubscribers.get(pair);
                if (sessions != null) {
                    sessions.remove(sessionId);
                }
            }
        }
        log.debug("Cleaned all subscriptions for session '{}'", sessionId);
    }

    @Override
    public List<String> getSupportedPairs() {
        return Collections.unmodifiableList(pairRankings);
    }

    /**
     * Periodic background tick simulating live OKX WebSocket stream pushes.
     * Fires every 350ms to deliver low-latency order book updates to active
     * subscribers.
     */
    @Scheduled(fixedDelay = 350)
    public void streamOrderBookTicks() {
        for (Map.Entry<String, Set<String>> entry : pairSubscribers.entrySet()) {
            String pair = entry.getKey();
            Set<String> subscribers = entry.getValue();

            if (subscribers == null || subscribers.isEmpty()) {
                continue; // Zero resource waste if no downstream clients are watching
            }

            TickerState state = tickerStates.get(pair);
            if (state == null)
                continue;

            // Micro random walk (Brownian fluctuation: +/- 0.02%)
            double jitter = ThreadLocalRandom.current().nextDouble(-0.0002, 0.0002);
            state.currentPrice = Math.max(state.currentPrice * (1.0 + jitter), 0.000001);
            state.highPrice = Math.max(state.highPrice, state.currentPrice);
            state.lowPrice = Math.min(state.lowPrice, state.currentPrice);

            OkxOrderBookData obData = generateOrderBook(state);

            OkxWsMessage message = OkxWsMessage.builder()
                    .arg(Map.of("channel", "books5", "instId", pair))
                    .action("update")
                    .data(List.of(obData))
                    .build();

            for (OrderBookBroadcastListener listener : broadcastListeners) {
                try {
                    listener.accept(pair, message);
                } catch (Exception e) {
                    log.error("Error broadcasting to listener for pair '{}': {}", pair, e.getMessage());
                }
            }
        }
    }

    private OkxOrderBookData generateOrderBook(TickerState state) {
        state.seqId++;
        long now = System.currentTimeMillis();

        double spreadPercent = ThreadLocalRandom.current().nextDouble(0.0001, 0.0003); // 0.01% - 0.03%
        double halfSpread = (state.currentPrice * spreadPercent) / 2.0;

        double bestAsk = state.currentPrice + halfSpread;
        double bestBid = state.currentPrice - halfSpread;

        List<List<String>> asks = new ArrayList<>(5);
        List<List<String>> bids = new ArrayList<>(5);

        // Generate 5 Ask levels (ascending prices)
        for (int i = 0; i < 5; i++) {
            double step = halfSpread * (i + 1) * ThreadLocalRandom.current().nextDouble(0.8, 1.2);
            double askPx = bestAsk + step;
            double askSz = ThreadLocalRandom.current().nextDouble(0.2, 8.0);
            int orderCount = ThreadLocalRandom.current().nextInt(1, 15);
            asks.add(List.of(
                    format(askPx, state.priceDecimals),
                    format(askSz, state.sizeDecimals),
                    "0",
                    String.valueOf(orderCount)));
        }

        // Generate 5 Bid levels (descending prices)
        for (int i = 0; i < 5; i++) {
            double step = halfSpread * (i + 1) * ThreadLocalRandom.current().nextDouble(0.8, 1.2);
            double bidPx = Math.max(bestBid - step, 0.00000001);
            double bidSz = ThreadLocalRandom.current().nextDouble(0.2, 8.0);
            int orderCount = ThreadLocalRandom.current().nextInt(1, 15);
            bids.add(List.of(
                    format(bidPx, state.priceDecimals),
                    format(bidSz, state.sizeDecimals),
                    "0",
                    String.valueOf(orderCount)));
        }

        return OkxOrderBookData.builder()
                .asks(asks)
                .bids(bids)
                .ts(String.valueOf(now))
                .seqId(state.seqId)
                .build();
    }

    public Set<String> getSubscribersForPair(String pair) {
        Set<String> subs = pairSubscribers.get(pair);
        return subs != null ? Collections.unmodifiableSet(subs) : Collections.emptySet();
    }

    private String format(double value, int decimals) {
        if (decimals <= 0) {
            return String.valueOf(Math.round(value));
        }
        return BigDecimal.valueOf(value)
                .setScale(decimals, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
