package com.simulation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Java 23 record representing an incoming or resting order in the marketplace.
 *
 * @param orderId   Globally unique identifier of the order
 * @param agentId   Identifier of the agent or player submitting the order
 * @param itemId    Identifier of the commodity or game asset being traded
 * @param side      Market side (BUY or SELL)
 * @param price     Unit limit price
 * @param quantity  Requested order quantity (strictly positive)
 * @param timestamp Order creation timestamp (used for time-priority ordering)
 */
public record Order(
        UUID orderId,
        String agentId,
        String itemId,
        Side side,
        BigDecimal price,
        int quantity,
        Instant timestamp
) {
    public Order {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Objects.requireNonNull(side, "side cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");

        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be strictly positive: " + price);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be strictly positive: " + quantity);
        }
    }

    /**
     * Creates a copy of this order with an updated remaining quantity.
     *
     * @param newQuantity the updated quantity
     * @return a new Order instance
     */
    public Order withQuantity(int newQuantity) {
        return new Order(orderId, agentId, itemId, side, price, newQuantity, timestamp);
    }
}
