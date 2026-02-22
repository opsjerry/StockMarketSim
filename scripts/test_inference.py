import numpy as np
import tensorflow as tf

def test_inference():
    # Build LSTM Model (same as train_stock_model.py)
    model = tf.keras.Sequential([
        tf.keras.layers.LSTM(64, return_sequences=True, input_shape=(60, 1)),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.LSTM(32, return_sequences=False),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(1)
    ])
    
    # We just want to see untaught model behavior (random weights) 
    # to check the scale of predictions
    model.compile(optimizer='adam', loss=tf.keras.losses.Huber())
    
    test_cases = [
        ("All Zeros", np.zeros((1, 60, 1), dtype=np.float32)),
        ("All Ones", np.ones((1, 60, 1), dtype=np.float32)),
        ("Small Values (0.01)", np.full((1, 60, 1), 0.01, dtype=np.float32)),
        ("Negative Values (-0.01)", np.full((1, 60, 1), -0.01, dtype=np.float32)),
        ("Random Noise (-0.05 to 0.05)", np.random.uniform(-0.05, 0.05, (1, 60, 1)).astype(np.float32))
    ]
    
    for name, data in test_cases:
        output = model.predict(data, verbose=0)
        # Note: log returns are typically very small, e.g., 0.005 for 0.5%
        print(f"[{name}] Predicted Log Return: {output[0][0]:.6f} ({output[0][0] * 100:.4f}%)")

if __name__ == "__main__":
    test_inference()
