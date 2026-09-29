package com.simulation.mcp;

import com.simulation.model.Order;
import com.simulation.model.OrderBookSnapshot;
import com.simulation.model.Side;
import com.simulation.model.Trade;
import com.simulation.orderbook.PinnedMarketplaceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Model Context Protocol (MCP) Server for market interactions.
 *
 * <p>All autonomous agents in the simulation interact with the market strictly
 * through {@link #callTool(String, Map)}.
 */
@Component
public class MarketMcpServer {

    private static final Logger log = LoggerFactory.getLogger(MarketMcpServer.class);

    private final PinnedMarketplaceEngine marketplaceEngine;
    private final ConcurrentLinkedQueue<Trade> tradeBuffer = new ConcurrentLinkedQueue<>();

    public MarketMcpServer(PinnedMarketplaceEngine marketplaceEngine) {
        this.marketplaceEngine = Objects.requireNonNull(marketplaceEngine, "marketplaceEngine cannot be null");
    }

    /**
     * Executes an MCP tool call against the market engine.
     *
     * @param toolName  the name of the tool to execute
     * @param arguments input arguments map
     * @return result of the tool invocation
     */
    public McpToolResult callTool(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isBlank()) {
            return McpToolResult.error("Tool name must not be blank");
        }

        try {
            return switch (toolName.toLowerCase()) {
                case "submit_order", "place_order" -> handleSubmitOrder(arguments);
                case "get_order_book", "get_market_depth" -> handleGetOrderBook(arguments);
                case "get_quote" -> handleGetQuote(arguments);
                case "cancel_order" -> handleCancelOrder(arguments);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.warn("MCP tool call '{}' failed: {}", toolName, e.getMessage());
            return McpToolResult.error(e.getMessage());
        }
    }

    private McpToolResult handleSubmitOrder(Map<String, Object> args) {
        String agentId = (String) args.get("agentId");
        String itemId = (String) args.get("itemId");
        Object sideObj = args.get("side");
        Object priceObj = args.get("price");
        Object qtyObj = args.get("quantity");

        if (agentId == null || itemId == null || sideObj == null || priceObj == null || qtyObj == null) {
            return McpToolResult.error("Missing required arguments for submit_order: [agentId, itemId, side, price, quantity]");
        }

        Side side = (sideObj instanceof Side s) ? s : Side.valueOf(sideObj.toString().toUpperCase());
        BigDecimal price = parseBigDecimal(priceObj);
        int quantity = parseInteger(qtyObj);

        Order order = new Order(
                UUID.randomUUID(),
                agentId,
                itemId,
                side,
                price,
                quantity,
                Instant.now()
        );

        List<Trade> executedTrades = marketplaceEngine.match(order);
        if (!executedTrades.isEmpty()) {
            tradeBuffer.addAll(executedTrades);
        }

        int filledQty = executedTrades.stream().mapToInt(Trade::quantity).sum();
        int remainingQty = quantity - filledQty;

        Map<String, Object> responseData = Map.of(
                "orderId", order.orderId().toString(),
                "itemId", itemId,
                "side", side.name(),
                "price", price,
                "requestedQuantity", quantity,
                "filledQuantity", filledQty,
                "remainingQuantity", remainingQty,
                "tradesCount", executedTrades.size(),
                "trades", executedTrades
        );

        return McpToolResult.success(responseData, "Order processed successfully");
    }

    private McpToolResult handleGetOrderBook(Map<String, Object> args) {
        String itemId = (String) args.get("itemId");
        if (itemId == null || itemId.isBlank()) {
            return McpToolResult.error("itemId is required for get_order_book");
        }
        OrderBookSnapshot snapshot = marketplaceEngine.getOrCreateBook(itemId).getSnapshot();
        return McpToolResult.success(snapshot);
    }

    private McpToolResult handleGetQuote(Map<String, Object> args) {
        String itemId = (String) args.get("itemId");
        if (itemId == null || itemId.isBlank()) {
            return McpToolResult.error("itemId is required for get_quote");
        }
        var book = marketplaceEngine.getOrCreateBook(itemId);
        Map<String, Object> quote = Map.of(
                "itemId", itemId,
                "bestBid", book.getBestBid().map(BigDecimal::toString).orElse("N/A"),
                "bestAsk", book.getBestAsk().map(BigDecimal::toString).orElse("N/A"),
                "spread", book.getSpread().map(BigDecimal::toString).orElse("N/A"),
                "bidDepth", book.getBidDepth(),
                "askDepth", book.getAskDepth()
        );
        return McpToolResult.success(quote);
    }

    private McpToolResult handleCancelOrder(Map<String, Object> args) {
        String itemId = (String) args.get("itemId");
        Object orderIdObj = args.get("orderId");
        if (itemId == null || orderIdObj == null) {
            return McpToolResult.error("itemId and orderId are required for cancel_order");
        }

        UUID orderId = (orderIdObj instanceof UUID u) ? u : UUID.fromString(orderIdObj.toString());
        boolean cancelled = marketplaceEngine.getOrCreateBook(itemId).cancelOrder(orderId);
        return McpToolResult.success(Map.of("cancelled", cancelled, "orderId", orderId.toString()));
    }

    /**
     * Drains and returns all trades accumulated in the buffer since the last drain.
     *
     * @return unmodifiable list of drained trades
     */
    public List<Trade> drainTradeBuffer() {
        List<Trade> drained = new ArrayList<>();
        Trade trade;
        while ((trade = tradeBuffer.poll()) != null) {
            drained.add(trade);
        }
        return drained;
    }

    /**
     * Retrieves snapshots for all active order books.
     */
    public Map<String, OrderBookSnapshot> getMarketSnapshots() {
        return marketplaceEngine.getAllSnapshots();
    }

    public PinnedMarketplaceEngine getMarketplaceEngine() {
        return marketplaceEngine;
    }

    private static BigDecimal parseBigDecimal(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return new BigDecimal(val.toString());
    }

    private static int parseInteger(Object val) {
        if (val instanceof Number num) return num.intValue();
        return Integer.parseInt(val.toString());
    }
}
