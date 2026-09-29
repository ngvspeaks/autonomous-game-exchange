package com.simulation.service;

import com.simulation.mcp.MarketMcpServer;
import com.simulation.model.MarketStreamFrame;
import com.simulation.orderbook.PinnedMarketplaceEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketSimulationEngineTest {

    private PinnedMarketplaceEngine marketplaceEngine;
    private MarketMcpServer mcpServer;
    private SimpMessagingTemplate messagingTemplate;
    private MarketSimulationEngine simulationEngine;

    @BeforeEach
    void setUp() {
        marketplaceEngine = new PinnedMarketplaceEngine();
        mcpServer = new MarketMcpServer(marketplaceEngine);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        simulationEngine = new MarketSimulationEngine(mcpServer, messagingTemplate);
    }

    @AfterEach
    void tearDown() {
        simulationEngine.shutdown();
        marketplaceEngine.shutdown();
    }

    @Test
    @DisplayName("Spawns 100 concurrent agents using Virtual Threads and streams at 20Hz")
    void testEngineSpawns100AgentsAndBroadcasts() throws InterruptedException {
        simulationEngine.startSimulation();

        assertTrue(simulationEngine.isRunning());
        assertEquals(100, simulationEngine.getAgentCount());
        assertEquals(100, simulationEngine.getAgents().size());

        // Wait for multiple 50ms ticks to fire
        Thread.sleep(250);

        assertTrue(simulationEngine.getCurrentTick() >= 2, "Should have executed multiple 50ms broadcast ticks");

        // Verify STOMP broadcast payload
        ArgumentCaptor<MarketStreamFrame> frameCaptor = ArgumentCaptor.forClass(MarketStreamFrame.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq(MarketSimulationEngine.MARKET_STREAM_TOPIC),
                frameCaptor.capture()
        );

        MarketStreamFrame frame = frameCaptor.getValue();
        assertNotNull(frame);
        assertEquals(100, frame.activeAgents());
        assertEquals(100, frame.agents().size());
        assertNotNull(frame.orderBooks());
    }
}
