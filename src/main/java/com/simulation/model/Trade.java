package com.simulation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Java 23 record representing an executed trade between a buyer and a seller.
 *
 * @param tradeId     Globally unique identifier of the executed trade
 * @param buyOrderId  Order ID of the buying counterparty
 * @param sellOrderId Order ID of the selling counterparty
 * @param itemId      Identifier of the traded game item
 * @param price       Execution price determined by double-auction matching
 * @param quantity    Quantity traded in this match execution
 * @param timestamp   Execution timestamp
 */
public record Trade(
        UUID tradeId,
        UUID buyOrderId,
        UUID sellOrderId,
        String itemId,
        BigDecimal price,
        int quantity,
        Instant timestamp
) {
    public Trade {
        Objects.requireNonNull(tradeId, "tradeId cannot be null");
        Objects.requireNonNull(buyOrderId, "buyOrderId cannot be null");
        Objects.requireNonNull(sellOrderId, "sellOrderId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");

        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be strictly positive: " + price);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be strictly positive: " + quantity);
        }
    }
}
