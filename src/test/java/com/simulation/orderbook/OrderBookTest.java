package com.simulation.orderbook;

import com.simulation.model.Order;
import com.simulation.model.OrderBookSnapshot;
import com.simulation.model.Side;
import com.simulation.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    private OrderBook orderBook;
    private final String itemId = "MYTHIC_SWORD";

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(itemId);
    }

    private Order createOrder(Side side, String price, int quantity) {
        return new Order(
                UUID.randomUUID(),
                "agent-" + UUID.randomUUID().toString().substring(0, 8),
                itemId,
                side,
                new BigDecimal(price),
                quantity,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Resting orders without match remain on the book")
    void testRestingLiquidity() {
        Order buyOrder = createOrder(Side.BUY, "100.00", 10);
        List<Trade> trades = orderBook.match(buyOrder);

        assertTrue(trades.isEmpty());
        assertEquals(10, orderBook.getBidDepth());
        assertEquals(0, orderBook.getAskDepth());
        assertEquals(Optional.of(new BigDecimal("100.00")), orderBook.getBestBid());
        assertEquals(Optional.empty(), orderBook.getBestAsk());
    }

    @Test
    @DisplayName("Single exact match executes at resting maker price")
    void testExactMatchAtMakerPrice() {
        // Seller rests 10 units at $100.00
        Order sellOrder = createOrder(Side.SELL, "100.00", 10);
        List<Trade> initialTrades = orderBook.match(sellOrder);
        assertTrue(initialTrades.isEmpty());

        // Buyer arrives willing to pay up to $105.00 for 10 units
        Order buyOrder = createOrder(Side.BUY, "105.00", 10);
        List<Trade> matchTrades = orderBook.match(buyOrder);

        assertEquals(1, matchTrades.size());
        Trade trade = matchTrades.getFirst();
        assertEquals(buyOrder.orderId(), trade.buyOrderId());
        assertEquals(sellOrder.orderId(), trade.sellOrderId());
        assertEquals(itemId, trade.itemId());
        // Maker price: trade executes at resting ask price ($100.00)
        assertEquals(new BigDecimal("100.00"), trade.price());
        assertEquals(10, trade.quantity());

        // Book should now be empty
        assertEquals(0, orderBook.getBidDepth());
        assertEquals(0, orderBook.getAskDepth());
        assertEquals(0, orderBook.getOrderCount());
    }

    @Test
    @DisplayName("Partial fill on resting order leaves remaining liquidity")
    void testPartialFillOfRestingOrder() {
        Order sellOrder = createOrder(Side.SELL, "50.00", 100);
        orderBook.match(sellOrder);

        // Buyer takes 40 units
        Order buyOrder = createOrder(Side.BUY, "50.00", 40);
        List<Trade> trades = orderBook.match(buyOrder);

        assertEquals(1, trades.size());
        assertEquals(40, trades.getFirst().quantity());
        assertEquals(new BigDecimal("50.00"), trades.getFirst().price());

        // 60 units should remain resting on ask
        assertEquals(60, orderBook.getAskDepth());
        assertEquals(0, orderBook.getBidDepth());
        assertEquals(1, orderBook.getOrderCount());

        Optional<Order> resting = orderBook.getOrder(sellOrder.orderId());
        assertTrue(resting.isPresent());
        assertEquals(60, resting.get().quantity());
    }

    @Test
    @DisplayName("Incoming order sweeps multiple price levels and leaves resting remainder")
    void testMultiLevelSweepWithRestingRemainder() {
        // Resting asks:
        // 10 units @ 10.00
        // 20 units @ 11.00
        // 30 units @ 12.00
        Order ask1 = createOrder(Side.SELL, "10.00", 10);
        Order ask2 = createOrder(Side.SELL, "11.00", 20);
        Order ask3 = createOrder(Side.SELL, "12.00", 30);
        orderBook.match(ask1);
        orderBook.match(ask2);
        orderBook.match(ask3);

        assertEquals(60, orderBook.getAskDepth());

        // Buyer arrives willing to pay up to 11.50 for 40 units
        Order incomingBuy = createOrder(Side.BUY, "11.50", 40);
        List<Trade> trades = orderBook.match(incomingBuy);

        // Should match ask1 (10 @ 10.00) and ask2 (20 @ 11.00) -> 30 total
        assertEquals(2, trades.size());
        assertEquals(10, trades.get(0).quantity());
        assertEquals(new BigDecimal("10.00"), trades.get(0).price());
        assertEquals(20, trades.get(1).quantity());
        assertEquals(new BigDecimal("11.00"), trades.get(1).price());

        // Remaining 10 units cannot match ask3 (12.00 > 11.50), so it rests as Bid @ 11.50
        assertEquals(10, orderBook.getBidDepth());
        assertEquals(Optional.of(new BigDecimal("11.50")), orderBook.getBestBid());

        // ask3 remains untouched (30 units @ 12.00)
        assertEquals(30, orderBook.getAskDepth());
        assertEquals(Optional.of(new BigDecimal("12.00")), orderBook.getBestAsk());
        assertEquals(Optional.of(new BigDecimal("0.50")), orderBook.getSpread());
    }

    @Test
    @DisplayName("Price-Time Priority: earliest order at same price is matched first")
    void testPriceTimePriorityFIFO() throws InterruptedException {
        // Two resting buy orders at the exact same price
        Order earlyBid = createOrder(Side.BUY, "100.00", 15);
        orderBook.match(earlyBid);

        // Small sleep ensures distinct timestamp
        Thread.sleep(2);

        Order laterBid = createOrder(Side.BUY, "100.00", 25);
        orderBook.match(laterBid);

        // Seller sells 20 units @ 100.00
        Order sellOrder = createOrder(Side.SELL, "100.00", 20);
        List<Trade> trades = orderBook.match(sellOrder);

        assertEquals(2, trades.size());
        // First trade matches earlyBid completely (15 units)
        assertEquals(earlyBid.orderId(), trades.get(0).buyOrderId());
        assertEquals(15, trades.get(0).quantity());

        // Second trade matches 5 units of laterBid
        assertEquals(laterBid.orderId(), trades.get(1).buyOrderId());
        assertEquals(5, trades.get(1).quantity());

        // remaining laterBid should have 20 units
        assertEquals(20, orderBook.getBidDepth());
    }

    @Test
    @DisplayName("Order cancellation removes liquidity and prevents match")
    void testOrderCancellation() {
        Order sellOrder = createOrder(Side.SELL, "50.00", 25);
        orderBook.match(sellOrder);

        assertEquals(25, orderBook.getAskDepth());
        boolean cancelled = orderBook.cancelOrder(sellOrder.orderId());
        assertTrue(cancelled);

        // Order is no longer active
        assertEquals(0, orderBook.getAskDepth());
        assertFalse(orderBook.getOrder(sellOrder.orderId()).isPresent());

        // Subsequent buy order should not match
        Order buyOrder = createOrder(Side.BUY, "55.00", 10);
        List<Trade> trades = orderBook.match(buyOrder);
        assertTrue(trades.isEmpty());
        assertEquals(10, orderBook.getBidDepth());
    }

    @Test
    @DisplayName("OrderBook snapshot captures correct levels and spread")
    void testSnapshot() {
        orderBook.match(createOrder(Side.BUY, "99.00", 10));
        orderBook.match(createOrder(Side.BUY, "99.00", 15));
        orderBook.match(createOrder(Side.BUY, "98.00", 30));

        orderBook.match(createOrder(Side.SELL, "101.00", 5));
        orderBook.match(createOrder(Side.SELL, "102.50", 20));

        OrderBookSnapshot snapshot = orderBook.getSnapshot();
        assertNotNull(snapshot);
        assertEquals(itemId, snapshot.itemId());
        assertEquals(Optional.of(new BigDecimal("99.00")), snapshot.bestBid());
        assertEquals(Optional.of(new BigDecimal("101.00")), snapshot.bestAsk());
        assertEquals(Optional.of(new BigDecimal("2.00")), snapshot.spread());
        assertEquals(55, snapshot.bidDepth());
        assertEquals(25, snapshot.askDepth());

        // Check aggregated levels
        assertEquals(2, snapshot.bids().size());
        assertEquals(new BigDecimal("99.00"), snapshot.bids().get(0).price());
        assertEquals(25, snapshot.bids().get(0).totalQuantity());
        assertEquals(2, snapshot.bids().get(0).orderCount());
    }

    @Test
    @DisplayName("Concurrent matching preserves exact total volume and thread safety")
    void testConcurrentMatchingStress() throws Exception {
        int threadCount = 20;
        int ordersPerThread = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger totalExecutedQuantity = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    Side side = (threadIdx % 2 == 0) ? Side.BUY : Side.SELL;
                    // Overlapping price range [98, 102]
                    String price = (side == Side.BUY) ? "100.00" : "100.00";

                    for (int j = 0; j < ordersPerThread; j++) {
                        Order order = createOrder(side, price, 10);
                        List<Trade> trades = orderBook.match(order);
                        for (Trade t : trades) {
                            totalExecutedQuantity.addAndGet(t.quantity());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        int totalSubmitted = threadCount * ordersPerThread * 10;
        int remainingDepth = orderBook.getBidDepth() + orderBook.getAskDepth();
        // Conservation of volume: total submitted = (2 * total traded) + remaining depth
        int accounted = (totalExecutedQuantity.get() * 2) + remainingDepth;

        assertEquals(totalSubmitted, accounted, "Volume must be strictly conserved across concurrent matches");
    }
}
