package com.calore.tracker.data.household

import com.calore.tracker.data.db.ActivityEntity
import com.calore.tracker.data.db.HouseholdDao
import com.calore.tracker.data.db.MemberDao
import com.calore.tracker.domain.household.ActivityFeed
import com.calore.tracker.domain.household.ActivityType
import com.calore.tracker.domain.newId
import java.time.Clock

/** Writes a line of the activity feed (M11.8); does nothing outside a household. */
fun interface ActivityLog {
    suspend fun log(type: ActivityType, refTable: String?, refId: String?, names: List<String>, count: Int?)

    suspend fun log(type: ActivityType, refTable: String?, refId: String?, name: String) = log(type, refTable, refId, listOf(name), null)

    companion object {
        val NONE = ActivityLog { _, _, _, _, _ -> }
    }
}

/** The feed is a shared table, so each line goes to the other phones with the next sync. */
class HouseholdActivityLog(
    private val dao: HouseholdDao,
    private val memberDao: MemberDao,
    private val clock: Clock,
) : ActivityLog {
    override suspend fun log(type: ActivityType, refTable: String?, refId: String?, names: List<String>, count: Int?) {
        val kept = names.filter { it.isNotBlank() }
        if (dao.household() == null) return
        // Requests and answers are worth a line even without an item name.
        if (kept.isEmpty() && type != ActivityType.REQUESTED && type != ActivityType.ANSWERED) return
        val me = memberDao.me() ?: return
        dao.upsertActivity(ActivityEntity(newId(), me.id, type.name, refTable, refId, ActivityFeed.encode(kept, count), clock.millis()))
    }
}
