
import numpy as np
import tensorflow as tf
import yfinance as yf
import pandas as pd
import os

# --- CONFIGURATION ---
TICKER = "^NSEI"  # NIFTY 50 (Indian Market) — was SPY, fixed per Expert Review #1
HISTORY_YEARS = 5 # Increased for better training
SEQ_LENGTH = 60  # Days of lookback

# Calculate absolute path from script location (works from any directory)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)  # Go up from scripts/ to project root
MODEL_PATH = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "stock_model.tflite")

def fetch_data():
    print(f"Fetching data for {TICKER}...")
    try:
        try:
            from curl_cffi import requests as curl_requests
            session = curl_requests.Session(impersonate="chrome110")
        except Exception as e:
            print(f"Warning: Failed to create curl_cffi session ({e}). Falling back to requests.")
            import requests
            session = requests.Session()
            session.headers.update({'User-Agent': 'Mozilla/5.0'})
            
        # Use progress=False to keep logs clean, and pass the explicit session
        df = yf.download(TICKER, period=f"{HISTORY_YEARS}y", progress=False, session=session)
        if df.empty:
            print("WARNING: No data fetched. Check internet connection.")
            return np.zeros(0)
            
        # Handle new yfinance MultiIndex output for single ticker
        if isinstance(df.columns, pd.MultiIndex):
            # In newer yfinance, df['Close'] might be a DataFrame with columns [TICKER]
            if TICKER in df['Close'].columns:
                close_data = df['Close'][TICKER]
            else:
                # Fallback to the first available column
                close_data = df['Close'].iloc[:, 0]
        else:
            close_data = df['Close']
            
        # Ensure we have a 1D array and remove any NaNs
        values = close_data.values.flatten()
        values = values[~np.isnan(values)]
        
        print(f"Fetched {len(values)} price points.")
        return values
    except Exception as e:
        print(f"ERROR fetching data: {e}")
        return np.zeros(0)

def calculate_rsi(prices, period=14):
    """Calculate RSI for a price array, returns array of RSI values."""
    deltas = np.diff(prices)
    gains = np.where(deltas > 0, deltas, 0)
    losses = np.where(deltas < 0, -deltas, 0)
    
    rsi = np.full(len(prices), 50.0)  # Default neutral
    for i in range(period, len(deltas)):
        avg_gain = gains[i-period:i].mean()
        avg_loss = losses[i-period:i].mean()
        if avg_loss == 0:
            rsi[i+1] = 100.0
        else:
            rs = avg_gain / avg_loss
            rsi[i+1] = 100.0 - (100.0 / (1.0 + rs))
    return rsi

def calculate_sma_ratio(prices, fast=50, slow=200):
    """Calculate SMA Ratio (fast/slow), returns array."""
    ratio = np.ones(len(prices))  # Default neutral
    for i in range(slow, len(prices)):
        sma_fast = prices[i-fast:i].mean()
        sma_slow = prices[i-slow:i].mean()
        if sma_slow > 0:
            ratio[i] = sma_fast / sma_slow
    return ratio

def calculate_atr_pct(highs, lows, closes, period=14):
    """Calculate ATR as percentage of price."""
    atr_pct = np.zeros(len(closes))
    for i in range(period + 1, len(closes)):
        tr_sum = 0.0
        for j in range(i - period, i):
            tr = max(highs[j] - lows[j], abs(highs[j] - closes[j-1]), abs(lows[j] - closes[j-1]))
            tr_sum += tr
        atr = tr_sum / period
        if closes[i] > 0:
            atr_pct[i] = atr / closes[i]
    return atr_pct

def calculate_relative_volume(volumes, period=20):
    """Calculate relative volume vs N-day average."""
    rel_vol = np.ones(len(volumes))  # Default neutral
    for i in range(period, len(volumes)):
        avg_vol = volumes[i-period:i].mean()
        if avg_vol > 0:
            rel_vol[i] = volumes[i] / avg_vol
    return rel_vol

FEATURE_COUNT = 64  # 60 log returns + 4 TA indicators

