package com.calore.tracker.data.household

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.calore.tracker.domain.newId
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * This phone's household secrets (M11.2), in the app's no-backup folder, so they are never in
 * Android backups or phone-to-phone transfers:
 * - the device id (two phones must never share one, so it can't come back from a backup);
 * - the household key, encrypted with an AES key that lives in the Android Keystore;
 * - whether hub mode is on (to start it again after the phone restarts).
 * The key is never in the database or in the app's own backup file.
 */
class HouseholdKeys(context: Context, folder: String = "household") {

    private val dir = File(context.noBackupFilesDir, folder)
    private val idFile = File(dir, "device-id")
    private val keyFile = File(dir, "key")
    private val hubFile = File(dir, "hub-mode")

    @Volatile private var cached: ByteArray? = null

    @Synchronized
    fun deviceId(): String {
        idFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        dir.mkdirs()
        return newId().also { idFile.writeText(it) }
    }

    @Synchronized
    fun saveKey(key: ByteArray) {
        dir.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        keyFile.writeBytes(cipher.iv + cipher.doFinal(key))
        cached = key.copyOf()
    }

    /** The household key, or null when there is none (or it can't be read any more). */
    @Synchronized
    fun key(): ByteArray? {
        cached?.let { return it.copyOf() }
        if (!keyFile.exists()) return null
        return try {
            val bytes = keyFile.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).also { cached = it.copyOf() }
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    var hubMode: Boolean
        get() = hubFile.exists()
        set(on) {
            if (on) {
                dir.mkdirs()
                hubFile.writeText("on")
            } else {
                hubFile.delete()
            }
        }

    /** Leaving the household: the key and hub mode go; the device id stays. */
    @Synchronized
    fun clear() {
        cached = null
        keyFile.delete()
        hubFile.delete()
        try {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        } catch (e: GeneralSecurityException) {
            // Nothing to delete.
        } catch (e: IOException) {
            // Nothing to delete.
        }
    }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "calore_household_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
