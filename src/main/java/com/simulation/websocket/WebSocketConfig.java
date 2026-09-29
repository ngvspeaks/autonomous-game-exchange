package com.simulation.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring STOMP WebSocket configuration for real-time market data broadcasting.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple in-memory broker on /topic and /queue
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix for incoming messages bound for @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP endpoint /ws-market with SockJS fallback
        registry.addEndpoint("/ws-market")
                .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws-market")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
