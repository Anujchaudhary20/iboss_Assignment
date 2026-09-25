package com.assingment.iboss.websocket;

import com.assingment.iboss.security.JwtService;
import com.assingment.iboss.service.ClientSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketSingleSessionEnforcementTest {

    private JwtService jwtService;
    private ClientSessionManager clientSessionManager;
    private SessionHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "iboss-market-data-service-secure-jwt-signing-secret-key-2026-okx-proxy-crypto",
                86400000L
        );
        clientSessionManager = new ClientSessionManager();
        interceptor = new SessionHandshakeInterceptor(jwtService, clientSessionManager);
    }

    @Test
    @DisplayName("Should reject handshake with HTTP 401 if token is missing")
    void testHandshakeWithoutToken() throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(new URI("http://localhost:8080/ws/market"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result, "Handshake without token must be rejected");
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should approve handshake for valid token when client has no active session")
    void testHandshakeWithValidTokenNewSession() throws Exception {
        String token = jwtService.generateToken("trader1", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(new URI("http://localhost:8080/ws/market?token=" + token));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(result, "First connection handshake must be approved");
        assertEquals("trader1", attributes.get(SessionHandshakeInterceptor.ATTR_CLIENT_ID));
    }

    @Test
    @DisplayName("STRICT REJECTION: Should reject handshake with HTTP 409 Conflict if client already has active session")
    void testHandshakeStrictRejectionForDuplicateSession() throws Exception {
        String token = jwtService.generateToken("trader1", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // 1. First connection is registered
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("sess-001");
        when(session1.isOpen()).thenReturn(true);
        clientSessionManager.registerSession("trader1", session1);
        assertTrue(clientSessionManager.hasActiveSession("trader1"));

        // 2. Duplicate connection attempt arrives
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(new URI("http://localhost:8080/ws/market?token=" + token));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Assert strict rejection
        assertFalse(result, "Duplicate connection attempt must be strictly rejected");
        verify(response).setStatusCode(HttpStatus.CONFLICT);

        // Verify initial session was not affected
        assertTrue(clientSessionManager.hasActiveSession("trader1"), "Initial session must remain active");
        assertEquals(1, clientSessionManager.getActiveSessionCount());
    }
}
