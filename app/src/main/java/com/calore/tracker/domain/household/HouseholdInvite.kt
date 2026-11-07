package com.calore.tracker.domain.household

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64

/**
 * What the host's QR code holds (M11.2): `{v: 1, householdId, name, hubDeviceId, key}`, where `key`
 * is 32 random bytes in Base64. Anyone who scans it can talk to the household's phones, so it is
 * shown only on the host's screen.
 */
@Serializable
data class HouseholdInvite(
    val v: Int = VERSION,
    val householdId: String,
    val name: String,
    val hubDeviceId: String,
    val key: String,
) {
    fun keyBytes(): ByteArray = Base64.getDecoder().decode(key)

    companion object {
        const val VERSION = 1
        const val KEY_BYTES = 32
        private const val MAX_NAME = 60
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun of(householdId: String, name: String, hubDeviceId: String, key: ByteArray): HouseholdInvite =
            HouseholdInvite(VERSION, householdId, name, hubDeviceId, Base64.getEncoder().encodeToString(key))

        fun encode(invite: HouseholdInvite): String = json.encodeToString(serializer(), invite)

        /** The invite in a scanned text, or null when it isn't a valid Calore household code. */
        fun decode(text: String): HouseholdInvite? {
            val invite = try {
                json.decodeFromString(serializer(), text.trim())
            } catch (e: IllegalArgumentException) {
                return null
            }
            if (invite.v != VERSION) return null
            if (invite.householdId.isBlank() || invite.hubDeviceId.isBlank()) return null
            if (invite.name.isBlank() || invite.name.length > MAX_NAME) return null
            val key = try {
                invite.keyBytes()
            } catch (e: IllegalArgumentException) {
                return null
            }
            return invite.takeIf { key.size == KEY_BYTES }
        }

        /**
         * What the hub puts in its network announcement (M11.4): a hash of the household id, so
         * phones find their hub, while the network never sees the name, the id or the key.
         */
        fun discoveryHash(householdId: String): String =
            MessageDigest.getInstance("SHA-256").digest("calore-household:$householdId".toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it) }
    }
}
