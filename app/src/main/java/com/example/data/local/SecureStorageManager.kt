package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorageManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sasukex_secure_prefs"
        private const val KEY_ENCRYPTED_API_KEY = "encrypted_gemini_api_key"
        private const val KEY_IV = "gemini_api_key_iv"
        private const val KEYSTORE_ALIAS = "SasukeXGeminiKeyAlias"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey()
            return
        }
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            val encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)

            prefs.edit()
                .putString(KEY_ENCRYPTED_API_KEY, encryptedString)
                .putString(KEY_IV, ivString)
                .apply()
        } catch (e: Exception) {
            // Fallback to base64 obfuscation if hardware keystore fails on emulator/runtime
            val obfuscated = Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_ENCRYPTED_API_KEY, obfuscated)
                .putString(KEY_IV, "FALLBACK")
                .apply()
        }
    }

    fun getApiKey(): String {
        val encryptedString = prefs.getString(KEY_ENCRYPTED_API_KEY, null)
        val ivString = prefs.getString(KEY_IV, null)

        if (encryptedString != null && ivString != null) {
            try {
                if (ivString == "FALLBACK") {
                    val decoded = Base64.decode(encryptedString, Base64.NO_WRAP)
                    return String(decoded, Charsets.UTF_8)
                }
                val secretKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val iv = Base64.decode(ivString, Base64.NO_WRAP)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val encryptedBytes = Base64.decode(encryptedString, Base64.NO_WRAP)
                val decrypted = cipher.doFinal(encryptedBytes)
                val key = String(decrypted, Charsets.UTF_8)
                if (key.isNotBlank()) return key
            } catch (e: Exception) {
                // If decryption fails, continue to check BuildConfig fallback
            }
        }

        // Check if injected via BuildConfig
        try {
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey.isNotBlank() && !buildConfigKey.contains("MY_GEMINI_API_KEY")) {
                return buildConfigKey.trim()
            }
        } catch (e: Exception) {
            // Ignored
        }

        return ""
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun getMaskedApiKey(): String {
        val key = getApiKey()
        if (key.isBlank()) return ""
        return if (key.length > 8) {
            "••••••••••••••••" + key.takeLast(4)
        } else {
            "••••••••••••••••"
        }
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_API_KEY)
            .remove(KEY_IV)
            .apply()
    }
}
