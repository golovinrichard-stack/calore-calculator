package com.calore.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A medium-length date format in the app's current language ("6 Nov 2026", "6. 11. 2026").
 * Follows the per-app language as well as the phone's.
 */
@Composable
fun rememberMediumDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}
