package com.calore.tracker.data.household

import com.calore.tracker.data.sync.ApplyOutcome
import com.calore.tracker.data.sync.BadFrameException
import com.calore.tracker.domain.household.HouseholdInvite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.time.Clock

/** Why the last sync didn't happen (M11.4, M11.10). */
enum class SyncProblem { NO_WIFI, NO_HUB, CODE_CHANGED, FAILED }

data class SyncStatus(
    val syncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val problem: SyncProblem? = null,
    /** This phone is serving as the hub right now. */
    val serving: Boolean = false,
)

/** What a sync brought in, for notifications and the feed (M11.7, M11.8). */
fun interface HouseholdEvents {
    suspend fun onReceived(outcome: ApplyOutcome)
}

/**
 * When to sync (M11.4): when the app opens on Wi-Fi, 5 seconds after a local change, every minute
 * while the app is open, and on the hub whenever hub mode is on or the app is open there.
 */
class HouseholdSync(
    private val household: HouseholdRepository,
    private val network: HouseholdNetwork,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val events: HouseholdEvents,
) {
    private val state = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = state.asStateFlow()

    private val sessions = Mutex()
    private var foreground = false
    private var hubService = false
    private var loop: Job? = null
    private var server: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            household.recordedChanges.filter { it > 0 }.debounce(LOCAL_CHANGE_DELAY_MS).collect { syncNow() }
        }
        scope.launch {
            household.state.collect { updateServer(it) }
        }
    }

    fun onForeground() {
        foreground = true
        scope.launch { updateServer(household.current()) }
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                syncNow()
                delay(FOREGROUND_INTERVAL_MS)
            }
        }
    }

    fun onBackground() {
        foreground = false
        loop?.cancel()
        scope.launch { updateServer(household.current()) }
    }

    /** Called by the hub service (M11.5). */
    fun setHubService(running: Boolean) {
        hubService = running
        scope.launch { updateServer(household.current()) }
    }

    /** One sync with the hub, from a phone that isn't the hub. */
    suspend fun syncNow() {
        val current = household.current() as? HouseholdState.In ?: return
        if (current.isHub) return
        if (!network.onHomeNetwork()) {
            state.update { it.copy(problem = SyncProblem.NO_WIFI) }
            return
        }
        val key = household.key() ?: return
        sessions.withLock {
            state.update { it.copy(syncing = true) }
            val problem = withContext(Dispatchers.IO) {
                val address = network.findHub(HouseholdInvite.discoveryHash(current.household.id)) ?: return@withContext SyncProblem.NO_HUB
                try {
                    Socket().use { socket ->
                        socket.connect(address, TIMEOUT_MS)
                        socket.soTimeout = TIMEOUT_MS
                        val result = SyncSession(key, current.household.id).runPhone(
                            DataInputStream(BufferedInputStream(socket.getInputStream())),
                            DataOutputStream(BufferedOutputStream(socket.getOutputStream())),
                            household.endpoint(),
                        )
                        household.afterPhoneSession(result)
                        result.received?.let { events.onReceived(it) }
                    }
                    null
                } catch (e: BadFrameException) {
                    // The hub's frames don't open with this phone's key: the host made a new code (or removed this phone).
                    SyncProblem.CODE_CHANGED
                } catch (e: IOException) {
                    SyncProblem.FAILED
                }
            }
            state.update {
                it.copy(syncing = false, problem = problem, lastSyncAt = if (problem == null) clock.millis() else it.lastSyncAt)
            }
        }
    }

    @Synchronized
    private fun updateServer(current: HouseholdState) {
        val shouldServe = current is HouseholdState.In && current.isHub && (hubService || foreground)
        if (shouldServe && server?.isActive != true) {
            server = scope.launch(Dispatchers.IO) { serve((current as HouseholdState.In).household.id) }
        } else if (!shouldServe && server?.isActive == true) {
            server?.cancel()
            server = null
        }
    }

    private suspend fun serve(householdId: String) {
        val socket = try {
            ServerSocket(0)
        } catch (e: IOException) {
            return
        }
        val announcement = network.announce(socket.localPort, HouseholdInvite.discoveryHash(householdId))
        state.update { it.copy(serving = true) }
        try {
            currentCoroutineContext()[Job]?.invokeOnCompletion { runCatching { socket.close() } }
            while (currentCoroutineContext().isActive) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                scope.launch(Dispatchers.IO) { serveOne(client, householdId) }
            }
        } finally {
            announcement.close()
            runCatching { socket.close() }
            state.update { it.copy(serving = false) }
        }
    }

    private suspend fun serveOne(client: Socket, householdId: String) = sessions.withLock {
        client.use { s ->
            s.soTimeout = TIMEOUT_MS
            val key = household.key() ?: return@withLock
            try {
                val result = SyncSession(key, householdId).runHub(
                    DataInputStream(BufferedInputStream(s.getInputStream())),
                    DataOutputStream(BufferedOutputStream(s.getOutputStream())),
                    household.endpoint(),
                ) { household.admission(it) }
                if (result.admission != Admission.REFUSED) {
                    household.afterHubSession(result)
                    result.received?.let { events.onReceived(it) }
                    state.update { it.copy(lastSyncAt = clock.millis()) }
                }
            } catch (e: BadFrameException) {
                // A stranger, a removed phone or a broken frame: the connection just closes.
            } catch (e: IOException) {
                // The phone went away mid-sync; it will try again.
            }
        }
    }

    companion object {
        const val LOCAL_CHANGE_DELAY_MS = 5_000L
        const val FOREGROUND_INTERVAL_MS = 60_000L
        const val TIMEOUT_MS = 10_000
    }
}
