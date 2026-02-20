# Stock Market Simulation: The Professional Engine

## Overview
This is not a game—it's a **Professional-Grade Algorithmic Trading Simulator**. 

Built with institutional-grade logic, this Android application puts a hedge fund's power in your pocket. It doesn't just "guess" stocks; it autonomously builds, tests, and executes sophisticated trading strategies in a risk-managed environment. It features a **Combative Intelligence Engine** that constantly tournaments 40+ strategies against each other to find the mathematically optimal approach for the current market regime.

---

## 🚀 Key Features

### 1. Autonomous Intelligence (Auto-Pilot)
The app runs a continuous background "Strategy Tournament" to ensure your portfolio never stagnates.
- **Continuous Optimization:** A background worker consistently backtests 40+ variations of Momentum, Mean Reversion, and Volatility strategies against the last 300 days of market data.
- **Auto-Switching:** If the engine finds a strategy that outperforms your current one by a significant margin (Alpha > +5%), it will **automatically switch** your simulation to the new winner and rebalance your portfolio immediately.
- **Performance Health Check:** Every 12 hours, the system diagnoses your strategy's health. If Alpha drops below -5%, it triggers an emergency search for a better alternative.

### 2. Dynamic Stock Universe (Self-Updating)
Static lists are obsolete. This app finds its own stocks.
- **Wikipedia Discovery Bot:** A specialized background worker updates the stock universe weekly by scraping the latest **NIFTY 50** and **NIFTY NEXT 50** compositions directly from Wikipedia.
- **Zero Maintenance:** New IPOs and index inclusions are automatically detected and added to your trading universe without app updates.

### 3. Fail-Safe Alpha Engine (New)
We prioritize capital preservation with a "Safety First" architecture.
- **Fail-Safe Data Integrity:**
    - **Zero-Price Guard:** Automatically rejects invalid or zero-price data to prevent execution errors.
    - **Stale Data Storm Protection:** If >50% of the market returns stale data (e.g., Exchange Outage), the simulation aborts immediately to prevent false signals.
- **Turbulence Handling:**
    - **Volatility Shakeout:** Detected "Choppy" stocks get a tightened Stop Loss (1.5x ATR) to enable quick exits during intraday crashes.
    - **Daily Exits:** While buying is strategic (Mondays), **selling is tactical**. The engine can exit underperforming positions on ANY day of the week.
- **Liquidity & Quality Filters:**
    - **Penny Stock Guard:** Rejects all stocks < ₹20.
    - **High Liquidity:** increased turnover requirement to **₹5 Crore** to ensure realistic execution.
- **Risk Management:**
    - **Hard Sector Limits:** Absolute cap of 35% per sector to prevent concentration risk.
    - **Hard Stop Loss:** 7% Maximum drop rule, regardless of volatility.
- **Regime Filter (Macro Awareness):** Before buying, the engine checks the **NIFTY 50 Benchmark**. If the market is in a Bear trend (below 200-day SMA), the system forces a **100% Cash** position to avoid systemic risk.
- **Smart Scheduling:**
  - **Standard Entry:** Trades are normally executed only on **Mondays** to capture weekly swing moves and avoid mid-week noise/churn (saving 80% on transaction costs).
  - **Fast Start:** New simulations or empty portfolios bypass this rule to start trading immediately.
  - **Emergency Rebalance:** Strategy switches trigger immediate rebalancing to align with the new model instantly.
- **Sector Capping:** Exposure is strictly limited to **30% per Sector** and **Max 3 Stocks per Sector** to prevent "False Diversification" (e.g., holding 5 correlated banks).
- **Liquidity Filter:** The engine rejects any stock with a daily turnover below **₹1 Crore**, ensuring realistic execution simulaton.

### 4. Advanced Simulation Capabilities
- **Flexible Parameters:** Create simulations with custom Initial Capital (₹1,000 - ₹10 Cr), Duration (1-60 Months), and Target Returns.
- **Detailed Analytics:** Track Portfolio Equity, Daily Returns, Alpha vs. Benchmark, and Win Rates.
- **Transparent Logging:** Every decision is logged. See exactly *why* a trade happened: "Stop Loss", "Auto-Pilot Switch", "Weekly Rebalance", etc.

---

## 🧠 Strategy Arsenal
The engine doesn't rely on one trick. It employs a diverse library of quantitative models:

| Strategy Family | Logic | Best Market Condition |
| :--- | :--- | :--- |
| **Momentum (SMA/EMA)** | Trend Following (Price > Moving Average) | Strong Bull Markets |
| **Mean Reversion (RSI)** | Buy Oversold (<30), Sell Overbought | Choppy / Sideways Markets |
| **Volatility Breakout** | Bollinger Band Piercing + Volume Surge | Explosive Moves / News Events |
| **Volume Price Trend** | Confirming price moves with Volume accumulation | Steady Accumulation Phases |
| **Safe Haven (Smart Beta)** | Low Volatility selection | Defensive / Uncertain Markets |
| **Hybrid Models** | Combining Momentum + RSI + Volume | Complex / Mixed Conditions |

---

## 🛠 Technical Architecture
Built for performance, scalability, and reliability.

- **Language:** Kotlin
- **UI:** Jetpack Compose (Modern, Reactive UI)
- **Architecture:** Clean Architecture + MVVM (Domain, Data, Presentation layers)
- **Dependency Injection:** Hilt
- **Persistence:** Room Database (Local SQL storage for robust history)
- **Background Tasks:** WorkManager (Reliable scheduling involved for Analysis and Discovery)
- **Networking:** Retrofit & Jsoup (API & Web Scraping)
- **Performance:**
  - **Parallel Execution:** Strategy tournaments run on **4 threads** concurrently.
  - **Zero-Allocation Engine:** Custom "No-GC" loops ensure 60fps UI even during heavy simulation.
  - **Large Heap:** Optimized for heavy data processing.
- **Testing:** 166-point Regression Suite covering all domain logic (Risk, Slippage, Strategy).

---

*Institutional Logic. Mobile Convenience.*
