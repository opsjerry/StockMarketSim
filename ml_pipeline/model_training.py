import pandas as pd
import xgboost as xgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, classification_report
import matplotlib.pyplot as plt
import os
import requests
import json

from feature_engineering import PROCESSED_DIR

MODEL_DIR = os.path.join("..", "app", "src", "main", "assets")

def setup_directories():
    os.makedirs(MODEL_DIR, exist_ok=True)

def train_xgboost_model():
    print("--- Starting Phase 2: Model Training ---")
    data_path = os.path.join(PROCESSED_DIR, "master_training_data.csv")
    
    if not os.path.exists(data_path):
        print("Error: Training data not found. Run feature_engineering.py first.")
        return

    df = pd.read_csv(data_path)
    print(f"Loaded dataset: {df.shape}")

    # Define features and target
    features = ['RSI_14', 'SMA_Ratio', 'ATR_Pct', 'Relative_Volume', 'PE_Ratio', 'Sentiment_Score']
    target = 'Target'

    # Filter out missing values (from the mock fundamental joins or NaN shifts)
    df = df.dropna(subset=features + [target])

    # --- Phase 8 MLOps Safeguard ---
    MIN_VALID_SAMPLES = 50000 
    if len(df) < MIN_VALID_SAMPLES:
        error_msg = f"🛑 CRITICAL ABORT: Insufficient data for training. Found {len(df)} valid rows. Minimum required is {MIN_VALID_SAMPLES}."
        print(error_msg)
        print("This indicates a severe Data API failure or rate-limiting issue.")
        print("Aborting training to prevent deploying an overfitted or broken model to the Android app.")
        
        # In a real cloud environment (GitHub Actions/AWS Lambda), this webhook sends 
        # a notification directly to your engineering team's Slack or Discord channel.
        # webhook_url = os.environ.get("SLACK_WEBHOOK_URL")
        webhook_url = "https://discord.com/api/webhooks/1474394870029222000/NzERDjzAZEIrR8dwjnfLEn6g81y6Q_-NtIlZQz5Xev202hXTD-MhRCQh9uNbrpVoJ6Rt"
        
        try:
            # Discord uses 'content', Slack uses 'text'
            payload = {"content": error_msg}
            requests.post(webhook_url, data=json.dumps(payload), headers={"Content-Type": "application/json"})
            print("Alert dispatched to engineering team via Webhook.")
        except Exception as e:
            print(f"Failed to dispatch alert: {e}")
            
        return

    X = df[features]
    y = df[target]

    # Chronological Split (Train on past, Test on future to prevent data leakage)
    # Assuming data is sorted by date ascending from ingestion
    split_idx = int(len(df) * 0.8)
    X_train, X_test = X.iloc[:split_idx], X.iloc[split_idx:]
    y_train, y_test = y.iloc[:split_idx], y.iloc[split_idx:]

    print(f"Training set: {X_train.shape}, Test set: {X_test.shape}")

    # Initialize XGBoost Classifier
    model = xgb.XGBClassifier(
        n_estimators=100,
        max_depth=4,
        learning_rate=0.1,
        objective='binary:logistic',
        eval_metric='auc',
        random_state=42
    )

    # Train the model
    print("Training XGBoost...")
    model.fit(
        X_train, y_train,
        eval_set=[(X_train, y_train), (X_test, y_test)],
        verbose=10
    )

    # Evaluate
    predictions = model.predict(X_test)
    probs = model.predict_proba(X_test)[:, 1]

    accuracy = accuracy_score(y_test, predictions)
    precision = precision_score(y_test, predictions)

    print("\n--- Model Evaluation ---")
    print(f"Accuracy:  {accuracy:.2%}")
    print(f"Precision: {precision:.2%} (Crucial for trading to avoid false positives)")
    print("\nClassification Report:")
    print(classification_report(y_test, predictions))

    # Feature Importance
    importance = model.feature_importances_
    for i, f in enumerate(features):
        print(f"Feature {f}: {importance[i]:.4f}")

    # Export Model
    model_export_path = os.path.join(MODEL_DIR, "multifactor_xgboost.json")
    model.save_model(model_export_path)
    print(f"\nModel exported to: {model_export_path}")
    print("Note: In Android, you can use the XGBoost Java library or convert this to ONNX/TFLite.")

if __name__ == "__main__":
    setup_directories()
    train_xgboost_model()
    print("--- Training Complete ---")
