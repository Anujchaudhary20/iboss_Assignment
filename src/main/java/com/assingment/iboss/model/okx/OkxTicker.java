package com.assingment.iboss.model.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Representation of OKX Market Ticker data model adhering to OKX v5 format.
 * Uses descriptive Java member variable naming conventions with JSON mappings
 * to OKX API schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OkxTicker implements Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    @JsonProperty("instType")
    private String instrumentType = "SPOT";

    @JsonProperty("instId")
    private String instrumentId; // e.g. "BTC-USDT"

    @JsonProperty("last")
    private String lastPrice; // Last traded price

    @JsonProperty("lastSz")
    private String lastSize; // Last traded size

    @JsonProperty("askPx")
    private String askPrice; // Best ask price

    @JsonProperty("askSz")
    private String askSize; // Best ask size

    @JsonProperty("bidPx")
    private String bidPrice; // Best bid price

    @JsonProperty("bidSz")
    private String bidSize; // Best bid size

    @JsonProperty("open24h")
    private String openPrice24h; // Open price 24h ago

    @JsonProperty("high24h")
    private String highPrice24h; // Highest price in 24h

    @JsonProperty("low24h")
    private String lowPrice24h; // Lowest price in 24h

    @JsonProperty("vol24h")
    private String volume24h; // 24h volume in base currency

    @JsonProperty("volCcy24h")
    private String volumeCurrency24h; // 24h volume in quote currency (e.g. USDT)

    @JsonProperty("sodUtc0")
    private String startOfDayUtc0; // Open price UTC 0

    @JsonProperty("sodUtc8")
    private String startOfDayUtc8; // Open price UTC+8

    @JsonProperty("ts")
    private String timestamp; // Epoch timestamp in milliseconds
}
