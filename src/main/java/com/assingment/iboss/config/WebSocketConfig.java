package com.assingment.iboss.config;

import com.assingment.iboss.websocket.MarketDataWebSocketHandler;
import com.assingment.iboss.websocket.SessionHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketDataWebSocketHandler marketDataWebSocketHandler;
    private final SessionHandshakeInterceptor sessionHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketDataWebSocketHandler, "/ws/market")
                .addInterceptors(sessionHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
