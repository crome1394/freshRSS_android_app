package com.crome.freshrss.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Keystore-backed storage for secrets (API password).
 *
 * Uses stable [androidx.security:security-crypto:1.0.0] APIs
 * (AES-256-GCM values, AES-256-SIV keys).
 */
class SecureSecrets(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiPassword(): String =
        prefs.getString(KEY_API_PASSWORD, "").orEmpty()

    fun setApiPassword(value: String) {
        prefs.edit().putString(KEY_API_PASSWORD, value).apply()
    }

    fun clearApiPassword() {
        prefs.edit().remove(KEY_API_PASSWORD).apply()
    }

    companion object {
        private const val PREFS_FILE = "freshrss_secure_prefs"
        private const val KEY_API_PASSWORD = "api_password"
    }
}
