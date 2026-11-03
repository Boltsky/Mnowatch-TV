package com.example.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("munowatch_secure_session", Context.MODE_PRIVATE)
    private val keyAlias = "munowatch_session_key"
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"

    private val _sessionStateFlow = MutableStateFlow(getSessionState())
    val sessionStateFlow: StateFlow<String> = _sessionStateFlow.asStateFlow()

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        try {
            val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // Fallback gracefully if hardware keystore error
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            keyStore.getKey(keyAlias, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    fun saveSessionId(sessionId: String) {
        if (sessionId.isBlank()) return
        try {
            val secretKey = getSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(transformation)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(sessionId.toByteArray(Charsets.UTF_8))

                val combined = ByteArray(iv.size + encryptedBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

                val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
                prefs.edit()
                    .putString(PREF_ENCRYPTED_SESSION, encoded)
                    .putString(PREF_SESSION_STATUS, STATUS_AUTHENTICATED)
                    .putLong(PREF_SESSION_TIMESTAMP, System.currentTimeMillis())
                    .apply()
            } else {
                // Obfuscated fallback if Keystore is completely unavailable on emulator/device
                val fallbackEncoded = Base64.encodeToString(sessionId.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                prefs.edit()
                    .putString(PREF_FALLBACK_SESSION, fallbackEncoded)
                    .putString(PREF_SESSION_STATUS, STATUS_AUTHENTICATED)
                    .putLong(PREF_SESSION_TIMESTAMP, System.currentTimeMillis())
                    .apply()
            }
            _sessionStateFlow.value = STATUS_AUTHENTICATED
        } catch (e: Exception) {
            // Never log sensitive exception details
        }
    }

    fun getSessionId(): String? {
        try {
            val status = prefs.getString(PREF_SESSION_STATUS, null)
            if (status == STATUS_NOT_AUTHENTICATED) {
                return null
            }

            val encryptedBase64 = prefs.getString(PREF_ENCRYPTED_SESSION, null)
            if (encryptedBase64 != null) {
                val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val secretKey = getSecretKey() ?: return null
                val gcmIvLength = 12 // GCM standard IV size
                if (combined.size < gcmIvLength) return null

                val iv = ByteArray(gcmIvLength)
                System.arraycopy(combined, 0, iv, 0, gcmIvLength)
                val cipherBytes = ByteArray(combined.size - gcmIvLength)
                System.arraycopy(combined, gcmIvLength, cipherBytes, 0, cipherBytes.size)

                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decrypted = cipher.doFinal(cipherBytes)
                val stored = String(decrypted, Charsets.UTF_8)
                if (stored.isNotBlank() && stored != "f32a41dacb33fb4175640b14530f03b0") {
                    return stored
                }
            }

            val fallbackEncoded = prefs.getString(PREF_FALLBACK_SESSION, null)
            if (fallbackEncoded != null) {
                val stored = String(Base64.decode(fallbackEncoded, Base64.NO_WRAP), Charsets.UTF_8)
                if (stored.isNotBlank() && stored != "f32a41dacb33fb4175640b14530f03b0") {
                    return stored
                }
            }
        } catch (e: Exception) {
            // Redact in error states
        }
        return DEFAULT_SESSION_ID.takeIf { it.isNotBlank() }
    }

    fun hasStoredSession(): Boolean {
        return prefs.contains(PREF_ENCRYPTED_SESSION) || prefs.contains(PREF_FALLBACK_SESSION)
    }

    fun clearSession() {
        prefs.edit()
            .remove(PREF_ENCRYPTED_SESSION)
            .remove(PREF_FALLBACK_SESSION)
            .putString(PREF_SESSION_STATUS, STATUS_NOT_AUTHENTICATED)
            .apply()
        _sessionStateFlow.value = STATUS_NOT_AUTHENTICATED
    }

    fun markSessionExpired() {
        prefs.edit()
            .putString(PREF_SESSION_STATUS, STATUS_EXPIRED)
            .apply()
        _sessionStateFlow.value = STATUS_EXPIRED
    }

    fun getSessionState(): String {
        val status = prefs.getString(PREF_SESSION_STATUS, null)
        return if (status == STATUS_NOT_AUTHENTICATED) STATUS_NOT_AUTHENTICATED else "DIRECT_SERVER_MODE"
    }

    fun getMaskedSessionPreview(): String {
        val session = getSessionId()
        return if (!session.isNullOrBlank() && session.length > 8) {
            session.take(4) + "••••••••" + session.takeLast(4)
        } else {
            "Direct Server Mode (Active)"
        }
    }

    fun getDeviceId(): String {
        val existing = prefs.getString("pref_device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("pref_device_id", newId).apply()
        return newId
    }

    companion object {
        const val DEFAULT_SESSION_ID = "926cdd498cd8113008318bd9c6696ff5"
        const val DEFAULT_GA = "GA1.1.994953773.1791526221"
        const val DEFAULT_GA_SESSION = "GS2.1.s1793602702\$o4\$g1\$t1793602713\$j49\$l0\$h0"
        private const val PREF_ENCRYPTED_SESSION = "pref_enc_session_id"
        private const val PREF_FALLBACK_SESSION = "pref_fallback_session_id"
        private const val PREF_SESSION_STATUS = "pref_session_status"
        private const val PREF_SESSION_TIMESTAMP = "pref_session_timestamp"

        const val STATUS_AUTHENTICATED = "AUTHENTICATED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val STATUS_NOT_AUTHENTICATED = "NOT_AUTHENTICATED"
    }
}
