package com.assingment.iboss.websocket;

import com.assingment.iboss.model.okx.OkxOrderBookData;
import com.assingment.iboss.model.okx.OkxWsMessage;
import com.assingment.iboss.service.ClientSessionManager;
import com.assingment.iboss.service.mock.MockMarketDataEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private final ClientSessionManager clientSessionManager;
    private final MockMarketDataEngine mockMarketDataEngine;
    private final ObjectMapper objectMapper;

    // Fast lookup for active sessions: sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> activeSessionMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void setupBroadcastListener() {
        mockMarketDataEngine.addBroadcastListener(this::broadcastOrderBookUpdate);
        log.info("MarketDataWebSocketHandler registered for order book broadcasts.");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String clientId = (String) session.getAttributes().get(SessionHandshakeInterceptor.ATTR_CLIENT_ID);
        if (clientId == null) {
            log.error("Rejecting session {}: clientId missing from attributes", session.getId());
            session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Unauthenticated session"));
            return;
        }

        boolean registered = clientSessionManager.registerSession(clientId, session);
        if (!registered) {
            // Strict rejection fallback if race condition occurred
            log.warn("STRICT REJECTION: Closing duplicate session {} for client '{}'", session.getId(), clientId);
            session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(),
                    "Session rejected: Client '" + clientId + "' already has an active connection."));
            return;
        }

        activeSessionMap.put(session.getId(), session);

        // Send Welcome ACK
        Map<String, Object> welcome = Map.of(
                "event", "connected",
                "clientId", clientId,
                "sessionId", session.getId(),
                "msg", "Authenticated and connected to Market Data Service proxy. Single-session enforced.",
                "supportedPairs", mockMarketDataEngine.getSupportedPairs()
        );
        sendSafe(session, welcome);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload().trim();

        // 1. Raw Ping or JSON Ping
        if ("ping".equalsIgnoreCase(payload)) {
            sendSafe(session, Map.of("op", "pong"));
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);

            // Check OKX "op" command format
            if (root.has("op")) {
                String op = root.get("op").asText();
                handleOkxOp(session, op, root);
                return;
            }

            // Check alternative "action" format
            if (root.has("action")) {
                String action = root.get("action").asText();
                String pair = root.has("pair") ? root.get("pair").asText() : "BTC-USDT";
                handleAction(session, action, pair);
                return;
            }

            // Unknown message format
            sendSafe(session, Map.of(
                    "event", "error",
                    "code", "60012",
                    "msg", "Invalid command format. Supported formats: {'op':'subscribe','args':[{'channel':'books5','instId':'BTC-USDT'}]} or {'action':'SUBSCRIBE','pair':'BTC-USDT'}"
            ));

        } catch (Exception e) {
            log.error("Failed to process message from session {}: {}", session.getId(), e.getMessage());
            sendSafe(session, Map.of("event", "error", "code", "60013", "msg", "Malformed JSON: " + e.getMessage()));
        }
    }

    private void handleOkxOp(WebSocketSession session, String op, JsonNode root) throws IOException {
        if ("ping".equalsIgnoreCase(op)) {
            sendSafe(session, Map.of("op", "pong"));
            return;
        }

        if ("subscribe".equalsIgnoreCase(op) || "unsubscribe".equalsIgnoreCase(op)) {
            JsonNode argsNode = root.get("args");
            if (argsNode == null || !argsNode.isArray()) {
                sendSafe(session, Map.of("event", "error", "code", "60014", "msg", "Missing 'args' array"));
                return;
            }

            for (JsonNode argNode : argsNode) {
                String channel = argNode.has("channel") ? argNode.get("channel").asText() : "books5";
                String instId = argNode.has("instId") ? argNode.get("instId").asText() : "BTC-USDT";

                if ("subscribe".equalsIgnoreCase(op)) {
                    subscribePair(session, channel, instId);
                } else {
                    unsubscribePair(session, channel, instId);
                }
            }
        }
    }

    private void handleAction(WebSocketSession session, String action, String pair) throws IOException {
        if ("SUBSCRIBE".equalsIgnoreCase(action)) {
            subscribePair(session, "books5", pair);
        } else if ("UNSUBSCRIBE".equalsIgnoreCase(action)) {
            unsubscribePair(session, "books5", pair);
        } else {
            sendSafe(session, Map.of("event", "error", "code", "60015", "msg", "Unknown action: " + action));
        }
    }

    private void subscribePair(WebSocketSession session, String channel, String instId) throws IOException {
        mockMarketDataEngine.subscribe(instId, session.getId());

        // 1. Send subscription confirmation ACK
        Map<String, Object> ack = Map.of(
                "event", "subscribe",
                "arg", Map.of("channel", channel, "instId", instId)
        );
        sendSafe(session, ack);

        // 2. Immediately push initial snapshot
        OkxOrderBookData snapshot = mockMarketDataEngine.getOrderBookSnapshot(instId);
        OkxWsMessage snapshotMsg = OkxWsMessage.builder()
                .arg(Map.of("channel", channel, "instId", instId))
                .action("snapshot")
                .data(List.of(snapshot))
                .build();
        sendSafe(session, snapshotMsg);
    }

    private void unsubscribePair(WebSocketSession session, String channel, String instId) {
        mockMarketDataEngine.unsubscribe(instId, session.getId());

        Map<String, Object> ack = Map.of(
                "event", "unsubscribe",
                "arg", Map.of("channel", channel, "instId", instId)
        );
        sendSafe(session, ack);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: sessionId='{}', status={}", session.getId(), status);
        activeSessionMap.remove(session.getId());
        clientSessionManager.unregisterSession(session.getId());
        mockMarketDataEngine.unsubscribeAll(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error on session {}: {}", session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Broadcasts order book updates from MockMarketDataEngine to all sessions watching the pair.
     */
    private void broadcastOrderBookUpdate(String pair, OkxWsMessage message) {
        Set<String> subscriberIds = mockMarketDataEngine.getSubscribersForPair(pair);
        if (subscriberIds.isEmpty()) return;

        try {
            String jsonPayload = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(jsonPayload);

            for (String sessionId : subscriberIds) {
                WebSocketSession session = activeSessionMap.get(sessionId);
                if (session != null && session.isOpen()) {
                    synchronized (session) {
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(textMessage);
                            }
                        } catch (IOException e) {
                            log.error("Error sending update to session {}: {}", sessionId, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error serializing order book update for pair {}: {}", pair, e.getMessage());
        }
    }

    private void sendSafe(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) return;
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    String json = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
