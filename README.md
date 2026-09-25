# Market Data Service (OKX Proxy & Real-Time Streaming Aggregator)

A production-grade Java Spring Boot backend service that acts as an authenticated intermediary proxy and streaming aggregator for cryptocurrency market data. Clients connect to this service to query the top 20 spot trading pairs by volume and subscribe to live Depth-5 order book feeds over WebSockets, with strict single-session-per-client enforcement and role-based access control.

---

## Architecture Overview

```
                      +---------------------------------------+
                      |       Authenticated Clients           |
                      |   (Trading Bots / Web Terminals)      |
                      +-------------------+-------------------+
                                          |
                        HTTP / WebSocket  | (Bearer JWT Token)
                                          v
+---------------------------------------------------------------------------------+
|                       Spring Boot Market Data Service                           |
|                                                                                 |
|  +------------------------+  +-----------------------------------------------+  |
|  |     Security Layer     |  |          WebSocket Connection Manager         |  |
|  |   - Spring Security    |  |   - SessionHandshakeInterceptor               |  |
|  |   - JWT Validator      |  |   - ClientSessionManager (Strict Rejection)   |  |
|  |   - RBAC (USER, ADMIN) |  |   - MarketDataWebSocketHandler                |  |
|  +------------------------+  +-----------------------+-----------------------+  |
|                                                      |                          |
|             +----------------------------------------+                          |
|             |                                                                   |
|             v                                                                   |
|  +---------------------------------------------------------------------------+  |
|  |                        Redis Distributed Cache Layer                      |  |
|  |  - Custom TTLs: Tickers (3s), OrderBook (2s), Pairs (10m), Profile (5m)   |  |
|  |  - Resilient Error Handling (Zero-downtime fallback to origin on failure) |  |
|  |  - Jackson JSON serialization with JavaTimeModule & Type preservation   |  |
|  +---------------------------------------------------------------------------+  |
|                                     ^                                           |
|                                     | (Cache Aside / Read-Through)              |
|                                     v                                           |
|  +---------------------------------------------------------------------------+  |
|  |                       Market Data Aggregator Engine                       |  |
|  |  +---------------------------------------------------------------------+  |  |
|  |  |                  MockMarketDataEngine (OKX Mirror)                  |  |  |
|  |  |  - 20 Spot Pairs sorted descending by 24h quote volume (USDT)       |  |  |
|  |  |  - Brownian random-walk micro ticks & Depth-5 Order Book generator  |  |  |
|  |  |  - OKX v5 Public API Schema (`code`, `data`, `books5` channel)      |  |  |
|  |  +---------------------------------------------------------------------+  |  |
|  +---------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------+
```

---

## Single-Session Enforcement (Evaluation Note)

> **How Single-Session Enforcement Works:**
> 
> 1. **Client Identity Binding**: Each client is authenticated using a cryptographically signed JWT token. The client's unique identity (`clientId` / `username`) is extracted from the verified token claims during the WebSocket handshake (`SessionHandshakeInterceptor`).
> 2. **Strict Rejection Policy**: Before allowing the WebSocket upgrade to complete, the interceptor consults the `ClientSessionManager`. If an active, open connection already exists for that `clientId`, the server **strictly rejects the new connection attempt** by returning an HTTP `409 Conflict` response status code.
> 3. **Non-Disruptive Safety**: The existing client session remains fully operational, connected, and streaming without interruption.
> 4. **Concurrency Safety & Zero Resource Leaks**: All session lookups, registrations, and removals use thread-safe `ConcurrentHashMap` instances with synchronized atomic checks, preventing race conditions. When a client socket closes, the `afterConnectionClosed` hook immediately unregisters the session and unlinks all instrument subscriptions, preventing memory and thread leaks.

---

## Distributed Redis Caching Layer

The service integrates a high-performance **Redis Cache Layer** to optimize read-heavy REST endpoints, prevent backend database/engine lockups, and absorb high-frequency polling from trading bots and web dashboards.

### Cache Policies & TTL Matrix

| Cache Name | Endpoint | Target Entity | Cache Key | TTL | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `market:tickers` | `GET /api/market/tickers` | `OkxResponse<OkxTicker>` | Single (default) | **3 seconds** | Absorbs spike loads while ensuring real-time freshness |
| `market:orderbook` | `GET /api/market/orderbook` | `OkxOrderBookData` | `#pair` (e.g. `BTC-USDT`) | **2 seconds** | Depth-5 order book snapshots per instrument |
| `market:pairs` | `GET /api/market/pairs` | `List<String>` | Single (default) | **10 minutes** | Static supported pair symbol catalog |
| `user:profile` | `GET /api/auth/me` | `Map<String, Object>` | `#authentication.name` | **5 minutes** | Cached user identity and granted authorities |

### Architectural Highlights

1. **Zero-Downtime Resilient Cache Error Handling**:
   - The cache layer implements `CachingConfigurer.errorHandler()`.
   - If Redis becomes temporarily unreachable, encounters a network timeout, or restarts, the application **automatically catches the Redis exception and falls back to invoking the live service method**.
   - API clients never receive a `500 Internal Server Error` due to Redis downtime.
