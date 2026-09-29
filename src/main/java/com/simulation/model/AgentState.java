package com.simulation.model;

import java.math.BigDecimal;

/**
 * Immutable point-in-time snapshot of an autonomous agent's state.
 *
 * @param id         Unique agent identifier
 * @param name       Human-readable name of the agent
 * @param role       Economic role (MINER, CRAFTER, MERCHANT)
 * @param x          Current 2D X coordinate on the world map
 * @param y          Current 2D Y coordinate on the world map
 * @param gold       Current gold treasury balance
 * @param targetZone Destination map zone for pathfinding
 * @param lastAction Description or tag of the most recent action executed
 */
public record AgentState(
        String id,
        String name,
        AgentRole role,
        double x,
        double y,
        BigDecimal gold,
        MapZone targetZone,
        String lastAction
) {}
