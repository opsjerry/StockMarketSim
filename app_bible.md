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
*   **Data Source**: Yahoo Finance `quoteSummary` API (Zerodha paid API as fallback when available).
*   **Criteria**: ROE ≥ 12% **and** Debt/Equity ≤ 1.0.
*   **Applied**: On Monday rebalance days only. Stocks with missing data pass (benefit of the doubt).
*   **Purpose**: Prevents RSI and Bollinger Mean Reversion from buying "falling knives" — stocks that are cheap because fundamentals are deteriorating.
*   **Caching**: 1-hour in-memory cache to avoid redundant API calls.

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

### C. Fee-Adjusted Tournament Scoring
*   **Problem**: High-churn strategies look good on paper but lose to transaction costs in reality.
*   **Formula**: `adjustedAlpha = alpha − (totalTrades × 0.4%)`
*   **Effect**: A strategy with 100 trades gets penalized 40 percentage points. This correctly favors strategies that achieve alpha efficiently.
*   **Applied**: In the Walk-Forward Tournament's final scoring (Step D).

### D. Comprehensive Regression Suite
*   **Concept**: Trust, but Verify.
*   **Coverage**: **166 Unit Tests** covering every critical domain component (`RiskEngine`, `Backtester`, `SlippageModel`, etc.).
*   **Key Validations**:
    *   **Equity Capture**: Validates correct return calculations for specific time windows.
    *   **Benchmark Alignment**: Ensures market data and benchmark indices are date-aligned perfectly.
    *   **Safety Checks**: Verifies that Stop-Losses, Sector Caps, and Penny Stock filters behave exactly as documented.
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
| **Deep Neural Net** | AI | Non-Linear | Keras Multi-Factor pattern recognition (TensorFlow) |

---

> *Updated: 20 Feb 2026 - v2.6: Migrated Machine Learning Pipeline to Native Keras TFLite Engine.*

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
    *   **Primitive Math**: All strategy indicators (SMA, RSI, Bollinger) are calculated using primitive `double` loops, avoiding `List<Double>` creation.
    *   **ML Buffers**: The TFLite model (`StockPriceForecaster`) reuses a single `ByteBuffer` for all 100,000+ predictions, eliminating 99% of GC pressure.
*   **Impact**: Garbage Collection (GC) pauses are reduced to negligible levels (<5ms), allowing the CPU to run full throttle on 4 threads without memory thrashing.

### C. M.A.C.D. & Indicator Optimization
*   **Problem**: Naive `calculateMACD` logic recalculated EMA(12)/EMA(26) from scratch for every day, leading to **O(N²)** complexity.
*   **Solution**: **Incremental Calculation (O(N))**.
    *   `EMA_today = (Price * k) + (EMA_yesterday * (1-k))`

---

> *Last Updated: 19 Feb 2026 - v3.0: Zero-Allocation Engine & Parallel Processing.*

