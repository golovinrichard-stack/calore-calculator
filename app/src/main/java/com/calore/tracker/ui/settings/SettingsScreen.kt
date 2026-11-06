package com.calore.tracker.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.FeatureFlags
import com.calore.tracker.R
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.body.MeasureReminderDialog
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.notifications.rememberNotificationPermission
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPots: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val perAppLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deniedMessage = stringResource(R.string.notifications_denied)
    var choosingMeasureReminder by rememberSaveable { mutableStateOf(false) }
    var pendingWeeks by rememberSaveable { mutableIntStateOf(0) }
    var pendingCheckin by rememberSaveable { mutableStateOf(false) }
    var pendingFreshness by rememberSaveable { mutableStateOf(false) }
    var pendingThaw by rememberSaveable { mutableStateOf(false) }

    // Reminders need notification permission; ask only when one is switched on (M1.6).
    val requestPermission = rememberNotificationPermission { granted ->
        if (granted) {
            if (pendingWeeks > 0) viewModel.setMeasureReminderWeeks(pendingWeeks)
            if (pendingCheckin) viewModel.setCheckinReminders(true)
            if (pendingFreshness) viewModel.setFreshness(morning = true)
            if (pendingThaw) viewModel.setFreshness(evening = true)
        } else {
            scope.launch { snackbarHostState.showSnackbar(deniedMessage) }
        }
        pendingWeeks = 0
        pendingCheckin = false
        pendingFreshness = false
        pendingThaw = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_hide_numbers)) },
                supportingContent = { Text(stringResource(R.string.settings_hide_numbers_desc)) },
                trailingContent = { Switch(checked = state.hideNumbers, onCheckedChange = null) },
                modifier = Modifier.clickable { viewModel.setHideNumbers(!state.hideNumbers) },
            )
            SectionHeader(stringResource(R.string.settings_reminders), modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_measure_reminder)) },
                supportingContent = {
                    Text(
                        if (state.measureReminderWeeks > 0) {
                            pluralStringResource(R.plurals.every_weeks, state.measureReminderWeeks, state.measureReminderWeeks)
                        } else {
                            stringResource(R.string.reminder_off)
                        },
                    )
                },
                modifier = Modifier.clickable { choosingMeasureReminder = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_checkin_reminder)) },
                supportingContent = { Text(stringResource(R.string.settings_checkin_reminder_desc)) },
                trailingContent = { Switch(checked = state.checkinReminders, onCheckedChange = null) },
                modifier = Modifier.clickable {
                    if (state.checkinReminders) {
                        viewModel.setCheckinReminders(false)
                    } else {
                        pendingCheckin = true
                        requestPermission()
                    }
                },
            )
            if (FeatureFlags.kitchen) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_freshness_reminder)) },
                    supportingContent = { Text(stringResource(R.string.settings_freshness_reminder_desc)) },
                    trailingContent = { Switch(checked = state.freshnessReminders, onCheckedChange = null) },
                    modifier = Modifier.clickable {
                        if (state.freshnessReminders) {
                            viewModel.setFreshness(morning = false)
                        } else {
                            pendingFreshness = true
                            requestPermission()
                        }
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_thaw_reminder)) },
                    supportingContent = { Text(stringResource(R.string.settings_thaw_reminder_desc)) },
                    trailingContent = { Switch(checked = state.thawReminders, onCheckedChange = null) },
                    modifier = Modifier.clickable {
                        if (state.thawReminders) {
                            viewModel.setFreshness(evening = false)
                        } else {
                            pendingThaw = true
                            requestPermission()
                        }
                    },
                )
                SectionHeader(stringResource(R.string.settings_kitchen), modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pots_title)) },
                    supportingContent = { Text(stringResource(R.string.pots_desc)) },
                    modifier = Modifier.clickable(onClick = onOpenPots),
                )
            }
            SectionHeader(stringResource(R.string.settings_data), modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_title)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_desc)) },
                modifier = Modifier.clickable(onClick = onOpenBackup),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (perAppLanguage) R.string.settings_language_desc else R.string.settings_language_desc_system,
                        ),
                    )
                },
                modifier = if (perAppLanguage) {
                    Modifier.clickable {
                        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // A few phones ship without this settings page; the phone's language still applies.
                        }
                    }
                } else {
                    Modifier
                },
            )
        }
    }

    if (choosingMeasureReminder) {
        MeasureReminderDialog(
            current = state.measureReminderWeeks,
            offering = false,
            onChoose = { weeks ->
                choosingMeasureReminder = false
                if (weeks == 0) {
                    viewModel.setMeasureReminderWeeks(0)
                } else {
                    pendingWeeks = weeks
                    requestPermission()
                }
            },
            onDismiss = { choosingMeasureReminder = false },
        )
    }
}