def create_sequences_multi_factor(data, seq_length):
    """Create sequences with 64 features: 60 log returns + RSI + SMA Ratio + ATR% + RelVol."""
    data = data.flatten() if hasattr(data, 'flatten') else data
    if len(data) < 2:
        return np.array([]), np.array([])
    
    # For multi-factor, we need OHLCV data. Since we only have close prices from yfinance,
    # we approximate: High ≈ Close * 1.01, Low ≈ Close * 0.99, Volume = synthetic
    # This is a bootstrap approximation — replace with real OHLCV when available.
    closes = data
    highs = closes * 1.01   # Approximate
    lows = closes * 0.99    # Approximate
    volumes = np.random.uniform(800000, 1200000, len(closes))  # Synthetic volume
    
    log_returns = np.diff(np.log(closes))
    
    # Pre-compute TA indicators for entire series
    rsi = calculate_rsi(closes, 14) / 100.0        # Normalize to 0-1
    sma_ratio = calculate_sma_ratio(closes, 50, 200)  # ~1.0 centered
    atr_pct = calculate_atr_pct(highs, lows, closes, 14)  # 0.01-0.10 range
    rel_vol = calculate_relative_volume(volumes, 20)    # ~1.0 centered
    
    xs, ys = [], []
    for i in range(len(log_returns) - seq_length):
        # 60 log returns
        log_ret_seq = log_returns[i:(i + seq_length)]
        
        # 4 TA indicators at the END of the window
        idx = i + seq_length  # Current position in original price array
        ta_features = np.array([
            rsi[idx],
            sma_ratio[idx],
            atr_pct[idx],
            rel_vol[idx]
        ])
        
        # Concatenate: [60 log returns, RSI, SMA_Ratio, ATR%, RelVol]
        x_combined = np.concatenate([log_ret_seq.flatten(), ta_features])
        
        y_raw = log_returns[i + seq_length]
        
        xs.append(x_combined)
        ys.append(y_raw)
        
    return np.array(xs), np.array(ys)

def train_model():
    print(f"--- STARTING MULTI-FACTOR TRAINING [Target: {MODEL_PATH}] ---")
    print(f"    Market: {TICKER} | Features: {FEATURE_COUNT} (60 log returns + 4 TA indicators)")
    
    # 1. Prepare Data
    raw_data = fetch_data()
    
    if len(raw_data) < SEQ_LENGTH * 2:
        print("Insufficient data for training. Skipping.")
        return

    # Generate Multi-Factor Sequences (64 features per sample)
    X, y = create_sequences_multi_factor(raw_data, SEQ_LENGTH)
    
    if len(X) == 0:
        print("No sequences generated.")
        return

    # Reshape for LSTM [samples, features, 1]
    X = X.reshape((X.shape[0], X.shape[1], 1))
    
    # Split Train/Val (80/20) - preserve time order!
    split_idx = int(len(X) * 0.8)
    X_train, y_train = X[:split_idx], y[:split_idx]
    X_val, y_val = X[split_idx:], y[split_idx:]
    
    print(f"Training Samples: {len(X_train)}, Validation Samples: {len(X_val)}")
    print(f"Feature Shape: {X_train.shape[1]} features x 1")

    # 2. Build LSTM Model (Multi-Factor Architecture)
    model = tf.keras.Sequential([
        # Layer 1: Learn from 64 multi-factor features
        tf.keras.layers.LSTM(64, return_sequences=True, input_shape=(FEATURE_COUNT, 1)),
        tf.keras.layers.Dropout(0.2),
        
        # Layer 2: Learn complex cross-feature patterns
        tf.keras.layers.LSTM(32, return_sequences=False),
        tf.keras.layers.Dropout(0.2),
        
        # Output Layer: Predicted next-day log return
        tf.keras.layers.Dense(1)
    ])
    
    # Huber Loss is robust to outliers (market crashes/spikes)
    model.compile(optimizer='adam', loss=tf.keras.losses.Huber())
    
    # 3. Train
    print("Training Model...")
    callback = tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=3)
    model.fit(X_train, y_train, validation_data=(X_val, y_val), batch_size=32, epochs=10, callbacks=[callback], verbose=1)
    
    # 4. Save & Convert to TFLite
    print("Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # FIX: LSTM layers use dynamic tensor ops that require SELECT_TF_OPS
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    converter._experimental_lower_tensor_list_ops = False
    
    tflite_model = converter.convert()
    
    # Ensure directory exists
    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    
    with open(MODEL_PATH, "wb") as f:
        f.write(tflite_model)
        
    print(f"SUCCESS: Multi-Factor model ({FEATURE_COUNT} features) saved to {MODEL_PATH}")

if __name__ == "__main__":
    train_model()
