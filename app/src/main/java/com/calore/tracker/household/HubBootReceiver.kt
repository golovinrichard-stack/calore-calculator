package com.calore.tracker.household

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calore.tracker.CaloreApp
import com.calore.tracker.data.household.HouseholdState
import kotlinx.coroutines.launch

/** Hub mode starts again after the phone restarts or the app updates, if it was on (decision record 0001). */
class HubBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as CaloreApp).container
        if (!container.householdRepository.hubMode) return
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val state = container.householdRepository.current()
                if (state is HouseholdState.In && state.isHub) HubService.start(context) else container.householdRepository.hubMode = false
            } finally {
                pending.finish()
            }
        }
    }
}
