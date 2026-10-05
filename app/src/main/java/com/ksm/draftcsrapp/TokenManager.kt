package com.ksm.draftcsrapp

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        sharedPreferences.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? = sharedPreferences.getString("jwt_token", null)

    fun saveRefreshToken(token: String) {
        sharedPreferences.edit().putString("refresh_token", token).apply()
    }

    fun getRefreshToken(): String? = sharedPreferences.getString("refresh_token", null)

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    // True bila access token ada dan belum kedaluwarsa (cek klaim "exp" di JWT)
    fun hasValidToken(): Boolean {
        val token = getToken() ?: return false
        return try {
            val payload = token.split(".")[1]
            val json = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            )
            val exp = JSONObject(json).getLong("exp")
            exp > System.currentTimeMillis() / 1000
        } catch (e: Exception) {
            false
        }
    }

    // Nama user dari klaim "unique_name" di JWT
    fun getUsername(): String? {
        val token = getToken() ?: return null
        return try {
            val payload = token.split(".")[1]
            val json = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            )
            JSONObject(json).optString("unique_name").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
