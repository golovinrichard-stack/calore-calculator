package com.calore.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
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
        setContent {
            CaloreTheme {
                CaloreNavHost(openRoute = openRoute.value, onOpened = { openRoute.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_OPEN)?.let { openRoute.value = it }
    }
}
