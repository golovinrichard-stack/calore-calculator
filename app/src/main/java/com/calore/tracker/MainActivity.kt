package com.calore.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.calore.tracker.domain.cycle.AppLock
import com.calore.tracker.ui.cycle.LocalAppLock
import com.calore.tracker.ui.cycle.LockedContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calore.tracker.ui.LocalHideNumbers
import kotlinx.coroutines.flow.map
import com.calore.tracker.notifications.Notifications
import com.calore.tracker.ui.navigation.CaloreNavHost
import com.calore.tracker.ui.theme.CaloreTheme

/** A [FragmentActivity] so the app lock can show the system unlock prompt (M7.6). */
class MainActivity : FragmentActivity() {

    /** A screen a tapped notification asks for, such as the body-fat screen. */
    private val openRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRoute.value = intent.getStringExtra(Notifications.EXTRA_OPEN)
        val container = (application as CaloreApp).container
        val hideNumbersFlow = container.appPrefs.settings.map { it.hideNumbers }
        val lockAppFlow = container.cycleRepository.settings.map { it.enabled && it.appLock == AppLock.APP }
        setContent {
            val hideNumbers by hideNumbersFlow.collectAsStateWithLifecycle(initialValue = false)
            // Unknown until read: show nothing rather than a moment of locked content.
            val lockApp by lockAppFlow.collectAsStateWithLifecycle(initialValue = null)
            val unlocked by container.appLock.unlocked.collectAsStateWithLifecycle()
            CaloreTheme {
                CompositionLocalProvider(LocalHideNumbers provides hideNumbers, LocalAppLock provides container.appLock) {
                    when (lockApp) {
                        null -> Surface(Modifier.fillMaxSize()) {}
                        true -> if (unlocked) {
                            CaloreNavHost(openRoute = openRoute.value, onOpened = { openRoute.value = null })
                        } else {
                            Surface(Modifier.fillMaxSize()) { LockedContent(container.appLock) }
                        }
                        false -> CaloreNavHost(openRoute = openRoute.value, onOpened = { openRoute.value = null })
                    }
                }
            }
        }
    }

    /** M7.6: leaving the screen locks the app (and the cycle section) again; turning the phone doesn't. */
    override fun onStart() {
        super.onStart()
        (application as CaloreApp).container.householdSync.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as CaloreApp).container.householdSync.onBackground()
        if (!isChangingConfigurations) (application as CaloreApp).container.appLock.lock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_OPEN)?.let { openRoute.value = it }
    }
}
