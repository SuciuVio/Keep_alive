package com.keepalive

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object AuthRepository {
    private const val PREFS_NAME = "keep_alive_auth"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_HASH = "pin_hash"
    private val secureRandom = SecureRandom()

    fun hasPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PIN_SALT) && prefs.contains(KEY_PIN_HASH)
    }

    fun isValidPin(pin: String): Boolean {
        return pin.length >= 4 && pin.all { it.isDigit() }
    }

    fun savePin(context: Context, pin: String) {
        require(isValidPin(pin)) { "PIN must contain at least 4 digits" }
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val hash = hashPin(salt, pin)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saltText = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashText = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expectedHash = Base64.decode(hashText, Base64.NO_WRAP)
        val actualHash = hashPin(salt, pin)
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    private fun hashPin(salt: ByteArray, pin: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }
}