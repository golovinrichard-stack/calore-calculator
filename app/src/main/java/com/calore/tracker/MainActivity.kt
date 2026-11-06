package com.calore.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calore.tracker.ui.LocalHideNumbers
import kotlinx.coroutines.flow.map
import com.calore.tracker.notifications.Notifications
import com.calore.tracker.ui.navigation.CaloreNavHost
import com.calore.tracker.ui.theme.CaloreTheme

class MainActivity : ComponentActivity() {

    /** A screen a tapped notification asks for, such as the body-fat screen. */
    private val openRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRoute.value = intent.getStringExtra(Notifications.EXTRA_OPEN)
        val hideNumbersFlow = (application as CaloreApp).container.appPrefs.settings.map { it.hideNumbers }
        setContent {
            val hideNumbers by hideNumbersFlow.collectAsStateWithLifecycle(initialValue = false)
            CaloreTheme {
                CompositionLocalProvider(LocalHideNumbers provides hideNumbers) {
                    CaloreNavHost(openRoute = openRoute.value, onOpened = { openRoute.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_OPEN)?.let { openRoute.value = it }
    }
}
