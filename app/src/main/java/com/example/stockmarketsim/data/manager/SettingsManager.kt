package com.example.stockmarketsim.data.manager

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("stock_sim_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALPHA_VANTAGE_API = "alpha_vantage_api_key"
        private const val KEY_INDIAN_API = "indian_api_key"
        private const val KEY_MOCK_DATA = "force_mock_data"
    }

    var alphaVantageApiKey: String
        get() = prefs.getString(KEY_ALPHA_VANTAGE_API, "SZ9YA1WTWFHKW9EF") ?: "SZ9YA1WTWFHKW9EF"
        set(value) = prefs.edit().putString(KEY_ALPHA_VANTAGE_API, value).apply()

    var indianApiKey: String
        get() = prefs.getString(KEY_INDIAN_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_INDIAN_API, value).apply()

    var isMockDataEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOCK_DATA, false)
        set(value) = prefs.edit().putBoolean(KEY_MOCK_DATA, value).apply()
}
