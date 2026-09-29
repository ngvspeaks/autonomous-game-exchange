package com.simulation.orderbook;

import com.simulation.model.Order;
import com.simulation.model.OrderBookSnapshot;
import com.simulation.model.OrderLevel;
import com.simulation.model.Side;
import com.simulation.model.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance in-memory double-auction OrderBook for simulated game marketplaces.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li><b>Price-Time Priority:</b> Uses {@link ConcurrentSkipListMap} sorted in descending order for Bids
 *       and ascending order for Asks. Price levels contain {@link ConcurrentLinkedQueue} to enforce FIFO time priority.</li>
 *   <li><b>Zero Synchronized Lock Bottlenecks:</b> Avoids Java intrinsic monitor locks (synchronized blocks/methods)
 *       to prevent thread pinning on Java 23 Virtual Threads. Matching operations use low-overhead {@link ReentrantLock}
 *       and lock-free concurrent collections.</li>
 *   <li><b>Pinned Worker Ready:</b> When executed inside a pinned single-worker thread or virtual thread actor,
 *       the lock operates without contention at maximum memory bus throughput.</li>
 *   <li><b>Continuous Double-Auction Matching:</b> Executes immediate matches, supports partial fills,
 *       and leaves unexecuted remaining volume as resting liquidity on the book.</li>
 * </ul>
 */
public class OrderBook {

    private final String itemId;

    /**
     * Bids sorted highest price first (descending).
     */
    private final ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<RestingOrder>> bids =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    /**
     * Asks sorted lowest price first (ascending).
     */
    private final ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<RestingOrder>> asks =
            new ConcurrentSkipListMap<>(Comparator.naturalOrder());

    /**
     * Fast O(1) index of all resting orders by orderId for cancellation and status lookups.
     */
    private final ConcurrentMap<UUID, RestingOrder> orderIndex = new ConcurrentHashMap<>();

    /**
     * Non-synchronized reentrant lock ensuring atomic matching cycles without JVM monitor lock pinning.
     */
    private final ReentrantLock matchLock = new ReentrantLock();

    /**
     * Constructs a generic OrderBook not pinned to a predefined item identifier.
     */
    public OrderBook() {
        this.itemId = null;
    }

    /**
     * Constructs an OrderBook dedicated to a specific item.
     *
     * @param itemId the game asset identifier
     */
    public OrderBook(String itemId) {
        this.itemId = Objects.requireNonNull(itemId, "itemId cannot be null");
    }

    /**
     * Matches an incoming order against the resting liquidity in the book.
     *
     * <p>Executes immediate trades at the resting maker orders' prices, handles partial fills,
     * and deposits any remaining unfilled quantity onto the book as resting liquidity.
     *
     * @param incomingOrder the order to match
     * @return an unmodifiable list of trades generated from this matching event
     */
    public List<Trade> match(Order incomingOrder) {
        Objects.requireNonNull(incomingOrder, "incomingOrder cannot be null");

        if (this.itemId != null && !this.itemId.equals(incomingOrder.itemId())) {
            throw new IllegalArgumentException(
                    "Order itemId '%s' does not match OrderBook itemId '%s'".formatted(incomingOrder.itemId(), this.itemId)
            );
        }

        matchLock.lock();
        try {
            return switch (incomingOrder.side()) {
                case BUY -> matchBuyOrder(incomingOrder);
                case SELL -> matchSellOrder(incomingOrder);
            };
        } finally {
            matchLock.unlock();
        }
    }

    private List<Trade> matchBuyOrder(Order incomingOrder) {
        int remainingQty = incomingOrder.quantity();
        List<Trade> trades = new ArrayList<>();

        var iterator = asks.entrySet().iterator();
        while (iterator.hasNext() && remainingQty > 0) {
            var entry = iterator.next();
            BigDecimal askPrice = entry.getKey();

            // Buyer's maximum bid price must be >= resting ask price
            if (incomingOrder.price().compareTo(askPrice) < 0) {
                break;
            }

            ConcurrentLinkedQueue<RestingOrder> queue = entry.getValue();
            while (!queue.isEmpty() && remainingQty > 0) {
                RestingOrder restingAsk = queue.peek();
                if (restingAsk == null) {
                    break;
                }

                if (restingAsk.isCancelled() || restingAsk.getRemainingQuantity() <= 0) {
                    queue.poll();
                    orderIndex.remove(restingAsk.orderId());
                    continue;
                }

                int availableQty = restingAsk.getRemainingQuantity();
                int matchQty = Math.min(remainingQty, availableQty);
                remainingQty -= matchQty;
                restingAsk.decrement(matchQty);

                // In double auction, resting order sets the trade execution price (maker price)
                Trade trade = new Trade(
                        UUID.randomUUID(),
                        incomingOrder.orderId(),
                        restingAsk.orderId(),
                        incomingOrder.itemId(),
                        askPrice,
                        matchQty,
                        Instant.now()
                );
                trades.add(trade);

                if (restingAsk.getRemainingQuantity() <= 0) {
                    queue.poll();
                    orderIndex.remove(restingAsk.orderId());
                }
            }

            if (queue.isEmpty()) {
                iterator.remove();
            }
        }

        // If not completely filled, leave remaining quantity as resting bid
        if (remainingQty > 0) {
            RestingOrder restingOrder = new RestingOrder(incomingOrder, remainingQty);
            bids.computeIfAbsent(incomingOrder.price(), k -> new ConcurrentLinkedQueue<>()).add(restingOrder);
            orderIndex.put(restingOrder.orderId(), restingOrder);
        }

        return Collections.unmodifiableList(trades);
    }

