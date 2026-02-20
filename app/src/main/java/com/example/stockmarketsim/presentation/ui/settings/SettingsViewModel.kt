package com.example.stockmarketsim.presentation.ui.settings

import androidx.lifecycle.ViewModel
import com.example.stockmarketsim.data.manager.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val stockRepository: com.example.stockmarketsim.domain.repository.StockRepository,
    private val encryptedPreferences: com.example.stockmarketsim.data.local.EncryptedPreferences
) : ViewModel() {

    private val _apiKey = MutableStateFlow(settingsManager.alphaVantageApiKey)
    val apiKey: StateFlow<String> = _apiKey

    private val _indianApiKey = MutableStateFlow(settingsManager.indianApiKey)
    val indianApiKey: StateFlow<String> = _indianApiKey

    private val _isMockDataEnabled = MutableStateFlow(settingsManager.isMockDataEnabled)
    val isMockDataEnabledBool: StateFlow<Boolean> = _isMockDataEnabled

    // Dynamic Universe
    val activeUniverse = stockRepository.getActiveUniverse()
    
    private val _isSyncingUniverse = MutableStateFlow(false)
    val isSyncingUniverse: StateFlow<Boolean> = _isSyncingUniverse

    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey
    }

    fun updateIndianApiKey(newKey: String) {
        _indianApiKey.value = newKey
    }

    fun updateMockData(enabled: Boolean) {
        _isMockDataEnabled.value = enabled
    }

    fun saveSettings() {
        settingsManager.alphaVantageApiKey = _apiKey.value
        settingsManager.indianApiKey = _indianApiKey.value
        settingsManager.isMockDataEnabled = _isMockDataEnabled.value
    }

    // --- Zerodha Configuration ---
    
    private val _zerodhaApiKey = MutableStateFlow(encryptedPreferences.getZerodhaApiKey() ?: "")
    val zerodhaApiKey: StateFlow<String> = _zerodhaApiKey

    private val _zerodhaAccessToken = MutableStateFlow(encryptedPreferences.getZerodhaAccessToken() ?: "")
    val zerodhaAccessToken: StateFlow<String> = _zerodhaAccessToken

    fun updateZerodhaApiKey(key: String) {
        _zerodhaApiKey.value = key
    }

    fun updateZerodhaAccessToken(token: String) {
        _zerodhaAccessToken.value = token
    }

    fun saveZerodhaSettings() {
        encryptedPreferences.setZerodhaApiKey(_zerodhaApiKey.value)
        encryptedPreferences.setZerodhaAccessToken(_zerodhaAccessToken.value)
    }
    
    fun addToUniverse(symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            stockRepository.addStockToUniverse(symbol)
        }
    }
    
    fun removeFromUniverse(symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            stockRepository.removeStockFromUniverse(symbol)
        }
    }
    
    fun syncUniverse(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingUniverse.value = true
            val count = stockRepository.syncUniverseFromDiscovery { log ->
                 // Ideally stream logs, but simple result is fine
            }
            _isSyncingUniverse.value = false
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    onResult("Synced $count stocks from Wikipedia.")
                } else {
                    onResult("Sync failed or no stocks found.")
                }
            }
        }
    }
}
