
import numpy as np
import tensorflow as tf
import yfinance as yf
import pandas as pd
import os

# --- CONFIGURATION ---
TICKER = "SPY"
HISTORY_YEARS = 5 # Increased for better training
SEQ_LENGTH = 60  # Days of lookback

# Calculate absolute path from script location (works from any directory)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)  # Go up from scripts/ to project root
MODEL_PATH = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "stock_model.tflite")

def fetch_data():
    print(f"Fetching data for {TICKER}...")
    try:
        # Use progress=False to keep logs clean, and handle multi-level index
        df = yf.download(TICKER, period=f"{HISTORY_YEARS}y", progress=False)
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

def create_sequences_log_returns(data, seq_length):
    # Log Returns: r_t = ln(P_t / P_{t-1})
    # Ensure data is 1D
    data = data.flatten()
    if len(data) < 2:
        return np.array([]), np.array([])
        
    log_returns = np.diff(np.log(data))
    
    xs, ys = [], []
    for i in range(len(log_returns) - seq_length):
        x_raw = log_returns[i:(i + seq_length)]
        y_raw = log_returns[i + seq_length]
        
        # Ensure x_raw is 1D
        xs.append(x_raw.flatten())
        ys.append(y_raw)
        
    return np.array(xs), np.array(ys)

def train_model():
    print(f"--- STARTING QUANT-GRADE TRAINING [Target: {MODEL_PATH}] ---")
    
    # 1. Prepare Data
    raw_data = fetch_data()
    
    if len(raw_data) < SEQ_LENGTH * 2:
        print("Insufficient data for training. Skipping.")
        return

    # Generate Sequences with Log Returns
    X, y = create_sequences_log_returns(raw_data, SEQ_LENGTH)
    
    if len(X) == 0:
        print("No sequences generated.")
        return

    # Reshape for LSTM [samples, time steps, features]
    X = X.reshape((X.shape[0], X.shape[1], 1))
    
    # Split Train/Val (80/20) - preserve time order!
    split_idx = int(len(X) * 0.8)
    X_train, y_train = X[:split_idx], y[:split_idx]
    X_val, y_val = X[split_idx:], y[split_idx:]
    
    print(f"Training Samples: {len(X_train)}, Validation Samples: {len(X_val)}")

    # 2. Build LSTM Model (Improved Architecture)
    model = tf.keras.Sequential([
        # Layer 1: Learn sequences
        tf.keras.layers.LSTM(64, return_sequences=True, input_shape=(SEQ_LENGTH, 1)),
        tf.keras.layers.Dropout(0.2), # Regularization
        
        # Layer 2: Learn complex patterns
        tf.keras.layers.LSTM(32, return_sequences=False),
        tf.keras.layers.Dropout(0.2),
        
        # Output Layer
        tf.keras.layers.Dense(1)
    ])
    
    # Huber Loss is robust to outliers (market crashes/spikes)
    model.compile(optimizer='adam', loss=tf.keras.losses.Huber())
    
    # 3. Train
    print("Training Model...")
    # Increased epochs because local normalization makes the task harder (but more robust)
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
        
    print(f"SUCCESS: Model saved to {MODEL_PATH}")

if __name__ == "__main__":
    train_model()
