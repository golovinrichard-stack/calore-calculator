package com.calore.tracker.household

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.calore.tracker.CaloreApp
import com.calore.tracker.MainActivity
import com.calore.tracker.R
import com.calore.tracker.data.household.HouseholdState
import com.calore.tracker.notifications.CaloreChannel
import com.calore.tracker.notifications.Notifications
import com.calore.tracker.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Hub mode (M11.5, decision record 0001): a `connectedDevice` foreground service with a visible
 * notification. It keeps the hub listening on the home Wi-Fi. A wake lock is held only while the
 * phone is charging; on battery, Android may pause it and phones sync when it wakes or the app opens.
 */
class HubService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var charging: Job? = null

    private val power = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onCharging(intent.action == Intent.ACTION_POWER_CONNECTED)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        val container = (application as CaloreApp).container
        container.householdSync.setHubService(true)
        ContextCompat.registerReceiver(
            this,
            power,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onCharging(isCharging())
        scope.launch {
            combine(container.householdRepository.state, container.householdSync.status) { state, status -> state to status }
                .collect { (state, status) ->
                    if (state !is HouseholdState.In || !state.isHub) {
                        container.householdRepository.hubMode = false
                        stopSelf()
                        return@collect
                    }
                    val others = state.devices.count { it.id != state.me?.id && it.approved }
                    val people = resources.getQuantityString(R.plurals.hub_notification_members, others, others)
                    val text = status.lastSyncAt?.let {
                        getString(R.string.hub_notification_text, people, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
                    } ?: getString(R.string.hub_notification_text_never, people)
                    Notifications.notifyOngoing(this@HubService, Notifications.ID_HUB, notification(text))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY
    }

    private fun goForeground() {
        Notifications.ensureChannel(this, CaloreChannel.HUB)
        ServiceCompat.startForeground(
            this,
            Notifications.ID_HUB,
            notification(getString(R.string.hub_notification_starting)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
    }

    override fun onDestroy() {
        (application as CaloreApp).container.householdSync.setHubService(false)
        runCatching { unregisterReceiver(power) }
        onCharging(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun isCharging(): Boolean {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return status != 0
    }

    /** The wake lock only while charging (decision record 0001), renewed every hour. */
    private fun onCharging(on: Boolean) {
        charging?.cancel()
        charging = null
        wakeLock?.takeIf { it.isHeld }?.release()
        if (!on) return
        val lock = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "calore:hub") ?: return
        wakeLock = lock
        charging = scope.launch {
            while (isActive) {
                lock.acquire(WAKE_LOCK_MS)
                delay(WAKE_LOCK_RENEW_MS)
            }
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            Notifications.ID_HUB,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Notifications.EXTRA_OPEN, Routes.HOUSEHOLD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CaloreChannel.HUB.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.hub_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val WAKE_LOCK_MS = 65 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 60 * 60 * 1000L

        /** False when Android didn't allow it right now (from the background); the app-open fallback still works. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, HubService::class.java))
            true
        } catch (e: IllegalStateException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HubService::class.java))
        }
    }
}
