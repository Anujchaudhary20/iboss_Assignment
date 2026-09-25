package com.assingment.iboss.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ClientSessionManager {

    @Data
    @Builder
    public static class SessionMetadata {
        private String clientId;
        private String sessionId;
        private Instant connectedAt;
        private String remoteAddress;
    }

    // clientId -> WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    
    // sessionId -> clientId
    private final Map<String, String> sessionToClientMap = new ConcurrentHashMap<>();
    
    // sessionId -> SessionMetadata
    private final Map<String, SessionMetadata> sessionMetadataMap = new ConcurrentHashMap<>();

    // Metrics for assignment evaluation & admin monitoring
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong rejectedConnections = new AtomicLong(0);

    /**
     * Checks if a client currently has an active and open session.
     */
    public boolean hasActiveSession(String clientId) {
        if (clientId == null) return false;
        WebSocketSession existing = activeSessions.get(clientId);
        return existing != null && existing.isOpen();
    }

    /**
     * Atomically registers a session for the given client ID.
     * Enforces the Strict Rejection single-session rule:
     * If an open session already exists for this client, registration fails and returns false.
     */
    public synchronized boolean registerSession(String clientId, WebSocketSession session) {
        totalConnections.incrementAndGet();

        WebSocketSession existingSession = activeSessions.get(clientId);
        if (existingSession != null && existingSession.isOpen()) {
            rejectedConnections.incrementAndGet();
            log.warn("STRICT REJECTION: Connection attempt rejected for clientId='{}'. An active session ({}) is already running.",
                    clientId, existingSession.getId());
            return false;
        }

        activeSessions.put(clientId, session);
        sessionToClientMap.put(session.getId(), clientId);
        
        String remote = session.getRemoteAddress() != null ? session.getRemoteAddress().toString() : "unknown";
        sessionMetadataMap.put(session.getId(), SessionMetadata.builder()
                .clientId(clientId)
                .sessionId(session.getId())
                .connectedAt(Instant.now())
                .remoteAddress(remote)
                .build());

        log.info("Session registered successfully: clientId='{}', sessionId='{}'", clientId, session.getId());
        return true;
    }

    /**
     * Unregisters a session when disconnected.
     */
    public synchronized void unregisterSession(String sessionId) {
        String clientId = sessionToClientMap.remove(sessionId);
        sessionMetadataMap.remove(sessionId);

        if (clientId != null) {
            WebSocketSession current = activeSessions.get(clientId);
            if (current != null && current.getId().equals(sessionId)) {
                activeSessions.remove(clientId);
                log.info("Session unregistered cleanly: clientId='{}', sessionId='{}'", clientId, sessionId);
            }
        }
    }

    /**
     * Administratively terminates an active session.
     */
    public synchronized boolean terminateSession(String clientId) {
        WebSocketSession session = activeSessions.remove(clientId);
        if (session != null && session.isOpen()) {
            try {
                sessionToClientMap.remove(session.getId());
                sessionMetadataMap.remove(session.getId());
                session.close(new CloseStatus(1000, "Administratively terminated"));
                log.info("Session administratively terminated for clientId='{}'", clientId);
                return true;
            } catch (IOException e) {
                log.error("Failed to close session for clientId='{}': {}", clientId, e.getMessage());
            }
        }
        return false;
    }

    public WebSocketSession getSession(String clientId) {
        return activeSessions.get(clientId);
    }

    public String getClientId(String sessionId) {
        return sessionToClientMap.get(sessionId);
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public long getTotalConnections() {
        return totalConnections.get();
    }

    public long getRejectedConnections() {
        return rejectedConnections.get();
    }

    public List<SessionMetadata> getActiveSessionList() {
        return new ArrayList<>(sessionMetadataMap.values());
    }
}
