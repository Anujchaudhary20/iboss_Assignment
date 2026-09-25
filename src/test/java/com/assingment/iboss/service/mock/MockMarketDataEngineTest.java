package com.assingment.iboss.service.mock;

import com.assingment.iboss.model.okx.OkxOrderBookData;
import com.assingment.iboss.model.okx.OkxResponse;
import com.assingment.iboss.model.okx.OkxTicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MockMarketDataEngineTest {

    private MockMarketDataEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MockMarketDataEngine();
        engine.init();
    }

    @Test
    @DisplayName("Should return exactly top 20 tickers sorted descending by 24h quote volume")
    void testGetTop20Tickers() {
        OkxResponse<OkxTicker> response = engine.getTop20Tickers();

        assertNotNull(response);
        assertEquals("0", response.getCode(), "Response code should be 0 per OKX spec");
        assertNotNull(response.getData());
        assertEquals(20, response.getData().size(), "Must return exactly 20 spot trading pairs");

        List<OkxTicker> tickers = response.getData();

        // Verify descending sort order by volumeCurrency24h
        for (int i = 0; i < tickers.size() - 1; i++) {
            double currentVol = Double.parseDouble(tickers.get(i).getVolumeCurrency24h());
            double nextVol = Double.parseDouble(tickers.get(i + 1).getVolumeCurrency24h());
            assertTrue(currentVol >= nextVol,
                    String.format("Ticker %s (vol=%.2f) must have >= volume than ticker %s (vol=%.2f)",
                            tickers.get(i).getInstrumentId(), currentVol, tickers.get(i + 1).getInstrumentId(), nextVol));
        }

        // Verify fields
        OkxTicker btc = tickers.get(0);
        assertEquals("BTC-USDT", btc.getInstrumentId());
        assertEquals("SPOT", btc.getInstrumentType());
        assertNotNull(btc.getLastPrice());
        assertNotNull(btc.getAskPrice());
        assertNotNull(btc.getBidPrice());
        assertNotNull(btc.getTimestamp());
    }

    @Test
    @DisplayName("Should generate valid Depth-5 order book with 5 asks and 5 bids")
    void testGetOrderBookSnapshot() {
        OkxOrderBookData snapshot = engine.getOrderBookSnapshot("BTC-USDT");

        assertNotNull(snapshot);
        assertNotNull(snapshot.getAsks());
        assertNotNull(snapshot.getBids());
        assertEquals(5, snapshot.getAsks().size(), "Must contain 5 ask levels");
        assertEquals(5, snapshot.getBids().size(), "Must contain 5 bid levels");

        // Verify each entry has [price, size, liquidated, orderCount]
        for (List<String> ask : snapshot.getAsks()) {
            assertEquals(4, ask.size());
            assertTrue(Double.parseDouble(ask.get(0)) > 0);
            assertTrue(Double.parseDouble(ask.get(1)) > 0);
        }

        for (List<String> bid : snapshot.getBids()) {
            assertEquals(4, bid.size());
            assertTrue(Double.parseDouble(bid.get(0)) > 0);
            assertTrue(Double.parseDouble(bid.get(1)) > 0);
        }

        // Best ask must be greater than best bid (non-negative spread)
        double bestAsk = Double.parseDouble(snapshot.getAsks().get(0).get(0));
        double bestBid = Double.parseDouble(snapshot.getBids().get(0).get(0));
        assertTrue(bestAsk >= bestBid, "Best ask must be >= best bid");
    }

    @Test
    @DisplayName("Should manage subscriptions cleanly without leaks")
    void testSubscriptionLifecycle() {
        String pair = "ETH-USDT";
        String sessionId = "test-sess-123";

        engine.subscribe(pair, sessionId);
        Set<String> subscribers = engine.getSubscribersForPair(pair);
        assertTrue(subscribers.contains(sessionId));

        engine.unsubscribe(pair, sessionId);
        subscribers = engine.getSubscribersForPair(pair);
        assertFalse(subscribers.contains(sessionId));

        engine.subscribe("BTC-USDT", sessionId);
        engine.subscribe("SOL-USDT", sessionId);
        engine.unsubscribeAll(sessionId);

        assertFalse(engine.getSubscribersForPair("BTC-USDT").contains(sessionId));
        assertFalse(engine.getSubscribersForPair("SOL-USDT").contains(sessionId));
    }
}
