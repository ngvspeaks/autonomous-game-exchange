package com.simulation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulation.model.SimulationTick;
import com.simulation.websocket.SimulationWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated simulation engine leveraging Virtual Threads for low-latency background execution.
 */
@Service
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private final SimulationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final AtomicLong tickCounter = new AtomicLong(0);
    private ScheduledExecutorService executorService;

    public SimulationEngine(SimulationWebSocketHandler webSocketHandler, ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startSimulation() {
        // Run simulation loop using lightweight virtual threads
        this.executorService = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("sim-engine-", 0).factory()
        );

        // Run simulation at 60 ticks per second (~16.6ms intervals)
        executorService.scheduleAtFixedRate(this::tick, 0, 16, TimeUnit.MILLISECONDS);
        log.info("Simulation engine started with Virtual Threads on 16ms tick interval.");
    }

    private void tick() {
        try {
            long tickId = tickCounter.incrementAndGet();
            long nowNano = System.nanoTime();

            // Generate synthetic high-density simulation vector
            double[] metrics = new double[] {
                    Math.sin(tickId * 0.05),
                    Math.cos(tickId * 0.05),
                    (tickId % 1000) / 1000.0
            };

            SimulationTick frame = new SimulationTick(
                    tickId,
                    nowNano,
                    webSocketHandler.getActiveSubscriberCount(),
                    metrics,
                    "RUNNING"
            );

            // Fast serialization directly to JSON
            String jsonPayload = objectMapper.writeValueAsString(frame);
            webSocketHandler.broadcast(jsonPayload);

        } catch (JsonProcessingException e) {
            log.error("Tick serialization error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Simulation tick failure: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stopSimulation() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public long getCurrentTick() {
        return tickCounter.get();
    }
}
