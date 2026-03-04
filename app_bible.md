# 📜 Stock Market Simulator: The Quant Bible

> "Amateurs focus on returns. Professionals focus on risk."

This document serves as the single source of truth for the **Stock Market Simulator's** quantitative architecture. It details the logic, protocols, and safeguards that differentiate this engine from a simple game.

---

## 🧠 1. The Intelligence Engine (Auto-Pilot)

The core differentiator of this system is its ability to autonomously adapt to changing market conditions.

### A. Strategy Tournament (The Brain)
*   **Concept**: A "Darwinian" evolution of strategies.
*   **Frequency**: Runs daily in the background.
*   **Candidates**: 22+ variations of Momentum, Mean Reversion, Volatility, Volume, and Hybrid models.
*   **Logic**:
    1.  **Walk-Forward Validation**: Strategies are trained on 80% of data and tested on the recent 20% (Out-of-Sample).
    2.  **Regime Alignment**: The "Test Window" is dynamically adjusted to capture the current market regime (e.g., Bull, Bear, Sideways).
    3.  **Scoring**: Strategies are ranked by **Risk-Adjusted Alpha** (Return > Benchmark), not just raw return.

### B. Signal-Proportional Sizing
*   **Concept**: Stronger signals get larger portfolio allocations.
*   **Logic**: Each strategy scores candidates (e.g., % distance above SMA, RSI depth). Weights are normalized: `weight = score / sum(all_scores)`.
*   **Impact**: A stock 40% above its SMA gets 4x the allocation of one 10% above, instead of the same 1/N.

### C. Auto-Pilot Switching (The Reflex)
*   **Trigger**: The weekly Strategy Tournament dynamically backtests 40+ models to find the current market winner.
*   **Switch Condition**: If a new strategy outperforms the active strategy on the Out-of-Sample Test Window, the simulation adopts the new winner.
*   **🧠 The Quant Guard (Sticky ML Anchor)**: 
    *   A special execution circuit breaker exists to protect the Deep Learning Model (`MULTI_FACTOR_DNN`).
    *   To prevent whiplash during short-term market noise, if the active strategy is the ML model, any legacy challenger (e.g., Simple Momentum) MUST produce an Out-of-Sample Alpha **≥ 1.5x higher** to dethrone it.
    *   If the challenger fails this strict margin, the ML model is retained as the portfolio anchor. This ensures regime shifts only occur when mathematically undeniable.

---

## 🛡️ 2. Risk Management Protocols

Capital preservation is the mathematically optimal path to long-term growth.

### A. Regime Filter (The Shield)
*   **Metric**: NIFTY 50 vs. 200-Day SMA.
*   **Logic**:
    *   **Bull Market**: Benchmark > SMA 200. Aggressive allocation allowed.
    *   **Bear Market**: Benchmark < SMA 200. **100% Cash**. No new buys. Preservation mode.
*   **Impact**: Protects the portfolio from systemic crashes (e.g., 2008, 2020) by sitting on the sidelines.

### B. Volatility & Stops
*   **ATR Trailing Stop**:
    *   Stops are calculated using **Average True Range (ATR)**, adapting to the stock's personality.
    *   *Standard*: 2.0x ATR.
    *   *Volatile/Turbulent*: 1.5x ATR (Tighter leash for stocks with ≥2 days of >2% swings in last 5 days).
*   **Hard Stop**: A hard floor of **-7%** per position is enforced to prevent catastrophic loss.
*   **Enforcement**: Applied in both Backtester and Daily Simulation engine.

### C. Liquidity Guard
*   **Turnover Floor**: **₹5 Crore** daily value traded.
*   **Price Floor**: **₹50** (enforced in both Backtester and Daily Simulation).
*   **Purpose**: Eliminates "Penny Stocks" and ensures simulated trades could actually be executed in real life without slippage impact.

### D. Sector Exposure Cap
*   **Max Weight**: 30% of portfolio per sector.
*   **Logic**: If strategy allocates >30% to a single sector (e.g., Finance), weights are proportionally scaled down.
*   **Purpose**: Prevents concentration risk from sector-heavy strategies.

### E. Live Trading Safety
*   **Max Order Value**: ₹50,000 per order (orders exceeding this are skipped with a log warning).
*   **Market Hours Guard**: Orders only submitted during NSE hours (9:15 AM – 3:30 PM IST).
*   **Integer Shares**: Fractional quantities are floored. Sub-1-share orders are skipped.

