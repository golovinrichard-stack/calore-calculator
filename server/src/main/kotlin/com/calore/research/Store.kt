package com.calore.research

import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/** A stored participant: never the secret, only its SHA-256 hash; never an IP address or a timestamp. */
data class Participant(val id: String, val secretHash: String, val consentVersion: Int, val categories: Set<String>, val profile: String)

/** A stored report: the participant, the ISO week it is about, the ISO week it arrived, and the JSON. */
data class StoredReport(val reportId: String, val participantId: String, val isoWeek: String, val receivedWeek: String, val payload: String)

/**
 * The research database over JDBC: PostgreSQL in production, H2 in PostgreSQL mode in tests.
 * The SQL is kept to what both understand. One connection per call: the traffic is tiny.
 */
class Store(private val url: String, private val user: String? = null, private val password: String? = null) {

    private fun <T> connect(block: (Connection) -> T): T =
        (if (user == null) DriverManager.getConnection(url) else DriverManager.getConnection(url, user, password)).use(block)

    fun migrate() = connect { c ->
        c.createStatement().use { s ->
            s.execute(
                "CREATE TABLE IF NOT EXISTS participants (id VARCHAR(36) PRIMARY KEY, secret_hash VARCHAR(64) NOT NULL, " +
                    "consent_version INT NOT NULL, categories VARCHAR(200) NOT NULL, profile VARCHAR(2000) NOT NULL, " +
                    "registered_week VARCHAR(8) NOT NULL)",
            )
            s.execute(
                "CREATE TABLE IF NOT EXISTS reports (report_id VARCHAR(36) PRIMARY KEY, participant_id VARCHAR(36) NOT NULL " +
                    "REFERENCES participants(id) ON DELETE CASCADE, iso_week VARCHAR(8) NOT NULL, received_week VARCHAR(8) NOT NULL, " +
                    "payload VARCHAR(16384) NOT NULL)",
            )
            s.execute("CREATE INDEX IF NOT EXISTS reports_participant ON reports (participant_id)")
        }
    }

    /** False when the participant exists already. */
    fun register(p: Participant, week: String): Boolean = connect { c ->
        try {
            c.prepareStatement("INSERT INTO participants (id, secret_hash, consent_version, categories, profile, registered_week) VALUES (?, ?, ?, ?, ?, ?)").use { st ->
                st.setString(1, p.id)
                st.setString(2, p.secretHash)
                st.setInt(3, p.consentVersion)
                st.setString(4, p.categories.sorted().joinToString(","))
                st.setString(5, p.profile)
                st.setString(6, week)
                st.executeUpdate()
            }
            true
        } catch (e: SQLException) {
            if (participant(p.id) != null) false else throw e
        }
    }

    fun participant(id: String): Participant? = connect { c ->
        c.prepareStatement("SELECT id, secret_hash, consent_version, categories, profile FROM participants WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { rs ->
                if (!rs.next()) null else Participant(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4).split(',').filter { it.isNotEmpty() }.toSet(), rs.getString(5))
            }
        }
    }

    fun update(id: String, consentVersion: Int, categories: Set<String>, profile: String) = connect { c ->
        c.prepareStatement("UPDATE participants SET consent_version = ?, categories = ?, profile = ? WHERE id = ?").use { st ->
            st.setInt(1, consentVersion)
            st.setString(2, categories.sorted().joinToString(","))
            st.setString(3, profile)
            st.setString(4, id)
            st.executeUpdate()
        }
    }

    /** Idempotent by report id: storing the same report again changes nothing. */
    fun insertReport(r: StoredReport): Boolean = connect { c ->
        val exists = c.prepareStatement("SELECT 1 FROM reports WHERE report_id = ?").use { st ->
            st.setString(1, r.reportId)
            st.executeQuery().use { it.next() }
        }
        if (exists) return@connect false
        c.prepareStatement("INSERT INTO reports (report_id, participant_id, iso_week, received_week, payload) VALUES (?, ?, ?, ?, ?)").use { st ->
            st.setString(1, r.reportId)
            st.setString(2, r.participantId)
            st.setString(3, r.isoWeek)
            st.setString(4, r.receivedWeek)
            st.setString(5, r.payload)
            st.executeUpdate()
        }
        true
    }

    /** Deletes the participant and every report (M10.5). */
    fun deleteParticipant(id: String): Boolean = connect { c ->
        c.autoCommit = false
        try {
            c.prepareStatement("DELETE FROM reports WHERE participant_id = ?").use { st ->
                st.setString(1, id)
                st.executeUpdate()
            }
            val n = c.prepareStatement("DELETE FROM participants WHERE id = ?").use { st ->
                st.setString(1, id)
                st.executeUpdate()
            }
            c.commit()
            n > 0
        } catch (e: SQLException) {
            c.rollback()
            throw e
        }
    }

    fun countRows(participantId: String): Int = connect { c ->
        c.prepareStatement("SELECT (SELECT COUNT(*) FROM participants WHERE id = ?) + (SELECT COUNT(*) FROM reports WHERE participant_id = ?)").use { st ->
            st.setString(1, participantId)
            st.setString(2, participantId)
            st.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    /** Reports with their participant's profile, for the statistics export. */
    fun reportsWithProfiles(): List<Pair<StoredReport, String>> = connect { c ->
        c.createStatement().use { s ->
            s.executeQuery(
                "SELECT r.report_id, r.participant_id, r.iso_week, r.received_week, r.payload, p.profile FROM reports r " +
                    "JOIN participants p ON p.id = r.participant_id ORDER BY r.iso_week",
            ).use { rs ->
                buildList {
                    while (rs.next()) add(StoredReport(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)) to rs.getString(6))
                }
            }
        }
    }

    companion object {
        fun hash(secret: String): String =
            MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
