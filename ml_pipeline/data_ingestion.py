import yfinance as yf
import pandas as pd
import requests
import time
import os
from datetime import datetime, timedelta

# Constants
DATA_DIR = "raw_data"
YFINANCE_DIR = os.path.join(DATA_DIR, "yfinance")
INDIAN_API_DIR = os.path.join(DATA_DIR, "indianapi")
YEARS_OF_DATA = 5
INDIANAPI_KEY = os.getenv("INDIANAPI_KEY", "sk-live-Oo1mYkKJG5aMxtdpkRmEG7bzfWuGtTPCWD2wCyn7")

def setup_directories():
    os.makedirs(YFINANCE_DIR, exist_ok=True)
    os.makedirs(INDIAN_API_DIR, exist_ok=True)

def get_nifty500_tickers():
    # For demonstration, returning a small subset of highly liquid NSE stocks
    # In production, this should read from the app's StockUniverse.kt or a full list
    return [
        "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "INFY.NS", "ICICIBANK.NS",
        "HINDUNILVR.NS", "ITC.NS", "SBIN.NS", "BHARTIARTL.NS", "L&T.NS"
    ]

def download_yahoo_data(tickers):
    """
    Downloads historical OHLCV data from Yahoo Finance.
    Saves each ticker to a CSV file to avoid re-downloading.
    """
    print(f"Downloading YFinance data for {len(tickers)} tickers...")
    end_date = datetime.now()
    start_date = end_date - timedelta(days=365 * YEARS_OF_DATA)
    
    for ticker in tickers:
        file_path = os.path.join(YFINANCE_DIR, f"{ticker.replace('.NS', '')}.csv")
        if os.path.exists(file_path):
            print(f"[{ticker}] Data already exists. Skipping...")
            continue
            
        try:
            print(f"[{ticker}] Fetching...")
            df = yf.download(ticker, start=start_date.strftime('%Y-%m-%d'), end=end_date.strftime('%Y-%m-%d'))
            
            if not df.empty:
                df.to_csv(file_path)
            else:
                print(f"[{ticker}] No data found.")
                
            # Sleep to respect rate limits
            time.sleep(0.5)
        except Exception as e:
            print(f"[{ticker}] Error: {e}")

def fetch_indianapi_fundamentals(ticker_symbol):
    """
    Stub for fetching fundamentals from IndianAPI.in
    Endpoint example: /v1/stock/{symbol}/fundamentals
    Requires Pro/Developer Plan for historical metrics or high limits.
    """
    # symbol = ticker_symbol.replace('.NS', '')
    # url = f"https://indianapi.in/api/v1/stock/{symbol}/fundamentals"
    # headers = {"Authorization": f"Bearer {INDIANAPI_KEY}"}
    # response = requests.get(url, headers=headers)
    # return response.json()
    
    # Mock return for development
    return {
        "symbol": ticker_symbol,
        "PE_Ratio": 25.4,
        "MarketCap_Cr": 1500000,
        "ROE": 15.2,
        "DebtToEquity": 0.4
    }

def fetch_indianapi_sentiment(ticker_symbol):
    """
    Stub for fetching Analyst Views or Sentiment from IndianAPI.in
    """
    # Mock return for development
    return {
        "symbol": ticker_symbol,
        "sentiment_score": 0.65, # Range -1 to 1
        "analyst_rating": "Buy"
    }

def build_mock_indianapi_dataset(tickers):
    """
    Creates a mock dataset of fundamentals and sentiment to simulate
    what the real API would return over time for feature engineering.
    """
    print(f"Building IndianAPI fundamental/sentiment dataset...")
    data = []
    
    # In reality, this would be a time-series of changing fundamentals/sentiment.
    # We are mocking static values mapping for simplicity in this script structure.
    for ticker in tickers:
        funds = fetch_indianapi_fundamentals(ticker)
        sent = fetch_indianapi_sentiment(ticker)
        data.append({
            "Ticker": ticker.replace('.NS', ''),
            "PE_Ratio": funds["PE_Ratio"],
            "ROE": funds["ROE"],
            "DebtToEquity": funds["DebtToEquity"],
            "Sentiment_Score": sent["sentiment_score"]
        })
        
    df = pd.DataFrame(data)
    df.to_csv(os.path.join(INDIAN_API_DIR, "fundamentals_sentiment.csv"), index=False)
    print("IndianAPI mock dataset saved.")

if __name__ == "__main__":
    setup_directories()
    tickers = get_nifty500_tickers()
    
    print("--- Starting Phase 1: Data Ingestion ---")
    download_yahoo_data(tickers)
    build_mock_indianapi_dataset(tickers)
    print("--- Data Ingestion Complete ---")