    private List<Trade> matchSellOrder(Order incomingOrder) {
        int remainingQty = incomingOrder.quantity();
        List<Trade> trades = new ArrayList<>();

        var iterator = bids.entrySet().iterator();
        while (iterator.hasNext() && remainingQty > 0) {
            var entry = iterator.next();
            BigDecimal bidPrice = entry.getKey();

            // Seller's minimum ask price must be <= resting bid price
            if (incomingOrder.price().compareTo(bidPrice) > 0) {
                break;
            }

            ConcurrentLinkedQueue<RestingOrder> queue = entry.getValue();
            while (!queue.isEmpty() && remainingQty > 0) {
                RestingOrder restingBid = queue.peek();
                if (restingBid == null) {
                    break;
                }

                if (restingBid.isCancelled() || restingBid.getRemainingQuantity() <= 0) {
                    queue.poll();
                    orderIndex.remove(restingBid.orderId());
                    continue;
                }

                int availableQty = restingBid.getRemainingQuantity();
                int matchQty = Math.min(remainingQty, availableQty);
                remainingQty -= matchQty;
                restingBid.decrement(matchQty);

                // Resting bid sets the execution price (maker price)
                Trade trade = new Trade(
                        UUID.randomUUID(),
                        restingBid.orderId(),
                        incomingOrder.orderId(),
                        incomingOrder.itemId(),
                        bidPrice,
                        matchQty,
                        Instant.now()
                );
                trades.add(trade);

                if (restingBid.getRemainingQuantity() <= 0) {
                    queue.poll();
                    orderIndex.remove(restingBid.orderId());
                }
            }

            if (queue.isEmpty()) {
                iterator.remove();
            }
        }

        // If not completely filled, leave remaining quantity as resting ask
        if (remainingQty > 0) {
            RestingOrder restingOrder = new RestingOrder(incomingOrder, remainingQty);
            asks.computeIfAbsent(incomingOrder.price(), k -> new ConcurrentLinkedQueue<>()).add(restingOrder);
            orderIndex.put(restingOrder.orderId(), restingOrder);
        }

        return Collections.unmodifiableList(trades);
    }

