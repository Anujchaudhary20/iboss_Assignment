package com.assingment.iboss.service;

import com.assingment.iboss.model.okx.OkxOrderBookData;
import com.assingment.iboss.model.okx.OkxResponse;
import com.assingment.iboss.model.okx.OkxTicker;

import java.util.List;

public interface MarketDataProvider {
    /**
     * Fetch top 20 spot trading pairs sorted descending by 24h quote volume (USDT).
     */
    OkxResponse<OkxTicker> getTop20Tickers();

    /**
     * Get instantaneous order book snapshot for the specified pair.
     */
    OkxOrderBookData getOrderBookSnapshot(String pair);

    /**
     * Register a subscriber session to an instrument feed.
     */
    void subscribe(String pair, String sessionId);

    /**
     * Unregister a subscriber session from an instrument feed.
     */
    void unsubscribe(String pair, String sessionId);

    /**
     * Remove subscriber from all instrument feeds upon session disconnection.
     */
    void unsubscribeAll(String sessionId);

    /**
     * Get list of supported trading pairs.
     */
    List<String> getSupportedPairs();
}
