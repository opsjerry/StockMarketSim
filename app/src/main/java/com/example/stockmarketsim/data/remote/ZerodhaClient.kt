package com.example.stockmarketsim.data.remote

import com.example.stockmarketsim.data.local.EncryptedPreferences
import com.zerodhatech.kiteconnect.KiteConnect
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZerodhaClient @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences
) {
    private var kiteConnect: KiteConnect? = null

    @Synchronized
    fun getKiteConnect(): KiteConnect? {
        val apiKey = encryptedPreferences.getZerodhaApiKey()
        if (apiKey.isNullOrEmpty()) return null

        if (kiteConnect == null) {
            kiteConnect = KiteConnect(apiKey)
            val accessToken = encryptedPreferences.getZerodhaAccessToken()
            if (!accessToken.isNullOrEmpty()) {
                kiteConnect?.setAccessToken(accessToken)
                kiteConnect?.setPublicToken(accessToken)
            }
        }
        return kiteConnect
    }

    fun isSessionActive(): Boolean {
        // Simple check: Do we have an access token?
        // In a real app, we might ping the user profile.
        return !encryptedPreferences.getZerodhaAccessToken().isNullOrEmpty()
    }
}
