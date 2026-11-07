package com.calore.tracker.household

import android.content.Context
import com.calore.tracker.R
import com.calore.tracker.data.db.HouseholdDao
import com.calore.tracker.data.household.HouseholdEvents
import com.calore.tracker.data.household.HouseholdRepository
import com.calore.tracker.data.household.HouseholdState
import com.calore.tracker.data.sync.ApplyOutcome
import com.calore.tracker.notifications.CaloreChannel
import com.calore.tracker.notifications.Notifications
import com.calore.tracker.ui.navigation.Routes

/** Notifications about what a sync brought in (channel Household, M11.5, M11.7). */
class HouseholdNotifier(
    private val context: Context,
    private val household: HouseholdRepository,
    private val dao: HouseholdDao,
    private val requests: RequestNotifier,
) : HouseholdEvents {

    override suspend fun onReceived(outcome: ApplyOutcome) {
        val state = household.current() as? HouseholdState.In ?: return
        val me = household.deviceId
        val deviceRows = outcome.rows.filter { it.table == "household_devices" }
        if (state.isHub) {
            for (row in deviceRows) {
                val device = dao.device(row.rowId) ?: continue
                if (device.id != me && !device.approved && device.deletedAt == null) {
                    post(
                        Notifications.ID_HOUSEHOLD_PHONE,
                        context.getString(R.string.household_notify_new_phone_title),
                        context.getString(R.string.household_notify_new_phone_text, device.memberName),
                    )
                }
            }
            if (deviceRows.any { it.rowId == me && "isHub" in it.columns }) {
                post(
                    Notifications.ID_HOUSEHOLD_HUB_NOW,
                    context.getString(R.string.household_notify_hub_now_title),
                    context.getString(R.string.household_notify_hub_now_text),
                )
            }
        } else if (deviceRows.any { it.rowId == me && "approved" in it.columns } && state.approved) {
            post(
                Notifications.ID_HOUSEHOLD_PHONE,
                context.getString(R.string.household_notify_approved_title),
                context.getString(R.string.household_notify_approved_text, state.household.name),
            )
        }
        for (conflict in outcome.conflicts) {
            val device = dao.device(conflict.earlierDevice)
            post(
                Notifications.ID_HOUSEHOLD_TAKEN,
                context.getString(R.string.household_notify_taken_title),
                context.getString(R.string.household_notify_taken_text, device?.memberName ?: "?"),
            )
        }
        requests.onReceived(outcome, state)
    }

    private fun post(id: Int, title: String, text: String) =
        Notifications.post(context, CaloreChannel.HOUSEHOLD, id, title, text, Routes.HOUSEHOLD)
}

/** Requests and answers that arrived (M11.7); filled in with the requests. */
fun interface RequestNotifier {
    suspend fun onReceived(outcome: ApplyOutcome, state: HouseholdState.In)
}
