package com.calore.tracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.calore.tracker.domain.research.ResearchCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Research consent (data-model M10, DataStore `research_prefs`). On this phone only, never in backups. */
data class ResearchConsent(
    val participantId: String? = null,
    /** 32 random bytes, Base64; the server stores only its SHA-256 hash. */
    val participantSecret: String? = null,
    val consentVersion: Int? = null,
    val consentedAt: Long? = null,
    val categories: Set<ResearchCategory> = emptySet(),
    /** ISO 3166-2:CZ code, or null. */
    val region: String? = null,
    val lastReportWeek: String? = null,
    /** "Stop sharing" (M10.5): when consent was withdrawn. */
    val withdrawnAt: Long? = null,
    /** "Delete my data from the server" is waiting for the network (M10.5). */
    val pendingDeletion: Boolean = false,
    /** The registration still has to reach the server. */
    val registrationPending: Boolean = false,
) {
    /** Taking part now: agreed and not stopped. */
    val active: Boolean get() = participantId != null && participantSecret != null && withdrawnAt == null && !pendingDeletion
}

class ResearchPrefs(private val dataStore: DataStore<Preferences>) {

    val consent: Flow<ResearchConsent> = dataStore.data.map { p ->
        ResearchConsent(
            participantId = p[PARTICIPANT_ID],
            participantSecret = p[PARTICIPANT_SECRET],
            consentVersion = p[CONSENT_VERSION],
            consentedAt = p[CONSENTED_AT],
            categories = p[CATEGORIES].orEmpty().mapNotNull { name -> ResearchCategory.entries.firstOrNull { it.name == name } }.toSet(),
            region = p[REGION],
            lastReportWeek = p[LAST_REPORT_WEEK],
            withdrawnAt = p[WITHDRAWN_AT],
            pendingDeletion = p[PENDING_DELETION] == 1,
            registrationPending = p[REGISTRATION_PENDING] == 1,
        )
    }

    suspend fun save(consent: ResearchConsent) {
        dataStore.edit { p ->
            fun <T> set(key: Preferences.Key<T>, value: T?) {
                if (value == null) p.remove(key) else p[key] = value
            }
            set(PARTICIPANT_ID, consent.participantId)
            set(PARTICIPANT_SECRET, consent.participantSecret)
            set(CONSENT_VERSION, consent.consentVersion)
            set(CONSENTED_AT, consent.consentedAt)
            p[CATEGORIES] = consent.categories.map { it.name }.toSet()
            set(REGION, consent.region)
            set(LAST_REPORT_WEEK, consent.lastReportWeek)
            set(WITHDRAWN_AT, consent.withdrawnAt)
            p[PENDING_DELETION] = if (consent.pendingDeletion) 1 else 0
            p[REGISTRATION_PENDING] = if (consent.registrationPending) 1 else 0
        }
    }

    companion object {
        const val FILE_NAME = "research_prefs"
        private val PARTICIPANT_ID = stringPreferencesKey("participant_id")
        private val PARTICIPANT_SECRET = stringPreferencesKey("participant_secret")
        private val CONSENT_VERSION = intPreferencesKey("consent_version")
        private val CONSENTED_AT = longPreferencesKey("consented_at")
        private val CATEGORIES = stringSetPreferencesKey("categories")
        private val REGION = stringPreferencesKey("region")
        private val LAST_REPORT_WEEK = stringPreferencesKey("last_report_week")
        private val WITHDRAWN_AT = longPreferencesKey("withdrawn_at")
        private val PENDING_DELETION = intPreferencesKey("pending_deletion")
        private val REGISTRATION_PENDING = intPreferencesKey("registration_pending")
    }
}
