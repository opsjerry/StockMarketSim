import pandas as pd
import numpy as np
import os
import glob
from data_ingestion import YFINANCE_DIR, INDIAN_API_DIR, DATA_DIR

PROCESSED_DIR = os.path.join(DATA_DIR, "processed")

def setup_directories():
    os.makedirs(PROCESSED_DIR, exist_ok=True)

def compute_orthogonal_features(df):
    """
    Computes orthogonal (uncorrelated) features for the XGBoost model.
    Requires OHLCV data.
    """
    if df.empty or len(df) < 200:
        return pd.DataFrame() # Not enough data for 200-day SMA
        
    # Ensure Date is datetime
    if 'Date' in df.columns:
        df['Date'] = pd.to_datetime(df['Date'])
        df.set_index('Date', inplace=True)
    
    # 1. Momentum: RSI (14-day)
    delta = df['Close'].diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=14).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=14).mean()
    rs = gain / loss
    df['RSI_14'] = 100 - (100 / (1 + rs))
    
    # 2. Trend: SMA Ratio (50-day / 200-day)
    sma_50 = df['Close'].rolling(window=50).mean()
    sma_200 = df['Close'].rolling(window=200).mean()
    df['SMA_Ratio'] = sma_50 / sma_200
    
    # 3. Volatility: ATR Proxy (Average True Range Percentage)
    high_low = df['High'] - df['Low']
    high_close = np.abs(df['High'] - df['Close'].shift())
    low_close = np.abs(df['Low'] - df['Close'].shift())
    ranges = pd.concat([high_low, high_close, low_close], axis=1)
    true_range = np.max(ranges, axis=1)
    atr_14 = true_range.rolling(14).mean()
    df['ATR_Pct'] = atr_14 / df['Close']
    
    # 4. Volume: Relative Volume (Today / 20-day Average)
    df['Vol_SMA_20'] = df['Volume'].rolling(window=20).mean()
    df['Relative_Volume'] = df['Volume'] / df['Vol_SMA_20']
    
    # 5. Target Variable: 5-Day Forward Return
    # What we are trying to predict: Will it go up in the next 5 days?
    df['Forward_5D_Return'] = df['Close'].shift(-5) / df['Close'] - 1.0
    
    # Binary Classification Target: 1 if return > 0.5% (covering fees), else 0
    df['Target'] = (df['Forward_5D_Return'] > 0.005).astype(int)
    
    # Cleanup NaN rows from rolling windows and shifts
    df.dropna(inplace=True)
    
    # Keep only the features we want to feed the model
    features_to_keep = [
        'Open', 'High', 'Low', 'Close', 'Volume', 
        'RSI_14', 'SMA_Ratio', 'ATR_Pct', 'Relative_Volume', 
        'Target', 'Forward_5D_Return'
    ]
    
    return df[features_to_keep]

def merge_fundamentals(df, ticker, fundamentals_df):
    """
    Merges the static/mock fundamental data into the time-series dataframe.
    """
    fund_row = fundamentals_df[fundamentals_df['Ticker'] == ticker]
    if fund_row.empty:
        return df
        
    # In a real scenario, this would merge on Date.
    # Here we broadcast the mock static data across all rows.
    df['PE_Ratio'] = fund_row.iloc[0]['PE_Ratio']
    df['ROE'] = fund_row.iloc[0]['ROE']
    df['DebtToEquity'] = fund_row.iloc[0]['DebtToEquity']
    df['Sentiment_Score'] = fund_row.iloc[0]['Sentiment_Score']
    
    return df


def process_all_tickers():
    print("--- Starting Phase 1: Feature Engineering ---")
    
    # Load fundamental data mock
    fund_path = os.path.join(INDIAN_API_DIR, "fundamentals_sentiment.csv")
    if os.path.exists(fund_path):
        fundamentals_df = pd.read_csv(fund_path)
    else:
        print("Warning: No fundamentals data found. Did you run data_ingestion.py?")
        fundamentals_df = pd.DataFrame()

    csv_files = glob.glob(os.path.join(YFINANCE_DIR, "*.csv"))
    print(f"Found {len(csv_files)} historical price files.")
    
    all_data = []

    for file in csv_files:
        ticker = os.path.basename(file).replace('.csv', '')
        print(f"Processing {ticker}...")
        
        # In yf.download format, multi-index columns might exist. 
        # Skip rows 1 and 2 if downloading multiple tickers, but since we downloaded 1 by 1:
        # Read the CSV. The first row is usually 'Price', second is 'Ticker'.
        # We need to handle yfinance's multi-level column formatting if it occurred.
        try:
            # Typical yfinance 0.2.40 single-ticker download format: Price, Close, High, Low, Open, Volume
            df = pd.read_csv(file, header=[0,1], index_col=0)
            
            # Flatten multi-index columns if present
            if isinstance(df.columns, pd.MultiIndex):
                # Keep only the feature name (Price), drop the Ticker name level
                df.columns = df.columns.get_level_values(0)
                        
            # Ensure proper index
            df.index = pd.to_datetime(df.index)
            
            features_df = compute_orthogonal_features(df)
            
            if not features_df.empty:
                features_df = merge_fundamentals(features_df, ticker, fundamentals_df)
                
                # Add Ticker column to differentiate when concatenating
                features_df['Ticker'] = ticker
                features_df.reset_index(inplace=True)
                
                all_data.append(features_df)
        except Exception as e:
            print(f"Error processing {ticker}: {e}")

    if all_data:
        master_df = pd.concat(all_data, ignore_index=True)
        out_path = os.path.join(PROCESSED_DIR, "master_training_data.csv")
        master_df.to_csv(out_path, index=False)
        print(f"Successfully generated master dataset: {out_path}")
        print(f"Total shape: {master_df.shape}")
    else:
        print("No data processed.")

if __name__ == "__main__":
    setup_directories()
    process_all_tickers()
    print("--- Feature Engineering Complete ---")
