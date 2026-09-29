package com.simulation.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Batched market stream payload broadcast every 50ms (20Hz) to STOMP subscribers.
 *
 * @param tickId        Monotonically increasing sequence number
 * @param timestamp     Instant when this batch frame was assembled
 * @param activeAgents  Total active agent count
 * @param agents        Snapshot of all agent states in this tick
 * @param trades        Trades executed across all order books during the last 50ms window
 * @param orderBooks    Point-in-time snapshots of the active commodity order books
 */
public record MarketStreamFrame(
        long tickId,
        Instant timestamp,
        int activeAgents,
        List<AgentState> agents,
        List<Trade> trades,
        Map<String, OrderBookSnapshot> orderBooks
) {}
