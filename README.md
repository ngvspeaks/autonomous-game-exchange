# Autonomous Game Exchange (Aethelgard Market Simulation)

An in-memory, high-performance double-auction continuous OrderBook and multi-agent economy simulation built with **Java 23 Virtual Threads**, **Spring Boot 3.4.1**, **Model Context Protocol (MCP)**, and real-time **STOMP WebSocket** broadcasting with an HTML5 dark-fantasy retro canvas UI.

![Aethelgard Market Simulation](https://raw.githubusercontent.com/ngvspeaks/autonomous-game-exchange/main/docs/preview.png)

## Architecture Overview

```
                      +-----------------------------------+
                      |      MarketSimulationEngine       |
                      |   100 Virtual-Thread Agents       |
                      +-----------------+-----------------+
                                        | (Autonomous Loops)
                                        v
                            +-----------------------+
                            |    MarketMcpServer    |
                            |  Strict Tool Routing  |
                            +-----------+-----------+
                                        |
                 +----------------------+----------------------+
                 |                                             |
                 v                                             v
    +--------------------------+                 +----------------------------+
    | PinnedMarketplaceEngine  |                 |     STOMP Broadcasting     |
    | Dedicated Worker / Symbol|                 |   20Hz (50ms) Telemetry    |
    +------------+-------------+                 +--------------+-------------+
                 |                                              |
                 v                                              v
    +--------------------------+                 +----------------------------+
    |   OrderBook (Java 23)    |                 |   HTML5 Dark Fantasy UI    |
    | Price-Time Priority FIFO |                 |  60 FPS Lerp Interpolation |
    | Zero Lock Contention     |                 |  Speech Bubbles & HUD      |
    +--------------------------+                 +----------------------------+
```

## Key Features

1. **In-Memory Double-Auction Engine (`OrderBook`)**:
   - Strict **Price-Time Priority (FIFO)**.
   - Bids ordered in descending order via `ConcurrentSkipListMap` + `ConcurrentLinkedQueue`.
   - Asks ordered in ascending order via `ConcurrentSkipListMap` + `ConcurrentLinkedQueue`.
   - Immediate matching at the maker price with partial fills and resting liquidity management.
   - **Zero Synchronized Lock Bottlenecks**: Uses `ReentrantLock` avoiding carrier thread pinning in Java 23 Virtual Threads.
   - Pinned single-worker execution per symbol via `PinnedMarketplaceEngine`.

2. **Model Context Protocol (`MarketMcpServer`)**:
   - Autonomous agents interact with the market strictly via `callTool(...)`.
   - Tools: `submit_order`, `get_order_book`, `get_quote`, `cancel_order`.
   - Buffers executed trades for telemetry streaming.

3. **100 Concurrent Autonomous Agents (`MarketSimulationEngine`)**:
   - Spawns 100 concurrent agents using `Executors.newVirtualThreadPerTaskExecutor()`.
   - Agent State: `(id, name, role [MINER, CRAFTER, MERCHANT], x, y, gold, targetZone, lastAction)`.
   - Pathfinding across map zones:
     - `LUMBER_MILL (100, 100)`
     - `MINE (700, 100)`
     - `GRAND_EXCHANGE (400, 300)`
     - `GUILD (400, 500)`
   - Clean virtual thread yields via `Thread.sleep(ThreadLocalRandom.current().nextInt(200, 800))`.

4. **Real-Time Telemetry & STOMP Streaming**:
   - Batches agent state, trade ticks, and order book snapshots every 50ms (20Hz).
   - Broadcasts via Spring STOMP `SimpMessagingTemplate` to `/topic/market-stream`.

5. **Retro Dark-Fantasy Frontend (`index.html`)**:
   - 800x600 HTML5 Canvas with smooth 60 FPS coordinate linear interpolation (`lerp`).
   - Colored retro sprites with role badges (`[M]`, `[C]`, `[$]`).
   - Dynamic floating speech bubbles for MCP actions.
   - Rising green floating text (`+50g`) on matched trades.
   - Real-time HUD showing active population, match latency, trade velocity, order book spreads, and live trade log.

## Getting Started

### Prerequisites
- **Java 23** (JDK 23 with preview features enabled)
- **Maven 3.9+** (or included `./mvnw`)

### Running Locally

```bash
# Run unit & concurrency tests (19 tests)
./mvnw clean test

# Run the simulation server
./mvnw spring-boot:run
```

Navigate to [http://localhost:8080/](http://localhost:8080/) in your browser to launch the live market simulation dashboard.

## Project Structure

```
src/
├── main/
│   ├── java/com/simulation/
│   │   ├── SimulationApplication.java
│   │   ├── agent/
│   │   │   └── AutonomousAgent.java
│   │   ├── mcp/
│   │   │   ├── MarketMcpServer.java
│   │   │   └── McpToolResult.java
│   │   ├── model/
│   │   │   ├── AgentRole.java
│   │   │   ├── AgentState.java
│   │   │   ├── MapZone.java
│   │   │   ├── MarketStreamFrame.java
│   │   │   ├── Order.java
│   │   │   ├── OrderBookSnapshot.java
│   │   │   ├── OrderLevel.java
│   │   │   ├── Side.java
│   │   │   ├── SimulationTick.java
│   │   │   └── Trade.java
│   │   ├── orderbook/
│   │   │   ├── OrderBook.java
│   │   │   └── PinnedMarketplaceEngine.java
│   │   ├── service/
│   │   │   ├── MarketSimulationEngine.java
│   │   │   └── SimulationEngine.java
│   │   └── websocket/
│   │       ├── RawWebSocketConfig.java
│   │       ├── SimulationWebSocketHandler.java
│   │       └── WebSocketConfig.java
│   └── resources/
│       ├── application.properties
│       └── static/
│           └── index.html
└── test/
    └── java/com/simulation/
        ├── SimulationApplicationTests.java
        ├── agent/AutonomousAgentTest.java
        ├── mcp/MarketMcpServerTest.java
        ├── orderbook/
        │   ├── OrderBookTest.java
        │   └── PinnedMarketplaceEngineTest.java
        └── service/MarketSimulationEngineTest.java
```
