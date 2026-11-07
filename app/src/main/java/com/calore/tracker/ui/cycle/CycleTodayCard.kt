package com.calore.tracker.ui.cycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.domain.cycle.AppLock
import com.calore.tracker.ui.AppViewModelProvider

/**
 * Today's cycle card (M7.3, M7.4): "Day 18 · luteal phase · next period in 9–11 days" and the phase
 * tips. Shows nothing when tracking is off, or while the cycle section is locked.
 */
@Composable
fun CycleTodayCard(onOpen: () -> Unit, viewModel: CycleViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unlocked by LocalAppLock.current.unlocked.collectAsStateWithLifecycle()
    val settings = state.settings
    if (!state.loaded || !settings.enabled) return
    if (settings.appLock != AppLock.OFF && !unlocked) return
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.cycle_title), style = MaterialTheme.typography.titleMedium)
            CycleTodayText(state.todayInfo, settings, style = MaterialTheme.typography.bodyLarge)
            state.tips.forEach { Text("•  " + stringResource(it.labelRes()), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
