package com.simulation.orderbook;

import com.simulation.model.Order;
import com.simulation.model.Side;
import com.simulation.model.Trade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinnedMarketplaceEngineTest {

    private PinnedMarketplaceEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PinnedMarketplaceEngine();
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("Pinned worker executes asynchronous matching cleanly")
    void testAsyncPinnedMatching() throws Exception {
        String item = "HEALTH_POTION";

        Order sell = new Order(
                UUID.randomUUID(),
                "seller-1",
                item,
                Side.SELL,
                new BigDecimal("25.00"),
                50,
                Instant.now()
        );

        CompletableFuture<List<Trade>> sellFuture = engine.submitOrderAsync(sell);
        List<Trade> sellTrades = sellFuture.get();
        assertTrue(sellTrades.isEmpty());

        Order buy = new Order(
                UUID.randomUUID(),
                "buyer-1",
                item,
                Side.BUY,
                new BigDecimal("25.00"),
                20,
                Instant.now()
        );

        CompletableFuture<List<Trade>> buyFuture = engine.submitOrderAsync(buy);
        List<Trade> buyTrades = buyFuture.get();

        assertEquals(1, buyTrades.size());
        Trade trade = buyTrades.getFirst();
        assertEquals(20, trade.quantity());
        assertEquals(new BigDecimal("25.00"), trade.price());
        assertEquals(item, trade.itemId());

        OrderBook book = engine.getOrCreateBook(item);
        assertEquals(30, book.getAskDepth());
        assertNotNull(engine.getAllSnapshots().get(item));
    }
}
