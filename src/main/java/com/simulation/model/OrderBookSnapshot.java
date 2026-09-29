package com.simulation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Point-in-time immutable snapshot of order book state, suitable for telemetry and WebSocket streaming.
 *
 * @param itemId    Identifier of the asset/item
 * @param bestBid   Current highest bid price, if any
 * @param bestAsk   Current lowest ask price, if any
 * @param spread    Current spread (bestAsk - bestBid), if both exist
 * @param bidDepth  Total quantity of all resting buy orders
 * @param askDepth  Total quantity of all resting sell orders
 * @param bids      List of aggregated bid price levels (sorted highest to lowest)
 * @param asks      List of aggregated ask price levels (sorted lowest to highest)
 * @param timestamp Instant when snapshot was captured
 */
public record OrderBookSnapshot(
        String itemId,
        Optional<BigDecimal> bestBid,
        Optional<BigDecimal> bestAsk,
        Optional<BigDecimal> spread,
        int bidDepth,
        int askDepth,
        List<OrderLevel> bids,
        List<OrderLevel> asks,
        Instant timestamp
) {}
