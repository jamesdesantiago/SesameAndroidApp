package com.gazzel.sesameapp.data.manager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.gazzel.sesameapp.domain.exception.AuthException
import com.gazzel.sesameapp.domain.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A singleton security manager responsible for handling encryption, decryption, and secure storage operations.
 * This class uses the Android Keystore system to securely store encryption keys and implements AES-GCM
 * encryption for sensitive data.
 *
 * Key features:
 * - AES-GCM encryption with 256-bit keys
 * - Secure key storage in Android Keystore
 * - Encrypted SharedPreferences storage
 * - Exception handling with logging
 *
 * Security considerations:
 * - Uses hardware-backed keystore when available
 * - Implements authenticated encryption (AES-GCM)
 * - Secure key generation and storage
 * - Protection against key extraction
 *
 * @property context The application context
 * @property logger Logger instance for error tracking
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "SesameAppKey"
    private val keySize = 256

    init {
        if (!keyStore.containsAlias(keyAlias)) {
            createKey()
        }
    }

    /**
     * Creates a new encryption key in the Android Keystore.
     * The key is configured with AES-GCM encryption and appropriate security parameters.
     *
     * @throws SecurityException if key creation fails
     */
    private fun createKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(keySize)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    /**
     * Encrypts the provided data using AES-GCM encryption.
     *
     * @param data The string data to encrypt
     * @return Base64 encoded string containing the IV and encrypted data
     * @throws AuthException if encryption fails
     */
    fun encrypt(data: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = keyStore.getKey(keyAlias, null) as SecretKey
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(data.toByteArray())
            val iv = cipher.iv
            Base64.encodeToString(iv + encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            logger.error(AuthException("Failed to encrypt data", e))
            throw e
        }
    }

    /**
     * Decrypts the provided encrypted data using AES-GCM decryption.
     *
     * @param encryptedData Base64 encoded string containing the IV and encrypted data
     * @return The decrypted string
     * @throws AuthException if decryption fails
     */
    fun decrypt(encryptedData: String): String {
        return try {
            val decoded = Base64.decode(encryptedData, Base64.DEFAULT)
            val iv = decoded.copyOfRange(0, 12)
            val encrypted = decoded.copyOfRange(12, decoded.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = keyStore.getKey(keyAlias, null) as SecretKey
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(encrypted))
        } catch (e: Exception) {
            logger.error(AuthException("Failed to decrypt data", e))
            throw e
        }
    }

    /**
     * Securely stores a key-value pair in encrypted SharedPreferences.
     *
     * @param key The key to store
     * @param value The value to store
     * @throws AuthException if storage fails
     */
    fun secureStore(key: String, value: String) {
        try {
            val encryptedValue = encrypt(value)
            context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(key, encryptedValue)
                .apply()
        } catch (e: Exception) {
            logger.error(AuthException("Failed to store secure data", e))
            throw e
        }
    }

    /**
     * Securely retrieves a value from encrypted SharedPreferences.
     *
     * @param key The key to retrieve
     * @return The decrypted value, or null if not found or decryption fails
     */
    fun secureRetrieve(key: String): String? {
        return try {
            val encryptedValue = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                .getString(key, null)
            encryptedValue?.let { decrypt(it) }
        } catch (e: Exception) {
            logger.error(AuthException("Failed to retrieve secure data", e))
            null
        }
    }
} 