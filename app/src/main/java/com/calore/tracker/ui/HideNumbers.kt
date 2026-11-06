package com.calore.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hide-numbers mode (M1.8): log food without seeing calories and macros. Provided once at the
 * root from the app settings; every screen that shows calories or macros reads it.
 */
val LocalHideNumbers = staticCompositionLocalOf { false }

/**
 * For screens where numbers appear only after "Show numbers" (Goals, Body, food details).
 * Returns whether numbers are visible now, and the action that reveals them for this screen.
 */
@Composable
fun rememberNumbersVisible(): Pair<Boolean, () -> Unit> {
    val hidden = LocalHideNumbers.current
    var revealed by rememberSaveable { mutableStateOf(false) }
    return (!hidden || revealed) to { revealed = true }
}
