package com.example.stockmarketsim.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = try {
        createEncryptedSharedPreferences()
    } catch (e: Exception) {
        // If keyset gets corrupted or deleted, clear the old preferences file and recreate it
        context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        createEncryptedSharedPreferences()
    }

    private fun createEncryptedSharedPreferences() = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // FALLBACK REMOVED: Using robust EncryptedSharedPreferences
    // private val sharedPreferences = context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)

    fun getZerodhaApiKey(): String? = sharedPreferences.getString("zerodha_api_key", null)
    fun setZerodhaApiKey(key: String) = sharedPreferences.edit().putString("zerodha_api_key", key).apply()

    fun getZerodhaApiSecret(): String? = sharedPreferences.getString("zerodha_api_secret", null)
    fun setZerodhaApiSecret(secret: String) = sharedPreferences.edit().putString("zerodha_api_secret", secret).apply()

    fun getZerodhaAccessToken(): String? = sharedPreferences.getString("zerodha_access_token", null)
    fun setZerodhaAccessToken(token: String) = sharedPreferences.edit().putString("zerodha_access_token", token).apply()
}
