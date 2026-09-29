package com.simulation.mcp;

import com.simulation.model.OrderBookSnapshot;
import com.simulation.model.Trade;
import com.simulation.orderbook.PinnedMarketplaceEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketMcpServerTest {

    private PinnedMarketplaceEngine marketplaceEngine;
    private MarketMcpServer mcpServer;

    @BeforeEach
    void setUp() {
        marketplaceEngine = new PinnedMarketplaceEngine();
        mcpServer = new MarketMcpServer(marketplaceEngine);
    }

    @AfterEach
    void tearDown() {
        marketplaceEngine.shutdown();
    }

    @Test
    @DisplayName("MCP submit_order tool correctly places orders and generates trade buffer entries")
    void testSubmitOrderTool() {
        // Place resting ask
        McpToolResult sellResult = mcpServer.callTool("submit_order", Map.of(
                "agentId", "agent-miner-1",
                "itemId", "RAW_ORE",
                "side", "SELL",
                "price", new BigDecimal("10.00"),
                "quantity", 20
        ));
        assertTrue(sellResult.isSuccess());

        // Match with incoming buy
        McpToolResult buyResult = mcpServer.callTool("submit_order", Map.of(
                "agentId", "agent-crafter-1",
                "itemId", "RAW_ORE",
                "side", "BUY",
                "price", new BigDecimal("10.50"),
                "quantity", 15
        ));
        assertTrue(buyResult.isSuccess());

        // Verify trades were buffered for STOMP stream
        List<Trade> drainedTrades = mcpServer.drainTradeBuffer();
        assertEquals(1, drainedTrades.size());
        Trade trade = drainedTrades.getFirst();
        assertEquals("RAW_ORE", trade.itemId());
        assertEquals(15, trade.quantity());
        assertEquals(new BigDecimal("10.00"), trade.price());

        // Second drain should be empty
        assertTrue(mcpServer.drainTradeBuffer().isEmpty());
    }

    @Test
    @DisplayName("MCP get_order_book tool returns valid snapshot")
    void testGetOrderBookTool() {
        mcpServer.callTool("submit_order", Map.of(
                "agentId", "agent-1",
                "itemId", "WOOD",
                "side", "BUY",
                "price", "5.00",
                "quantity", 10
        ));

        McpToolResult result = mcpServer.callTool("get_order_book", Map.of("itemId", "WOOD"));
        assertTrue(result.isSuccess());
        assertTrue(result.content() instanceof OrderBookSnapshot);
        OrderBookSnapshot snapshot = (OrderBookSnapshot) result.content();
        assertEquals("WOOD", snapshot.itemId());
        assertEquals(10, snapshot.bidDepth());
    }

    @Test
    @DisplayName("MCP get_quote tool returns price quote")
    void testGetQuoteTool() {
        mcpServer.callTool("submit_order", Map.of(
                "agentId", "agent-1",
                "itemId", "IRON",
                "side", "SELL",
                "price", "20.00",
                "quantity", 5
        ));

        McpToolResult quoteResult = mcpServer.callTool("get_quote", Map.of("itemId", "IRON"));
        assertTrue(quoteResult.isSuccess());
        assertNotNull(quoteResult.content());
    }

    @Test
    @DisplayName("Unknown tool returns MCP error result")
    void testUnknownTool() {
        McpToolResult result = mcpServer.callTool("invalid_tool", Map.of());
        assertTrue(result.isError());
        assertFalse(result.isSuccess());
    }
}
