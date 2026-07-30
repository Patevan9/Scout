package com.example.scoutface

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (API keys) using an AES-256-GCM key held inside the
 * Android Keystore -- hardware-backed where the device supports it, software-backed
 * otherwise; Android decides this, nothing here requires or assumes StrongBox. The
 * key itself never leaves the Keystore; only ciphertext + a per-encryption random
 * IV are ever persisted (by the caller, in ordinary SharedPreferences).
 *
 * Deliberately not androidx.security-crypto's EncryptedSharedPreferences/MasterKey --
 * both are deprecated (androidx.security-crypto 1.1.0-alpha07) over real reliability
 * problems (main-thread strict-mode violations, keyset-corruption crashes on some
 * OEM devices), not just API churn. This is a small, dependency-free alternative
 * using only platform APIs (android.security.keystore / java.security.KeyStore /
 * javax.crypto -- no new Gradle dependency).
 *
 * Stored format is versioned: "v1:<base64 iv>:<base64 ciphertext>".
 */
object ScoutSecureKeyStore {

    private const val TAG = "ScoutSecureKeyStore"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "scout_api_key_wrapper"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val FORMAT_VERSION = "v1"

    // Guards key creation so two concurrent callers (e.g. two screens reading a key
    // at nearly the same moment) can't both see "no key yet" and both call
    // generateKey() for the same alias.
    private val keyLock = Any()

    sealed class DecryptResult {
        data class Available(val value: String) : DecryptResult()

        // No Keystore key exists yet, or the stored ciphertext is malformed/
        // corrupted/foreign to this key -- either way, the caller must ask the
        // user to reconnect rather than guess, and must never surface the raw
        // stored value in this case.
        object Unavailable : DecryptResult()
    }

    sealed class EncryptResult {
        data class Available(val stored: String) : EncryptResult()

        // Keystore/crypto operation failed (e.g. the Keystore is unavailable,
        // key generation was rejected, or the cipher threw for any other
        // reason). Callers must not fall back to storing the plaintext value
        // in this case -- they should treat it as a save failure instead.
        object Unavailable : EncryptResult()
    }

    private fun getExistingSecretKey(): SecretKey? = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    // Only encrypt() may call this. decrypt() must only ever look up an existing
    // key (getExistingSecretKey()) and fail cleanly if none exists -- it must never
    // silently create a fresh key and attempt to decrypt old ciphertext with it,
    // since a mismatched key wouldn't decrypt correctly anyway and would just turn
    // a clean "unavailable" into a confusing crypto exception.
    private fun getOrCreateSecretKey(): SecretKey = synchronized(keyLock) {
        getExistingSecretKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    fun encrypt(plaintext: String): EncryptResult {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            }
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            EncryptResult.Available("$FORMAT_VERSION:$ivB64:$ctB64")
        } catch (e: Exception) {
            // Never log plaintext or key material here -- only that encryption
            // failed, so the caller can surface a save failure instead of
            // crashing or silently falling back to plaintext storage.
            Log.e(TAG, "encrypt() failed", e)
            EncryptResult.Unavailable
        }
    }

    fun decrypt(stored: String): DecryptResult {
        val parts = stored.split(":", limit = 3)
        if (parts.size != 3 || parts[0] != FORMAT_VERSION) return DecryptResult.Unavailable

        val key = getExistingSecretKey() ?: return DecryptResult.Unavailable

        return try {
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            DecryptResult.Available(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        } catch (_: Exception) {
            // Corrupted ciphertext, a foreign/incompatible IV, or any other crypto
            // failure -- never rethrow and never expose the raw stored value; the
            // caller re-prompts for the key instead.
            DecryptResult.Unavailable
        }
    }

    // True if a stored SharedPreferences value is already in the current encrypted
    // format -- used by callers to decide whether a one-time plaintext migration
    // is needed (see ScoutApiKeyHelper.getKey()).
    fun isEncryptedFormat(stored: String): Boolean = stored.startsWith("$FORMAT_VERSION:")
}
