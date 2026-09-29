package com.simulation.model;

import java.math.BigDecimal;

/**
 * Aggregated price level information for order book depth.
 *
 * @param price         Price at this level
 * @param totalQuantity Total resting quantity across all orders at this price
 * @param orderCount    Number of active orders resting at this price
 */
public record OrderLevel(
        BigDecimal price,
        int totalQuantity,
        int orderCount
) {}
