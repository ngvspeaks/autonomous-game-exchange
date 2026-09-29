package com.simulation.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Raw WebSocket configuration for streaming low-latency raw simulation ticks.
 */
@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final SimulationWebSocketHandler simulationWebSocketHandler;

    public RawWebSocketConfig(SimulationWebSocketHandler simulationWebSocketHandler) {
        this.simulationWebSocketHandler = simulationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(simulationWebSocketHandler, "/ws/simulation")
                .setAllowedOrigins("*");
    }
}
