package com.assingment.iboss.model.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OkxWsMessage {
    // Inbound: op = "subscribe" | "unsubscribe" | "ping"
    private String op;
    private List<Map<String, String>> args;

    // Outbound stream: arg = {"channel": "books5", "instId": "BTC-USDT"}
    private Map<String, String> arg;
    private String action; // e.g. "snapshot" or "update"
    private List<OkxOrderBookData> data;

    // Outbound event: event = "subscribe" | "unsubscribe" | "error" | "connected"
    private String event;
    private String code;
    private String msg;
    private String connId;
}
