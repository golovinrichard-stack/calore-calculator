package com.calore.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.calore.tracker.ui.navigation.CaloreNavHost
import com.calore.tracker.ui.theme.CaloreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaloreTheme {
                CaloreNavHost()
            }
        }
    }
}
