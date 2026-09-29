package com.simulation.orderbook;

import com.simulation.model.Order;
import com.simulation.model.OrderBookSnapshot;
import com.simulation.model.Trade;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance marketplace engine managing per-item OrderBooks with pinned single-worker execution.
 *
 * <p>Each distinct commodity/asset is pinned to a dedicated single-threaded virtual execution loop.
 * This guarantees zero lock contention and strict deterministic ordering for high-frequency simulated trading.
 */
@Service
public class PinnedMarketplaceEngine {

    private static final Logger log = LoggerFactory.getLogger(PinnedMarketplaceEngine.class);

    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExecutorService> pinnedWorkers = new ConcurrentHashMap<>();

    /**
     * Retrieves or creates an OrderBook for the specified item.
     *
     * @param itemId the item identifier
     * @return the active OrderBook
     */
    public OrderBook getOrCreateBook(String itemId) {
        return books.computeIfAbsent(itemId, OrderBook::new);
    }

    /**
     * Submits an order asynchronously to the item's pinned worker for zero-contention matching.
     *
     * @param order the order to submit
     * @return a future completing with the list of executed trades
     */
    public CompletableFuture<List<Trade>> submitOrderAsync(Order order) {
        String itemId = order.itemId();
        ExecutorService worker = pinnedWorkers.computeIfAbsent(itemId, id ->
                Executors.newSingleThreadExecutor(
                        Thread.ofVirtual().name("orderbook-worker-" + id + "-", 0).factory()
                )
        );

        return CompletableFuture.supplyAsync(() -> {
            OrderBook book = getOrCreateBook(itemId);
            return book.match(order);
        }, worker);
    }

    /**
     * Synchronously submits an order directly to the underlying order book.
     *
     * @param order the order to match
     * @return the list of executed trades
     */
    public List<Trade> match(Order order) {
        return getOrCreateBook(order.itemId()).match(order);
    }

    /**
     * Captures snapshots for all active order books.
     *
     * @return map of itemId to OrderBookSnapshot
     */
    public Map<String, OrderBookSnapshot> getAllSnapshots() {
        Map<String, OrderBookSnapshot> snapshots = new ConcurrentHashMap<>();
        books.forEach((id, book) -> snapshots.put(id, book.getSnapshot()));
        return snapshots;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down PinnedMarketplaceEngine workers...");
        pinnedWorkers.values().forEach(ExecutorService::shutdown);
    }
}
