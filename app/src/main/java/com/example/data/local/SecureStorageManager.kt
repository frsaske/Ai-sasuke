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
        private const val KEY_ENCRYPTED_TAVILY_KEY = "encrypted_tavily_api_key"
        private const val KEY_TAVILY_IV = "tavily_api_key_iv"
        const val DEFAULT_TAVILY_API_KEY = ""
        const val DEFAULT_GITHUB_TOKEN = ""
        private const val KEY_GITHUB_CLEARED = "github_token_cleared"
        private const val KEY_ENCRYPTED_GMAIL_TOKEN = "encrypted_gmail_token"
        private const val KEY_GMAIL_IV = "gmail_token_iv"
        private const val KEY_GMAIL_USER_EMAIL = "gmail_user_email"
        const val DEFAULT_GMAIL_USER_EMAIL = ""
        const val DEFAULT_GMAIL_OAUTH_CLIENT_ID = ""
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
            if (buildConfigKey.isNotBlank() &&
                !buildConfigKey.contains("MY_GEMINI_API_KEY") &&
                !buildConfigKey.contains("DEFAULT_GEMINI_API_KEY")
            ) {
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

    // --- GITHUB TOKEN STORAGE ---
    private val KEY_ENCRYPTED_GITHUB_TOKEN = "encrypted_github_token"
    private val KEY_GITHUB_IV = "github_token_iv"

    fun saveGitHubToken(rawToken: String) {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) {
            clearGitHubToken()
            return
        }
        prefs.edit().putBoolean(KEY_GITHUB_CLEARED, false).apply()
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            val encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)

            prefs.edit()
                .putString(KEY_ENCRYPTED_GITHUB_TOKEN, encryptedString)
                .putString(KEY_GITHUB_IV, ivString)
                .apply()
        } catch (e: Exception) {
            val obfuscated = Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_ENCRYPTED_GITHUB_TOKEN, obfuscated)
                .putString(KEY_GITHUB_IV, "FALLBACK")
                .apply()
        }
    }

    fun getGitHubToken(): String {
        if (prefs.getBoolean(KEY_GITHUB_CLEARED, false)) {
            return ""
        }

        val encryptedString = prefs.getString(KEY_ENCRYPTED_GITHUB_TOKEN, null)
        val ivString = prefs.getString(KEY_GITHUB_IV, null)

        if (encryptedString != null && ivString != null) {
            try {
                if (ivString == "FALLBACK") {
                    val decoded = Base64.decode(encryptedString, Base64.NO_WRAP)
                    val token = String(decoded, Charsets.UTF_8).trim()
                    if (token.isNotBlank()) return token
                } else {
                    val secretKey = getOrCreateSecretKey()
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    val iv = Base64.decode(ivString, Base64.NO_WRAP)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val encryptedBytes = Base64.decode(encryptedString, Base64.NO_WRAP)
                    val decrypted = cipher.doFinal(encryptedBytes)
                    val token = String(decrypted, Charsets.UTF_8).trim()
                    if (token.isNotBlank()) return token
                }
            } catch (e: Exception) {
                // Return default on failure
            }
        }

        // Check if injected via BuildConfig
        try {
            val buildConfigToken = BuildConfig.GITHUB_TOKEN
            if (buildConfigToken.isNotBlank() &&
                !buildConfigToken.contains("DEFAULT_") &&
                !buildConfigToken.contains("your_")
            ) {
                return buildConfigToken.trim()
            }
        } catch (_: Exception) {
            // Ignored
        }

        return DEFAULT_GITHUB_TOKEN
    }

    fun hasCustomGitHubToken(): Boolean {
        return !prefs.getBoolean(KEY_GITHUB_CLEARED, false) && prefs.contains(KEY_ENCRYPTED_GITHUB_TOKEN)
    }

    fun hasGitHubToken(): Boolean = getGitHubToken().isNotBlank()

    fun isGitHubPresetActive(): Boolean = hasGitHubToken() && !hasCustomGitHubToken()

    fun resetToDefaultGitHubToken() {
        prefs.edit()
            .putBoolean(KEY_GITHUB_CLEARED, false)
            .remove(KEY_ENCRYPTED_GITHUB_TOKEN)
            .remove(KEY_GITHUB_IV)
            .apply()
    }

    fun getMaskedGitHubToken(): String {
        val token = getGitHubToken()
        if (token.isBlank()) return ""
        return if (token.length > 8) {
            token.take(4) + "••••••••••••" + token.takeLast(4)
        } else {
            "••••••••••••"
        }
    }

    fun clearGitHubToken() {
        prefs.edit()
            .putBoolean(KEY_GITHUB_CLEARED, true)
            .remove(KEY_ENCRYPTED_GITHUB_TOKEN)
            .remove(KEY_GITHUB_IV)
            .apply()
    }

    // --- TAVILY API KEY STORAGE ---
    fun saveTavilyApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearTavilyApiKey()
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
                .putString(KEY_ENCRYPTED_TAVILY_KEY, encryptedString)
                .putString(KEY_TAVILY_IV, ivString)
                .apply()
        } catch (e: Exception) {
            val obfuscated = Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_ENCRYPTED_TAVILY_KEY, obfuscated)
                .putString(KEY_TAVILY_IV, "FALLBACK")
                .apply()
        }
    }

    fun getTavilyApiKey(): String {
        val encryptedString = prefs.getString(KEY_ENCRYPTED_TAVILY_KEY, null)
        val ivString = prefs.getString(KEY_TAVILY_IV, null)

        if (encryptedString != null && ivString != null) {
            try {
                if (ivString == "FALLBACK") {
                    val decoded = Base64.decode(encryptedString, Base64.NO_WRAP)
                    val k = String(decoded, Charsets.UTF_8).trim()
                    if (k.isNotBlank()) return k
                } else {
                    val secretKey = getOrCreateSecretKey()
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    val iv = Base64.decode(ivString, Base64.NO_WRAP)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val encryptedBytes = Base64.decode(encryptedString, Base64.NO_WRAP)
                    val decrypted = cipher.doFinal(encryptedBytes)
                    val k = String(decrypted, Charsets.UTF_8).trim()
                    if (k.isNotBlank()) return k
                }
            } catch (e: Exception) {
                // Return default on decryption error
            }
        }

        // Check if injected via BuildConfig
        try {
            val buildConfigKey = BuildConfig.TAVILY_API_KEY
            if (buildConfigKey.isNotBlank() &&
                !buildConfigKey.contains("DEFAULT_") &&
                !buildConfigKey.contains("your_")
            ) {
                return buildConfigKey.trim()
            }
        } catch (_: Exception) {
            // Ignored
        }

        return DEFAULT_TAVILY_API_KEY
    }

    fun hasCustomTavilyApiKey(): Boolean {
        return prefs.contains(KEY_ENCRYPTED_TAVILY_KEY)
    }

    fun getMaskedTavilyApiKey(): String {
        val key = getTavilyApiKey()
        if (key.isBlank()) return ""
        return if (key.length > 12) {
            key.take(8) + "••••••••" + key.takeLast(4)
        } else {
            "••••••••••••"
        }
    }

    fun clearTavilyApiKey() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_TAVILY_KEY)
            .remove(KEY_TAVILY_IV)
            .apply()
    }

    // --- GMAIL TOKEN STORAGE ---
    fun saveGmailToken(rawToken: String) {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) {
            clearGmailToken()
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
                .putString(KEY_ENCRYPTED_GMAIL_TOKEN, encryptedString)
                .putString(KEY_GMAIL_IV, ivString)
                .apply()
        } catch (e: Exception) {
            val obfuscated = Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_ENCRYPTED_GMAIL_TOKEN, obfuscated)
                .putString(KEY_GMAIL_IV, "FALLBACK")
                .apply()
        }
    }

    fun getGmailToken(): String {
        val encryptedString = prefs.getString(KEY_ENCRYPTED_GMAIL_TOKEN, null)
        val ivString = prefs.getString(KEY_GMAIL_IV, null)

        if (encryptedString != null && ivString != null) {
            try {
                if (ivString == "FALLBACK") {
                    val decoded = Base64.decode(encryptedString, Base64.NO_WRAP)
                    val t = String(decoded, Charsets.UTF_8).trim()
                    if (t.isNotBlank()) return t
                } else {
                    val secretKey = getOrCreateSecretKey()
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    val iv = Base64.decode(ivString, Base64.NO_WRAP)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val encryptedBytes = Base64.decode(encryptedString, Base64.NO_WRAP)
                    val decrypted = cipher.doFinal(encryptedBytes)
                    val t = String(decrypted, Charsets.UTF_8).trim()
                    if (t.isNotBlank()) return t
                }
            } catch (e: Exception) {
                // Return empty on decryption error
            }
        }
        return ""
    }

    fun hasGmailToken(): Boolean = getGmailToken().isNotBlank()

    fun getMaskedGmailToken(): String {
        val token = getGmailToken()
        if (token.isBlank()) return ""
        return if (token.length > 12) {
            token.take(6) + "••••••••" + token.takeLast(4)
        } else {
            "ya29.••••••••"
        }
    }

    fun clearGmailToken() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_GMAIL_TOKEN)
            .remove(KEY_GMAIL_IV)
            .apply()
    }

    // --- GOOGLE DRIVE TOKEN STORAGE ---
    fun saveDriveToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove("encrypted_drive_token").apply()
            return
        }
        val obfuscated = Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        prefs.edit().putString("encrypted_drive_token", obfuscated).apply()
    }

    fun getDriveToken(): String {
        val saved = prefs.getString("encrypted_drive_token", null)
        if (saved != null) {
            try {
                val decoded = Base64.decode(saved, Base64.NO_WRAP)
                val token = String(decoded, Charsets.UTF_8).trim()
                if (token.isNotBlank()) return token
            } catch (_: Exception) {}
        }
        // Fall back to Google OAuth token from Gmail if available
        return getGmailToken()
    }

    fun hasDriveToken(): Boolean = getDriveToken().isNotBlank()

    fun getGmailUserEmail(): String {
        return prefs.getString(KEY_GMAIL_USER_EMAIL, DEFAULT_GMAIL_USER_EMAIL) ?: DEFAULT_GMAIL_USER_EMAIL
    }

    fun saveGmailUserEmail(email: String) {
        prefs.edit().putString(KEY_GMAIL_USER_EMAIL, email.trim()).apply()
    }
}