### F. Weekly Rebalancing
*   **Rebalance Day**: **Monday only** — new portfolio allocations are computed weekly.
*   **Daily Risk**: Stop-losses and regime filter still run **every day** to protect capital.
*   **Non-Monday**: Existing positions are held. Only risk-triggered exits occur.
*   **Impact**: Reduces portfolio churn by ~80%, saving ~3-5% annually in transaction costs.

### G. Fundamental Quality Filter
*   **Primary Source**: IndianAPI.in (`GET /stock?name={company}`) — provides P/E, ROE, D/E, MarketCap, and Analyst Sentiment.
*   **Fallback Source**: Yahoo Finance `quoteSummary` API (free, no rate limit). Zerodha paid API ready when available.
*   **Persistent Cache**: Room database with **7-day TTL**. Stale cache is preferred over no data.
*   **Rate Limiting**: 1 request per second to IndianAPI.in. HTTP 429 triggers automatic Yahoo Finance fallback.
*   **No Mock Data Policy**: If all sources fail and no cache exists, the stock is **skipped entirely** rather than using fabricated fundamentals.
*   **Criteria**: ROE ≥ 12% **and** Debt/Equity ≤ 1.0.
*   **Applied**: On Monday rebalance days only. Stocks with missing data pass (benefit of the doubt).
*   **Purpose**: Prevents RSI and Bollinger Mean Reversion from buying "falling knives" — stocks that are cheap because fundamentals are deteriorating.

---

## 📊 3. Quantitative Integrity

### A. Deterministic Slippage
*   Simulators often show unrealistic profits because they assume execution at the exact closing price.
*   **Our Model**: We apply a **fixed 0.20% slippage + commission** drag on every trade.
*   *Effect*: A strategy must beat the market by >0.4% per round trip to be profitable.

