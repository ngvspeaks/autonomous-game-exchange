package com.simulation.agent;

import com.simulation.mcp.MarketMcpServer;
import com.simulation.model.AgentRole;
import com.simulation.model.AgentState;
import com.simulation.model.MapZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Autonomous marketplace agent running concurrently on a Java 23 Virtual Thread.
 *
 * <p>Each agent maintains internal state, pathfinds across map zones, and interacts
 * with the marketplace strictly via {@link MarketMcpServer#callTool(String, Map)}.
 */
public class AutonomousAgent implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AutonomousAgent.class);
    private static final double ARRIVAL_THRESHOLD = 15.0;
    private static final double MOVEMENT_SPEED = 25.0;

    private final String id;
    private final String name;
    private final AgentRole role;
    private final MarketMcpServer mcpServer;

    private volatile double x;
    private volatile double y;
    private volatile BigDecimal gold;
    private volatile MapZone targetZone;
    private volatile String lastAction;
    private volatile boolean running = true;

    public AutonomousAgent(
            String id,
            String name,
            AgentRole role,
            double initialX,
            double initialY,
            BigDecimal initialGold,
            MapZone initialTarget,
            MarketMcpServer mcpServer
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.x = initialX;
        this.y = initialY;
        this.gold = Objects.requireNonNull(initialGold, "initialGold cannot be null");
        this.targetZone = Objects.requireNonNull(initialTarget, "initialTarget cannot be null");
        this.lastAction = "INITIALIZED";
        this.mcpServer = Objects.requireNonNull(mcpServer, "mcpServer cannot be null");
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                step();
                // Clean virtual thread yield
                int sleepDurationMs = ThreadLocalRandom.current().nextInt(200, 800);
                Thread.sleep(sleepDurationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Agent {} encountered error during autonomous cycle: {}", id, e.getMessage());
            }
        }
    }

    /**
     * Executes one atomic simulation step: pathfinding or zone-based economic action.
     */
    public void step() {
        double dx = targetZone.getX() - x;
        double dy = targetZone.getY() - y;
        double dist = Math.hypot(dx, dy);

        if (dist > ARRIVAL_THRESHOLD) {
            // Pathfind towards target zone
            double stepSize = Math.min(MOVEMENT_SPEED, dist);
            this.x += (dx / dist) * stepSize;
            this.y += (dy / dist) * stepSize;
            this.lastAction = "PATHFINDING_TO_" + targetZone.name();
        } else {
            // Arrived at destination zone
            this.x = targetZone.getX();
            this.y = targetZone.getY();
            executeZoneBehavior();
        }
    }

    private void executeZoneBehavior() {
        switch (role) {
            case MINER -> handleMinerBehavior();
            case CRAFTER -> handleCrafterBehavior();
            case MERCHANT -> handleMerchantBehavior();
        }
    }

    private void handleMinerBehavior() {
        if (targetZone == MapZone.MINE) {
            this.lastAction = "MINING_RAW_ORE";
            this.targetZone = MapZone.GRAND_EXCHANGE;
        } else if (targetZone == MapZone.GRAND_EXCHANGE) {
            BigDecimal askPrice = BigDecimal.valueOf(10.0 + ThreadLocalRandom.current().nextDouble(-0.5, 1.5))
                    .setScale(2, RoundingMode.HALF_UP);
            int qty = ThreadLocalRandom.current().nextInt(3, 10);

            mcpServer.callTool("submit_order", Map.of(
                    "agentId", id,
                    "itemId", "RAW_ORE",
                    "side", "SELL",
                    "price", askPrice,
                    "quantity", qty
            ));

            this.lastAction = "SOLD_ORE_AT_EXCHANGE";
            this.gold = gold.add(askPrice.multiply(BigDecimal.valueOf(qty)));
            this.targetZone = MapZone.MINE;
        } else {
            this.targetZone = MapZone.MINE;
        }
    }

    private void handleCrafterBehavior() {
        if (targetZone == MapZone.LUMBER_MILL) {
            this.lastAction = "HARVESTING_LUMBER";
            this.targetZone = MapZone.GUILD;
        } else if (targetZone == MapZone.GUILD) {
            this.lastAction = "FORGING_MYTHIC_EQUIPMENT";
            this.targetZone = MapZone.GRAND_EXCHANGE;
        } else if (targetZone == MapZone.GRAND_EXCHANGE) {
            // Buy ore input
            BigDecimal buyPrice = BigDecimal.valueOf(11.0 + ThreadLocalRandom.current().nextDouble(0.0, 1.0))
                    .setScale(2, RoundingMode.HALF_UP);
            mcpServer.callTool("submit_order", Map.of(
                    "agentId", id,
                    "itemId", "RAW_ORE",
                    "side", "BUY",
                    "price", buyPrice,
                    "quantity", 3
            ));

            // Sell crafted weapon
            BigDecimal swordPrice = BigDecimal.valueOf(80.0 + ThreadLocalRandom.current().nextDouble(-5.0, 10.0))
                    .setScale(2, RoundingMode.HALF_UP);
            mcpServer.callTool("submit_order", Map.of(
                    "agentId", id,
                    "itemId", "MYTHIC_SWORD",
                    "side", "SELL",
                    "price", swordPrice,
                    "quantity", 1
            ));

            this.lastAction = "TRADED_GOODS_AT_EXCHANGE";
            this.gold = gold.add(swordPrice.subtract(buyPrice.multiply(BigDecimal.valueOf(3))));
            this.targetZone = MapZone.LUMBER_MILL;
        } else {
            this.targetZone = MapZone.LUMBER_MILL;
        }
    }

    private void handleMerchantBehavior() {
        if (targetZone == MapZone.GRAND_EXCHANGE) {
            // Inspect order book depth via MCP
            mcpServer.callTool("get_order_book", Map.of("itemId", "RAW_ORE"));

            // Place two-sided market making quotes
            BigDecimal bid = BigDecimal.valueOf(9.50 + ThreadLocalRandom.current().nextDouble(-0.2, 0.4))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal ask = bid.add(BigDecimal.valueOf(1.50 + ThreadLocalRandom.current().nextDouble(0.1, 0.5)))
                    .setScale(2, RoundingMode.HALF_UP);

            mcpServer.callTool("submit_order", Map.of(
                    "agentId", id,
                    "itemId", "RAW_ORE",
                    "side", "BUY",
                    "price", bid,
                    "quantity", 5
            ));

            mcpServer.callTool("submit_order", Map.of(
                    "agentId", id,
                    "itemId", "RAW_ORE",
                    "side", "SELL",
                    "price", ask,
                    "quantity", 5
            ));

            this.lastAction = "MARKET_MAKING_RAW_ORE";
            // Alternate visiting regional distribution hubs
            this.targetZone = (ThreadLocalRandom.current().nextBoolean()) ? MapZone.GUILD : MapZone.MINE;
        } else {
            this.lastAction = "AUDITED_REGIONAL_SUPPLIES";
            this.targetZone = MapZone.GRAND_EXCHANGE;
        }
    }

    public void stop() {
        this.running = false;
    }

    /**
     * Captures a thread-safe snapshot of the agent's current state.
     */
    public AgentState getStateSnapshot() {
        return new AgentState(
                id,
                name,
                role,
                Math.round(x * 100.0) / 100.0,
                Math.round(y * 100.0) / 100.0,
                gold,
                targetZone,
                lastAction
        );
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AgentRole getRole() {
        return role;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public BigDecimal getGold() {
        return gold;
    }

    public MapZone getTargetZone() {
        return targetZone;
    }

    public String getLastAction() {
        return lastAction;
    }
}
