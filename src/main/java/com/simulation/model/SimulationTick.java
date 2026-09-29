package com.simulation.model;

/**
 * Compact, allocation-efficient simulation tick data carrier using Java Records.
 */
public record SimulationTick(
        long tickId,
        long timestampNano,
        int activeEntities,
        double[] metrics,
        String status
) {}