    /**
     * Cancels an existing resting order in O(1) time.
     *
     * @param orderId ID of the order to cancel
     * @return true if the order was found and cancelled, false otherwise
     */
    public boolean cancelOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        RestingOrder resting = orderIndex.remove(orderId);
        if (resting != null) {
            resting.cancel();
            return true;
        }
        return false;
    }

    /**
     * Retrieves the current remaining state of an order if still resting on the book.
     *
     * @param orderId the order identifier
     * @return optional containing the order with its current remaining quantity
     */
    public Optional<Order> getOrder(UUID orderId) {
        RestingOrder resting = orderIndex.get(orderId);
        if (resting != null && !resting.isCancelled() && resting.getRemainingQuantity() > 0) {
            return Optional.of(resting.toCurrentOrder());
        }
        return Optional.empty();
    }

    /**
     * Retrieves the best (highest) bid price currently available.
     *
     * @return optional best bid price
     */
    public Optional<BigDecimal> getBestBid() {
        for (Map.Entry<BigDecimal, ConcurrentLinkedQueue<RestingOrder>> entry : bids.entrySet()) {
            for (RestingOrder order : entry.getValue()) {
                if (!order.isCancelled() && order.getRemainingQuantity() > 0) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves the best (lowest) ask price currently available.
     *
     * @return optional best ask price
     */
    public Optional<BigDecimal> getBestAsk() {
        for (Map.Entry<BigDecimal, ConcurrentLinkedQueue<RestingOrder>> entry : asks.entrySet()) {
            for (RestingOrder order : entry.getValue()) {
                if (!order.isCancelled() && order.getRemainingQuantity() > 0) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves the current market spread (bestAsk - bestBid), if both sides have liquidity.
     *
     * @return optional market spread
     */
    public Optional<BigDecimal> getSpread() {
        Optional<BigDecimal> bestAsk = getBestAsk();
        Optional<BigDecimal> bestBid = getBestBid();
        if (bestAsk.isPresent() && bestBid.isPresent()) {
            return Optional.of(bestAsk.get().subtract(bestBid.get()));
        }
        return Optional.empty();
    }

    /**
     * Total resting bid quantity across all price levels.
     *
     * @return aggregated bid quantity
     */
    public int getBidDepth() {
        int depth = 0;
        for (ConcurrentLinkedQueue<RestingOrder> queue : bids.values()) {
            for (RestingOrder order : queue) {
                if (!order.isCancelled()) {
                    depth += order.getRemainingQuantity();
                }
            }
        }
        return depth;
    }

    /**
     * Total resting ask quantity across all price levels.
     *
     * @return aggregated ask quantity
     */
    public int getAskDepth() {
        int depth = 0;
        for (ConcurrentLinkedQueue<RestingOrder> queue : asks.values()) {
            for (RestingOrder order : queue) {
                if (!order.isCancelled()) {
                    depth += order.getRemainingQuantity();
                }
            }
        }
        return depth;
    }

    /**
     * Total number of active resting orders in the book.
     *
     * @return count of resting orders
     */
    public int getOrderCount() {
        return orderIndex.size();
    }

    /**
     * Returns the item ID associated with this book, or null if unassigned.
     *
     * @return item ID
     */
    public String getItemId() {
        return itemId;
    }

    /**
     * Returns an aggregated list of bid price levels (highest to lowest).
     *
     * @return list of bid levels
     */
    public List<OrderLevel> getBidLevels() {
        List<OrderLevel> levels = new ArrayList<>();
        for (Map.Entry<BigDecimal, ConcurrentLinkedQueue<RestingOrder>> entry : bids.entrySet()) {
            int qty = 0;
            int count = 0;
            for (RestingOrder order : entry.getValue()) {
                if (!order.isCancelled() && order.getRemainingQuantity() > 0) {
                    qty += order.getRemainingQuantity();
                    count++;
                }
            }
            if (qty > 0) {
                levels.add(new OrderLevel(entry.getKey(), qty, count));
            }
        }
        return Collections.unmodifiableList(levels);
    }

    /**
     * Returns an aggregated list of ask price levels (lowest to highest).
     *
     * @return list of ask levels
     */
    public List<OrderLevel> getAskLevels() {
        List<OrderLevel> levels = new ArrayList<>();
        for (Map.Entry<BigDecimal, ConcurrentLinkedQueue<RestingOrder>> entry : asks.entrySet()) {
            int qty = 0;
            int count = 0;
            for (RestingOrder order : entry.getValue()) {
                if (!order.isCancelled() && order.getRemainingQuantity() > 0) {
                    qty += order.getRemainingQuantity();
                    count++;
                }
            }
            if (qty > 0) {
                levels.add(new OrderLevel(entry.getKey(), qty, count));
            }
        }
        return Collections.unmodifiableList(levels);
    }

    /**
     * Captures a point-in-time immutable snapshot of this order book.
     *
     * @return current snapshot
     */
    public OrderBookSnapshot getSnapshot() {
        return new OrderBookSnapshot(
                itemId,
                getBestBid(),
                getBestAsk(),
                getSpread(),
                getBidDepth(),
                getAskDepth(),
                getBidLevels(),
                getAskLevels(),
                Instant.now()
        );
    }

    /**
     * Clears all resting orders and resets the book.
     */
    public void clear() {
        matchLock.lock();
        try {
            bids.clear();
            asks.clear();
            orderIndex.clear();
        } finally {
            matchLock.unlock();
        }
    }

    /**
     * Internal mutable state holder for an order resting on the book.
     */
    static final class RestingOrder {
        private final Order order;
        private final AtomicInteger remainingQuantity;
        private volatile boolean cancelled = false;

        RestingOrder(Order order, int initialRemaining) {
            this.order = order;
            this.remainingQuantity = new AtomicInteger(initialRemaining);
        }

        UUID orderId() {
            return order.orderId();
        }

        String agentId() {
            return order.agentId();
        }

        Side side() {
            return order.side();
        }

        BigDecimal price() {
            return order.price();
        }

        Instant timestamp() {
            return order.timestamp();
        }

        int getRemainingQuantity() {
            return remainingQuantity.get();
        }

        void decrement(int amount) {
            remainingQuantity.addAndGet(-amount);
        }

        boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            this.cancelled = true;
            this.remainingQuantity.set(0);
        }

        Order toCurrentOrder() {
            return order.withQuantity(remainingQuantity.get());
        }
    }
}
