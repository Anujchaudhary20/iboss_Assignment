package com.assingment.iboss.websocket;

import com.assingment.iboss.security.JwtService;
import com.assingment.iboss.service.ClientSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_CLIENT_ID = "clientId";

    private final JwtService jwtService;
    private final ClientSessionManager clientSessionManager;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        String token = extractToken(request);

        // 1. Authenticate Token
        if (!StringUtils.hasText(token) || !jwtService.validateToken(token)) {
            log.warn("WebSocket handshake rejected: Missing or invalid JWT token.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String username = jwtService.extractUsername(token);
        if (!StringUtils.hasText(username)) {
            log.warn("WebSocket handshake rejected: Token does not contain subject/username.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 2. Strict Rejection: Enforce Single-Session-per-Client
        if (clientSessionManager.hasActiveSession(username)) {
            log.warn("STRICT REJECTION: Handshake rejected with 409 Conflict. Client '{}' already has an active session.", username);
            response.setStatusCode(HttpStatus.CONFLICT);
            return false;
        }

        // 3. Bind client ID to handshake attributes for subsequent session management
        attributes.put(ATTR_CLIENT_ID, username);
        log.info("WebSocket handshake approved for client '{}'", username);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }

    private String extractToken(ServerHttpRequest request) {
        // Check query parameter ?token=...
        URI uri = request.getURI();
        if (uri.getQuery() != null) {
            String[] params = uri.getQuery().split("&");
            for (String param : params) {
                String[] pair = param.split("=");
                if (pair.length == 2 && "token".equalsIgnoreCase(pair[0])) {
                    return pair[1];
                }
            }
        }

        // Check Authorization header: Bearer ...
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String header = servletRequest.getServletRequest().getHeader("Authorization");
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }

        return null;
    }
}
