package com.calore.tracker.data.household

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.net.InetSocketAddress
import kotlin.coroutines.resume

/**
 * Finding the hub on the home network (M11.4): the hub announces `_calore._tcp` with a hash of the
 * household id in its TXT record (never the name or the key); phones look for that hash. Nothing
 * here leaves the local network.
 */
class HouseholdNetwork(context: Context) {

    private val app = context.applicationContext
    private val nsd: NsdManager? = app.getSystemService(NsdManager::class.java)
    private val wifi: WifiManager? = app.getSystemService(WifiManager::class.java)
    private val connectivity: ConnectivityManager? = app.getSystemService(ConnectivityManager::class.java)

    /** True on Wi-Fi (or a wired home network). */
    fun onHomeNetwork(): Boolean {
        val manager = connectivity ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun multicastLock(tag: String): Closeable {
        val lock = wifi?.createMulticastLock(tag)?.apply {
            setReferenceCounted(false)
            acquire()
        }
        return Closeable { if (lock?.isHeld == true) lock.release() }
    }

    /** Announces the hub on [port]; closing the result withdraws the announcement. */
    fun announce(port: Int, hash: String): Closeable {
        val manager = nsd ?: return Closeable {}
        val info = NsdServiceInfo().apply {
            serviceName = NAME_PREFIX + hash.take(NAME_HASH_CHARS)
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute(TXT_HASH, hash)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        val lock = multicastLock("calore-hub")
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return Closeable {
            runCatching { manager.unregisterService(listener) }
            lock.close()
        }
    }

    /** The address of the hub of the household with [hash] on this network, or null after [timeoutMs]. */
    suspend fun findHub(hash: String, timeoutMs: Long = FIND_TIMEOUT_MS): InetSocketAddress? {
        val manager = nsd ?: return null
        return withTimeoutOrNull(timeoutMs) {
            callbackFlow {
                val lock = multicastLock("calore-find")
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.serviceName.startsWith(NAME_PREFIX + hash.take(NAME_HASH_CHARS))) trySend(serviceInfo)
                    }
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        close()
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                }
                manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                awaitClose {
                    runCatching { manager.stopServiceDiscovery(listener) }
                    lock.close()
                }
            }
                .mapNotNull { resolve(manager, it) }
                .filter { it.attributes[TXT_HASH]?.decodeToString() == hash }
                .mapNotNull { info -> address(info) }
                .firstOrNull()
        }
    }

    @Suppress("DEPRECATION") // resolveService and host: the replacements need Android 14; these work on all versions.
    private suspend fun resolve(manager: NsdManager, info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        manager.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(serviceInfo)
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun address(info: NsdServiceInfo): InetSocketAddress? = info.host?.let { InetSocketAddress(it, info.port) }

    companion object {
        const val SERVICE_TYPE = "_calore._tcp"
        private const val NAME_PREFIX = "Calore-"
        private const val NAME_HASH_CHARS = 8
        private const val TXT_HASH = "h"
        const val FIND_TIMEOUT_MS = 8_000L
    }
}