2. **Jackson JSON Type-Preserving Serialization**:
   - Uses `GenericJackson2JsonRedisSerializer` configured with `JavaTimeModule` and default polymorphic typing (`LaissezFaireSubTypeValidator`).
   - Cached entries in Redis are stored as human-readable JSON payloads rather than opaque Java binary blobs (`JdkSerializationRedisSerializer`), enabling cross-language inspection and interoperability.

---

## Data Model Conventions (OKX Specification Alignment)

All internal domain models adhere to standard Java camelCase naming conventions, while guaranteeing strict 1:1 compliance with the external OKX v5 Public API contract via Jackson `@JsonProperty` annotations:

| Java Field (`OkxTicker`) | JSON Attribute (OKX v5) | Type | Example / Description |
| :--- | :--- | :--- | :--- |
| `instrumentType` | `instType` | `String` | `"SPOT"` |
| `instrumentId` | `instId` | `String` | `"BTC-USDT"` |
| `lastPrice` | `last` | `String` | `"64250.00"` |
| `lastSize` | `lastSz` | `String` | `"1.25"` |
| `askPrice` | `askPx` | `String` | `"64256.42"` |
| `askSize` | `askSz` | `String` | `"2.84"` |
| `bidPrice` | `bidPx` | `String` | `"64243.58"` |
| `bidSize` | `bidSz` | `String` | `"3.12"` |
| `openPrice24h` | `open24h` | `String` | `"63800.00"` |
| `highPrice24h` | `high24h` | `String` | `"65100.00"` |
| `lowPrice24h` | `low24h` | `String` | `"63400.00"` |
| `volume24h` | `vol24h` | `String` | `"28793.77"` (Base currency volume) |
| `volumeCurrency24h` | `volCcy24h` | `String` | `"1850000000.00"` (Quote currency volume in USDT) |
| `startOfDayUtc0` | `sodUtc0` | `String` | `"63900.00"` |
| `startOfDayUtc8` | `sodUtc8` | `String` | `"64050.00"` |
| `timestamp` | `ts` | `String` | `"1727280000000"` (Epoch ms) |

---

## Features

- **OKX Public API Contract Mirroring**:
  - `GET /api/market/tickers`: Top 20 spot trading pairs sorted descending by 24-hour volume in quote currency (`volCcy24h`).
  - `GET /api/market/orderbook`: Depth-5 order book snapshot with 5 ask levels and 5 bid levels.
  - Returns responses adhering to the OKX v5 specification: `{"code": "0", "msg": "", "data": [...]}`.
- **Real-Time WebSocket Streaming**:
  - Path: `/ws/market?token=<JWT>`.
  - Supports standard OKX commands (`op: subscribe`, `op: unsubscribe`, `op: ping`) as well as simplified actions (`action: SUBSCRIBE`).
  - Broadcasts live Depth-5 (`books5`) order book updates every 350ms to active subscribers.
- **Authentication & Authorization**:
  - Every REST and WebSocket endpoint is secured.
  - JWT token generation via HMAC-SHA256 (`/api/auth/login`).
  - Role-Based Access Control: `ROLE_USER` for market data, `ROLE_ADMIN` for session monitoring.
- **Interactive OpenAPI / Swagger UI**:
  - Live Swagger documentation with Bearer JWT authentication support at `/swagger-ui.html`.

---

## Pre-configured User Accounts

| Username | Password | Role(s) | Description |
| :--- | :--- | :--- | :--- |
| `trader1` | `password123` | `ROLE_USER` | Standard trading client account |
| `trader2` | `password123` | `ROLE_USER` | Second client account for multi-client testing |
| `admin` | `admin123` | `ROLE_ADMIN`, `ROLE_USER` | Administrator for session inspection & management |

---

## Getting Started

### Prerequisites
- **Java 21** or later (`java -version`)
- **Maven Wrapper** is included in the project repository (`./mvnw`)

### 1. Build and Run the Service

```bash
# Build the project
./mvnw clean compile

# Run the test suite
./mvnw test

# Start the Spring Boot application
./mvnw spring-boot:run
```

The service will start on port `8085`.

---

## API Testing Guide

### 0. Automated All-In-One Test Suite
You can execute the automated end-to-end verification script with a single command while the service is running:
```bash
python3 test_live_service.py
```
This script automatically runs:
1. Trader authentication and JWT retrieval
2. Admin authentication and JWT retrieval
3. Top 20 spot tickers validation (sorted strictly descending by 24h volume)
4. Depth-5 order book validation (asks/bids structure)
5. RBAC authorization tests (Trader forbidden from Admin endpoints)
6. WebSocket single-session enforcement (First connection accepted with HTTP 101, duplicate connection strictly rejected with HTTP 409 Conflict)

---

