package com.calore.tracker.household

import android.content.Context
import com.calore.tracker.R
import com.calore.tracker.data.db.HouseholdDao
import com.calore.tracker.data.db.MemberDao
import com.calore.tracker.data.household.HouseholdState
import com.calore.tracker.data.household.RequestStatus
import com.calore.tracker.data.household.requestStatus
import com.calore.tracker.data.sync.ApplyOutcome
import com.calore.tracker.notifications.CaloreChannel
import com.calore.tracker.notifications.Notifications
import com.calore.tracker.ui.navigation.Routes

/** M11.7: notifications when requests and answers arrive (channel Household). */
class HouseholdRequestNotifier(
    private val context: Context,
    private val dao: HouseholdDao,
    private val memberDao: MemberDao,
    private val itemName: suspend (refTable: String?, refId: String?) -> String?,
) : RequestNotifier {

    override suspend fun onReceived(outcome: ApplyOutcome, state: HouseholdState.In) {
        val me = memberDao.me()?.id ?: return
        for (row in outcome.rows.filter { it.table == "requests" }) {
            val request = dao.request(row.rowId) ?: continue
            val from = memberDao.byId(request.fromMemberId)?.name ?: "?"
            val id = Notifications.ID_REQUEST_BASE + (request.id.hashCode() and 0xFFF)
            val name = itemName(request.refTable, request.refId)
            val isNew = "type" in row.columns
            when {
                isNew && request.fromMemberId != me && (request.toMemberId == null || request.toMemberId == me) &&
                    request.requestStatus == RequestStatus.OPEN ->
                    post(id, context.getString(R.string.request_notify_new, from), RequestText.what(context, request, name))
                request.fromMemberId == me && "status" in row.columns && request.requestStatus != RequestStatus.OPEN -> {
                    val by = request.toMemberId?.let { memberDao.byId(it)?.name } ?: context.getString(R.string.request_someone)
                    post(id, context.getString(R.string.request_notify_answer, by, RequestText.status(context, request)), RequestText.what(context, request, name))
                }
                request.fromMemberId == me && !isNew && "dueDate" in row.columns && request.requestStatus == RequestStatus.OPEN ->
                    post(id, context.getString(R.string.request_notify_suggested), RequestText.what(context, request, name))
            }
        }
    }

    private fun post(id: Int, title: String, text: String) =
        Notifications.post(context, CaloreChannel.HOUSEHOLD, id, title, text, Routes.REQUESTS)
}
