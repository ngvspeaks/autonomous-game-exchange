package com.simulation.service;

import com.simulation.agent.AutonomousAgent;
import com.simulation.mcp.MarketMcpServer;
import com.simulation.model.AgentRole;
import com.simulation.model.AgentState;
import com.simulation.model.MapZone;
import com.simulation.model.MarketStreamFrame;
import com.simulation.model.Trade;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Autonomous market simulation engine implementing Spring Boot's {@link CommandLineRunner}.
 *
 * <p>Key Architecture:
 * <ul>
 *   <li><b>Concurrent Agents:</b> Spawns 100 autonomous agents via {@link Executors#newVirtualThreadPerTaskExecutor()}.</li>
 *   <li><b>Autonomous Agent Lifecycle:</b> Agents maintain state, pathfind between key map zones,
 *       and interact with the market exclusively through {@link MarketMcpServer#callTool(String, java.util.Map)}.</li>
 *   <li><b>High-Frequency Telemetry:</b> Batches agent states, trade ticks, and order book snapshots
 *       every 50ms (20Hz) and streams via Spring STOMP {@link SimpMessagingTemplate} to {@code /topic/market-stream}.</li>
 * </ul>
 */
@Service
public class MarketSimulationEngine implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketSimulationEngine.class);

    public static final String MARKET_STREAM_TOPIC = "/topic/market-stream";
    public static final int AGENT_COUNT = 100;
    public static final int TICK_INTERVAL_MS = 50; // 20Hz (every 50ms)

    private final MarketMcpServer mcpServer;
    private final SimpMessagingTemplate messagingTemplate;

    private final List<AutonomousAgent> agents = new CopyOnWriteArrayList<>();
    private final AtomicLong tickCounter = new AtomicLong(0);

    private ExecutorService agentExecutor;
    private ScheduledExecutorService broadcastExecutor;
    private volatile boolean running = false;

    public MarketSimulationEngine(MarketMcpServer mcpServer, SimpMessagingTemplate messagingTemplate) {
        this.mcpServer = Objects.requireNonNull(mcpServer, "mcpServer cannot be null");
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "messagingTemplate cannot be null");
    }

    @Override
    public void run(String... args) {
        startSimulation();
    }

    /**
     * Initializes agents and starts the virtual-thread simulation and 20Hz broadcast loop.
     */
    public synchronized void startSimulation() {
        if (running) {
            log.info("MarketSimulationEngine is already running.");
            return;
        }

        log.info("Starting MarketSimulationEngine with {} Java 23 Virtual Thread agents...", AGENT_COUNT);
        this.running = true;
        this.agents.clear();

        // 1. Spawn 100 concurrent agents using VirtualThreadPerTaskExecutor
        this.agentExecutor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 1; i <= AGENT_COUNT; i++) {
            AutonomousAgent agent = createAgent(i);
            agents.add(agent);
            agentExecutor.submit(agent);
        }

        log.info("Spawned {} autonomous agents across Virtual Threads.", agents.size());

        // 2. Schedule 50ms (20Hz) batched broadcast loop
        this.broadcastExecutor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("market-broadcaster-", 0).factory()
        );

        broadcastExecutor.scheduleAtFixedRate(
                this::broadcastTick,
                TICK_INTERVAL_MS,
                TICK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        log.info("MarketSimulationEngine 20Hz broadcast loop active on topic '{}'.", MARKET_STREAM_TOPIC);
    }

    private AutonomousAgent createAgent(int index) {
        // Distribute agents across roles: 40 Miners, 35 Crafters, 25 Merchants
        AgentRole role;
        MapZone startingZone;
        String prefix;

        if (index <= 40) {
            role = AgentRole.MINER;
            startingZone = MapZone.MINE;
            prefix = "Miner";
        } else if (index <= 75) {
            role = AgentRole.CRAFTER;
            startingZone = MapZone.LUMBER_MILL;
            prefix = "Crafter";
        } else {
            role = AgentRole.MERCHANT;
            startingZone = MapZone.GRAND_EXCHANGE;
            prefix = "Merchant";
        }

        String id = "agent-%03d".formatted(index);
        String name = "%s #%d".formatted(prefix, index);
        BigDecimal initialGold = new BigDecimal("500.00");

        return new AutonomousAgent(
                id,
                name,
                role,
                startingZone.getX(),
                startingZone.getY(),
                initialGold,
                startingZone,
                mcpServer
        );
    }

    /**
     * Assembles and broadcasts the 50ms market telemetry frame.
     */
    void broadcastTick() {
        if (!running) {
            return;
        }

        try {
            long tickId = tickCounter.incrementAndGet();
            Instant now = Instant.now();

            // Harvest trades executed during this 50ms window
            List<Trade> trades = mcpServer.drainTradeBuffer();

            // Collect snapshots from all 100 agents
            List<AgentState> agentStates = new ArrayList<>(agents.size());
            for (AutonomousAgent agent : agents) {
                agentStates.add(agent.getStateSnapshot());
            }

            // Build payload
            MarketStreamFrame frame = new MarketStreamFrame(
                    tickId,
                    now,
                    agentStates.size(),
                    agentStates,
                    trades,
                    mcpServer.getMarketSnapshots()
            );

            // Broadcast via Spring STOMP
            messagingTemplate.convertAndSend(MARKET_STREAM_TOPIC, frame);

        } catch (Exception e) {
            log.warn("Failed to broadcast market telemetry tick: {}", e.getMessage());
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (!running) {
            return;
        }
        log.info("Shutting down MarketSimulationEngine...");
        this.running = false;

        for (AutonomousAgent agent : agents) {
            agent.stop();
        }

        if (broadcastExecutor != null) {
            broadcastExecutor.shutdownNow();
        }
        if (agentExecutor != null) {
            agentExecutor.shutdownNow();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getAgentCount() {
        return agents.size();
    }

    public long getCurrentTick() {
        return tickCounter.get();
    }

    public List<AutonomousAgent> getAgents() {
        return agents;
    }
}
