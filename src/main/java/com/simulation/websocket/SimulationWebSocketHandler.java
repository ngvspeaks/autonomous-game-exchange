package com.simulation.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, non-blocking raw WebSocket handler for streaming simulation ticks.
 */
@Component
public class SimulationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SimulationWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Client connected: {} (Active: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Client disconnected: {} (Active: {})", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Transport error on session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /**
     * High-speed broadcast to all active subscribers.
     */
    public void broadcast(String payload) {
        if (sessions.isEmpty()) {
            return;
        }

        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.error("Failed to send simulation frame to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public int getActiveSubscriberCount() {
        return sessions.size();
    }
}