### 1. Interactive Swagger UI
Open your browser to:
```
http://localhost:8085/swagger-ui.html
```
Click **Authorize**, enter your Bearer token obtained from `/api/auth/login`, and test any endpoint interactively.

---

### 2. cURL Walkthrough

#### Step A: Authenticate & Obtain JWT Token
```bash
curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "trader1", "password": "password123"}'
```
*Response:*
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "username": "trader1",
  "roles": ["ROLE_USER"],
  "expiresInMs": 86400000
}
```

Save the token to an environment variable:
```bash
export TOKEN="<YOUR_TOKEN_HERE>"
```

#### Step B: Fetch Top 20 Spot Tickers
```bash
curl -s -X GET http://localhost:8085/api/market/tickers \
  -H "Authorization: Bearer $TOKEN"
```
*Output (OKX v5 Format, sorted descending by 24h USDT volume):*
```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instType": "SPOT",
      "instId": "BTC-USDT",
      "last": "64250.0",
      "lastSz": "1.250",
      "askPx": "64256.42",
      "askSz": "2.840",
      "bidPx": "64243.58",
      "bidSz": "3.120",
      "vol24h": "28793.77",
      "volCcy24h": "1850000000.00",
      "ts": "1727280000000"
    },
    {
      "instType": "SPOT",
      "instId": "ETH-USDT",
      "last": "3450.5",
      "volCcy24h": "1220000000.00",
      ...
    }
  ]
}
```

#### Step C: Fetch Order Book Snapshot
```bash
curl -s -X GET "http://localhost:8085/api/market/orderbook?pair=BTC-USDT" \
  -H "Authorization: Bearer $TOKEN"
```

#### Step D: Admin Session Monitoring (Requires `admin` credentials)
```bash
# Obtain Admin Token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Inspect Sessions
curl -s -X GET http://localhost:8085/api/admin/sessions \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

### 3. WebSocket Streaming & Single-Session Verification

Using `wscat` (installable via `npm install -g wscat`):

#### A. Connect First Session (`trader1`)
```bash
wscat -c "ws://localhost:8085/ws/market?token=$TOKEN"
```
*Server Output:*
```json
{
  "event": "connected",
  "clientId": "trader1",
  "sessionId": "4f9d2a1b",
  "msg": "Authenticated and connected to Market Data Service proxy. Single-session enforced."
}
```

#### B. Subscribe to Order Book Feed
Type the subscription command into the `wscat` console:
```json
{"op": "subscribe", "args": [{"channel": "books5", "instId": "BTC-USDT"}]}
```
*Server streams instant snapshot followed by periodic depth updates:*
```json
{
  "arg": {"channel": "books5", "instId": "BTC-USDT"},
  "action": "snapshot",
  "data": [
    {
      "asks": [
        ["64256.42", "2.140", "0", "4"],
        ["64258.10", "1.850", "0", "2"],
        ["64260.00", "4.200", "0", "7"],
        ["64262.50", "0.950", "0", "1"],
        ["64265.00", "3.400", "0", "5"]
      ],
      "bids": [
        ["64243.58", "3.120", "0", "5"],
        ["64241.90", "1.450", "0", "3"],
        ["64240.00", "5.100", "0", "8"],
        ["64238.20", "2.250", "0", "2"],
        ["64235.00", "1.800", "0", "4"]
      ],
      "ts": "1727280000350",
      "seqId": 1001
    }
  ]
}
```

#### C. Verify Strict Rejection (In a Second Terminal)
Run the exact same command with the same token:
```bash
wscat -c "ws://localhost:8085/ws/market?token=$TOKEN"
```
*Output:*
```
error: Unexpected server response: 409
```
The second connection is **immediately rejected with HTTP 409 Conflict**. The first connection continues streaming without interruption.

---

## Assumptions Made

1. **Authentication Token Delivery**: During WebSocket handshakes, browser clients pass the JWT token in the query parameter (`?token=<JWT>`), as standard browser `WebSocket` APIs do not support arbitrary custom headers during the HTTP upgrade. For non-browser clients (bots/proxies), the standard `Authorization: Bearer <JWT>` header is also supported.
2. **Order Book Depth**: The `books5` channel was implemented as requested ("subscribe to order book WebSocket feed"), providing Depth-5 top bids/asks with sequence IDs and timestamps.
3. **Data Source Default**: In accordance with instructions, mock data is enabled by default to ensure immediate, dependable evaluation without dependency on external network conditions, rate limits, or region-based restrictions.

---

## Known Limitations

1. **In-Memory Session Registry**: Active WebSocket sessions are tracked in-memory using `ConcurrentHashMap`. This is optimal for single-node deployments. In a multi-node, horizontally scaled environment, single-session enforcement would require a distributed coordination layer such as Redis with atomic distributed locking and Redis Pub/Sub for cross-node eviction/rejection.
2. **Order Book Level**: Depth-5 (`books5`) snapshots are provided. Full L2 differential depth books (400 levels with sequence number gap reconciliation) can be layered onto this same pipeline if high-depth order books are required.
