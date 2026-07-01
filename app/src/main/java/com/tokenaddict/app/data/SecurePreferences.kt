package com.tokenaddict.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Fail-closed encrypted storage abstraction over [SharedPreferences].
 *
 * All operations throw [SecureStorageException] on failure — no plaintext fallback.
 * Uses [EncryptedSharedPreferences] with AES256_GCM for values and AES256_SIV for keys.
 *
 * @param prefs the underlying [SharedPreferences] instance (encrypted in production)
 */
class SecurePreferences private constructor(
    private val prefs: SharedPreferences
) {

    companion object {
        internal const val PLAINTEXT_PREFS_NAME = "kimi_tokens"

        fun create(context: Context, prefsName: String): SecurePreferences {
            val prefs = try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    prefsName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                throw SecureStorageException(
                    "Failed to initialize secure storage for '$prefsName'", e
                )
            }

            return SecurePreferences(prefs)
        }

        /**
         * Test seam: creates [SecurePreferences] with a directly injected [SharedPreferences].
         * Used in unit tests to avoid real Android Keystore.
         */
        internal fun create(prefs: SharedPreferences): SecurePreferences {
            return SecurePreferences(prefs)
        }

        /**
         * Test seam: creates [SecurePreferences] using a custom factory.
         *
         * @throws SecureStorageException if the factory throws
         */
        internal fun create(
            context: Context,
            prefsName: String,
            prefsFactory: (Context, String) -> SharedPreferences
        ): SecurePreferences {
            val prefs = try {
                prefsFactory(context, prefsName)
            } catch (e: Exception) {
                throw SecureStorageException(
                    "Failed to initialize secure storage for '$prefsName'", e
                )
            }
            return SecurePreferences(prefs)
        }
    }

    fun putString(key: String, value: String) {
        try {
            prefs.edit().putString(key, value).apply()
        } catch (e: Exception) {
            throw SecureStorageException("Failed to write key '$key'", e)
        }
    }

    fun getString(key: String, default: String? = null): String? {
        return try {
            prefs.getString(key, default)
        } catch (e: Exception) {
            throw SecureStorageException("Failed to read key '$key'", e)
        }
    }

    fun remove(key: String) {
        try {
            prefs.edit().remove(key).apply()
        } catch (e: Exception) {
            throw SecureStorageException("Failed to remove key '$key'", e)
        }
    }

    fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            throw SecureStorageException("Failed to clear secure storage", e)
        }
    }
}

/**
 * Runtime exception thrown when secure storage operations fail.
 *
 * This is a fail-closed exception: any failure during initialization, read, or write
 * surfaces as this exception. Callers should catch this and handle appropriately
 * (e.g., prompt re-authentication, log the error).
 *
 * Marked `open` so callers can catch it specifically or create subclasses if needed.
 */
open class SecureStorageException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