### B. Look-Ahead Bias & Data Integrity
*   **The Cardinal Sin**: Using tomorrow's data to trade today.
*   **Our Solution**:
    *   Signals are generated using data up to **T-1** (Yesterday).
    *   Execution happens at **T** (Today's Close).
*   **Split-Adjusted Data (Crucial)**:
    *   Raw prices misinterpret Stock Splits (e.g., 1:10) as 90% crashes, triggering false Stop-Losses.
    *   **Mandate**: All historical data MUST be **Split-Adjusted** (Adjusted Close). The engine back-adjusts OHLC to maintain candle consistency.

### C. Risk-Appetite-Weighted Tournament Scoring
*   **Problem**: High-churn strategies look good on paper but lose to transaction costs in reality. Additionally, a user targeting 5% should not get the same strategy as a user targeting 50%.
*   **Fee Adjustment**: `adjustedAlpha = alpha − (totalTrades × 0.4%)`
*   **Risk-Appetite Weighting**: The user's target return determines the tradeoff between raw alpha (growth) and Sharpe ratio (risk-adjusted stability):

| User Target | Profile | Alpha Weight | Sharpe Weight | Effect |
| :--- | :--- | :--- | :--- | :--- |
| ≤ 15% | Conservative | 0.3 | 0.7 | Favors Safe Haven, Mean Reversion |
| 15–30% | Balanced | 0.5 | 0.5 | Equal weight |
| > 30% | Aggressive | 0.8 | 0.2 | Favors Momentum, ML |

*   **Final Score**: `(adjustedAlpha × αWeight) + (clampedSharpe × σWeight) + hitTargetBonus`
*   **Applied**: In the Walk-Forward Tournament's final scoring (Step D).

### D. Comprehensive Regression Suite
*   **Concept**: Trust, but Verify.
*   **Coverage**: **168+ Unit Tests** covering every critical domain component (`RiskEngine`, `Backtester`, `SlippageModel`, `DataIntegrityRegressionTest`, etc.).
*   **Key Validations**:
    *   **Equity Capture**: Validates correct return calculations for specific time windows.
    *   **Benchmark Alignment**: Ensures market data and benchmark indices are date-aligned perfectly.
    *   **Safety Checks**: Verifies that Stop-Losses, Sector Caps, and Penny Stock filters behave exactly as documented.
    *   **Data Integrity**: Verifies `distinctBy { date }` deduplication removes duplicate candles and preserves sort order.
*   **Execution**: Automated suite runs on every build (`./gradlew testDebugUnitTest`).

---

## 🚀 4. Strategy Definitions

| Strategy | Type | Best Regime | Logic |
| :--- | :--- | :--- | :--- |
| **Momentum (SMA)** | Trend | Bull Run | Price > SMA 20, 50, 100, 200 (signal-weighted) |
| **Momentum (EMA)** | Trend | Fast Bull | Price > EMA (Reacts faster than SMA) |
| **RSI Mean Reversion** | Swing | Channeling | Buy RSI < 30, Sell RSI > 70 (depth-weighted) |
| **Bollinger Breakout** | Volatility | Explosive | Price pierces Upper Band (breakout-weighted) |
| **Bollinger Mean Rev** | Swing | Channeling | Price < Lower Band (Zero-Allocation Loop) |
| **VPT (Volume Price)** | Volume | Accumulation | Volume Trend confirms Price Trend |
| **MACD Crossover** | Cycle | Transitional | True EMA-9 signal line crossover (O(N) Optimized) |
| **Relative Volume** | Volume | Breakout | Volume spike (2x-5x avg) + uptrend confirmation |
| **52-Week Breakout** | Trend | Strong Bull | Price breaks 250-day high (needs 365-day data) |
| **Hybrid Models** | Multi-Factor | Mixed | Momentum + RSI < 65 Filter (Best of both) |
| **Safe Haven** | Smart Beta | Uncertain | Low Volatility Anomaly (inverse-vol weighted) |
| **Deep Neural Net** | AI | Non-Linear | Multi-Factor 64-feature LSTM (60 log returns + RSI, SMA Ratio, ATR%, RelVol) |

---

> *Updated: 22 Feb 2026 - v3.1: Multi-Factor ML (64-feature), IndianAPI.in Live Data Pipeline, Zero-Mock Fundamentals.*

---

## ⚡ 5. Android Performance & Stability

The simulation engine is computationally intensive. To prevent UI freezes (ANR) and battery drain on mobile devices, strict performance guidelines are enforced.

### A. Parallel Concurrency (Multi-Threaded)
*   **Update (Feb 2026)**: We now utilize **4 parallel threads** for the Strategy Tournament (`RunStrategyTournamentUseCase`).
*   **Enabler**: This was made possible by the "Zero-Allocation" refactor.
*   **Benefit**: Reduces 3-year simulation time from ~9 minutes to **< 2 minutes**.

### B. Zero-Allocation Architecture
*   **Core Principle**: **NEVER** allocate memory inside the simulation loop.
*   **Implementation**:
    *   **Cursors**: Instead of slicing lists (`history.takeLast(20)`), we pass a `cursor` (int index) and read directly from the source list.
    *   **Primitive Math**: All strategy indicators (SMA, RSI, Bollinger) and the **RegimeFilter** (SMA-200, volatility) are calculated using primitive `double` loops, avoiding `List<Double>` creation.
    *   **ML Buffers**: The TFLite model (`StockPriceForecaster`) uses a **dynamic ByteBuffer** that lazily adapts to the feature count (60 or 64), eliminating 99% of GC pressure.
*   **Impact**: Garbage Collection (GC) pauses are reduced to negligible levels (<5ms), allowing the CPU to run full throttle on 4 threads without memory thrashing.

### C. M.A.C.D. & Indicator Optimization
*   **Problem**: Naive `calculateMACD` logic recalculated EMA(12)/EMA(26) from scratch for every day, leading to **O(N²)** complexity.
*   **Solution**: **Incremental Calculation (O(N))**.
    *   `EMA_today = (Price * k) + (EMA_yesterday * (1-k))`

---

## 🔧 6. System Reliability & Data Integrity (Added: 2 Mar 2026)

This section documents critical fixes made to improve correctness and reliability.

### A. After-Hours Simulation Guard
*   **Problem**: WorkManager fired `DailySimulationWorker` at 2–3 AM IST, running the full heavy pipeline on stale overnight data, wasting battery and causing "frozen equity" log entries.
*   **Fix**: IST market-hours window guard added at the top of `DailySimulationWorker.doWork()`.
    *   **Active Window**: `08:30 – 16:30 IST` (wider than NSE hours to capture pre-market data availability).
    *   Outside this window, the worker exits immediately (`Result.success()`) — no tournament, no trades, no log noise.
*   **Benefit**: Prevents midnight strategy switching (two tournament runs in one day picking different winners), saves battery, and eliminates frozen equity log lines.

### B. Unique Simulation Log Architecture
*   **Problem**: `RunDailySimulationUseCase` ran the entire market pipeline (fundamentals, tournament, regime check) inside a `for (sim in simulations)` loop. With 2 active simulations, every global event was logged N times — one per simulation — making logs unreadable.
*   **Fix**: Global pipeline stages (market fetch, regime check, tournament, fundamentals) are **hoisted outside the sim loop** and broadcast to all active simulation logs via a new `SimulationLogManager.logToAll(simulationIds, message)` helper.
*   **Per-simulation section** (stop-losses, sector caps, rebalancing, equity update) still runs per-simulation using `log(sim.id, …)`.
*   **Benefit**: `🌅 Starting...`, `🏎️ Tournament...`, `🌙 Market Closed.` each appear **exactly once** per sim log. Tournament runs **once** per day (shared across all sims).

### C. Duplicate Stock Price Fix (Critical ML Impact)
*   **Problem**: `StockPriceEntity` used `@PrimaryKey(autoGenerate = true)` with no unique constraint on `(symbol, date)`. `OnConflictStrategy.REPLACE` deduplicates by PK only, so every remote fetch inserted **new rows** for the same candle — polluting the LSTM's 60-step input window with repeated prices.
*   **Fix**:
    *   Added composite `UNIQUE` index on `(symbol, date)` to `StockPriceEntity`.
    *   `StockRepositoryImpl.getStockHistory()` now applies `.distinctBy { it.date }.sortedBy { it.date }` as a defense-in-depth guard for any pre-migration data.
    *   **Migration 10 → 11**: Deduplicates existing rows (keeps highest `id` per `symbol, date`), recreates table with unique index.
*   **Benefit**: `OnConflictStrategy.REPLACE` now correctly replaces existing candles. LSTM sees clean, non-repeated price history.

### D. AnalysisWorker `targetReturn` Decimal Fix
*   **Problem**: `AnalysisWorker` passed `simulation.targetReturnPercentage` (e.g. `20.0`) **raw** to the tournament. The bucketing threshold `targetReturn > 0.30` evaluated `20.0 > 0.30 = true`, so **every new simulation was bucketed as Aggressive** regardless of user settings. The `hitTargetBonus` never fired because `returnPct` is in percent but the threshold was `>= 20.0`.
*   **Fix**: `targetReturn = simulation.targetReturnPercentage / 100.0` — e.g. `20.0` → `0.20`.
*   **Benefit**: Initial strategy selection via `AnalysisWorker` now correctly respects the user's risk-appetite bucket.

### E. Database Schema History

| Version | Change |
|:--|:--|
| 4 → 5 | `transactions.reason` column added |
| 5 → 6 | `simulations.lastSwitchDate` column added |
| 6 → 7 | `stock_universe` table created |
| 7 → 8 | Live trading: `isLiveTradingEnabled`, `brokerOrderId` |
| 8 → 9 | `predictions` table created |
| 9 → 10 | `fundamentals_cache` table created |
| **10 → 11** | **Duplicate candle fix**: `stock_prices` rebuilt with `UNIQUE(symbol, date)` |
| **11 → 12** | **Honeymoon stop-loss**: `portfolio_items.purchaseDate` column added |
| 12 → 13 | `fundamentals_cache.promoterHolding` column added |
| **13 → 14** | **Schema correction**: `portfolio_items` rebuilt to remove DEFAULT on `purchaseDate` and create missing `index_portfolio_items_simulationId` |

> **Current DB Version: 14**

### F. Portfolio Items Schema Correction (Migration 13 → 14)
*   **Problem**: Migration 11→12 added `purchaseDate` via `ALTER TABLE ... DEFAULT 0`. Room's schema validator expects no default value (the entity uses `val purchaseDate: Long = 0L` without `@ColumnInfo(defaultValue)`) — causing a **schema hash mismatch crash** on upgraded devices. Additionally, `index_portfolio_items_simulationId` declared in `@Entity` was never created by any migration (only existed on fresh installs).
*   **Fix (Migration 13 → 14)**: Recreates `portfolio_items` table with Room's exact expected schema — no `DEFAULT` clause on `purchaseDate`, correct `FOREIGN KEY` on `simulationId`, and creates the missing index. All existing data is preserved.

---

> *Last Updated: 2 Mar 2026 - v3.3: After-Hours Guard, Unique Log Architecture, DB Duplicate Price Fix, AnalysisWorker Decimal Fix, Portfolio Schema Correction. DB at v14, 168+ Regression Tests.*

