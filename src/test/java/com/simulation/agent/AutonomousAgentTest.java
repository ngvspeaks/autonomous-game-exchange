package com.simulation.agent;

import com.simulation.mcp.MarketMcpServer;
import com.simulation.model.AgentRole;
import com.simulation.model.AgentState;
import com.simulation.model.MapZone;
import com.simulation.orderbook.PinnedMarketplaceEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomousAgentTest {

    private PinnedMarketplaceEngine engine;
    private MarketMcpServer mcpServer;

    @BeforeEach
    void setUp() {
        engine = new PinnedMarketplaceEngine();
        mcpServer = new MarketMcpServer(engine);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("Agent pathfinds towards target zone and updates coordinates")
    void testAgentPathfinding() {
        // Agent starts far from MINE (700, 100)
        AutonomousAgent agent = new AutonomousAgent(
                "agent-test-1",
                "Miner Bob",
                AgentRole.MINER,
                0.0,
                0.0,
                new BigDecimal("100.00"),
                MapZone.MINE,
                mcpServer
        );

        double initialDist = MapZone.MINE.distanceTo(agent.getX(), agent.getY());
        agent.step();

        double newDist = MapZone.MINE.distanceTo(agent.getX(), agent.getY());
        assertTrue(newDist < initialDist, "Agent must get closer to the destination zone");
        assertTrue(agent.getLastAction().startsWith("PATHFINDING_TO_MINE"));
    }

    @Test
    @DisplayName("Agent arriving at destination executes economic action and updates target")
    void testAgentArrivalAndAction() {
        // Place miner directly at MINE
        AutonomousAgent agent = new AutonomousAgent(
                "agent-test-2",
                "Miner Alice",
                AgentRole.MINER,
                MapZone.MINE.getX(),
                MapZone.MINE.getY(),
                new BigDecimal("100.00"),
                MapZone.MINE,
                mcpServer
        );

        agent.step();

        // Miner should have mined ore and set target to GRAND_EXCHANGE
        assertEquals(MapZone.GRAND_EXCHANGE, agent.getTargetZone());
        assertEquals("MINING_RAW_ORE", agent.getLastAction());

        // Snapshot reflects accurate state
        AgentState snapshot = agent.getStateSnapshot();
        assertNotNull(snapshot);
        assertEquals("agent-test-2", snapshot.id());
        assertEquals(AgentRole.MINER, snapshot.role());
        assertEquals(MapZone.GRAND_EXCHANGE, snapshot.targetZone());
        assertEquals("MINING_RAW_ORE", snapshot.lastAction());
    }

    @Test
    @DisplayName("Crafter at Exchange trades goods via MCP")
    void testCrafterAtExchange() {
        AutonomousAgent crafter = new AutonomousAgent(
                "agent-test-3",
                "Crafter Vulcan",
                AgentRole.CRAFTER,
                MapZone.GRAND_EXCHANGE.getX(),
                MapZone.GRAND_EXCHANGE.getY(),
                new BigDecimal("500.00"),
                MapZone.GRAND_EXCHANGE,
                mcpServer
        );

        crafter.step();

        assertEquals("TRADED_GOODS_AT_EXCHANGE", crafter.getLastAction());
        assertEquals(MapZone.LUMBER_MILL, crafter.getTargetZone());
    }
}
