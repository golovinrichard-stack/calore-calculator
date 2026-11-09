package com.calore.tracker.ui.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.db.HouseholdDeviceEntity
import com.calore.tracker.data.household.HouseholdState
import com.calore.tracker.data.household.SyncProblem
import com.calore.tracker.domain.household.HouseholdInvite
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.SectionHeader
import java.text.DateFormat
import java.util.Date

/** The household (M11.2, M11.5, M11.10): create or join, the code, the phones, hub mode, leave. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdScreen(
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onOpenRequests: () -> Unit,
    viewModel: HouseholdViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val invite by viewModel.invite.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var explainHub by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.household_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.loaded) return@Column
            when (val household = state.household) {
                HouseholdState.None -> NotInHousehold(onCreate = { creating = true }, onJoin = onJoin)
                is HouseholdState.NeedsKey -> NeedsKey(household.household.name, onJoin = onJoin, onLeave = { confirmLeave = true })
                is HouseholdState.In -> InHousehold(
                    state = state,
                    household = household,
                    onSync = viewModel::syncNow,
                    onJoin = onJoin,
                    onHubMode = { on -> if (on) explainHub = "" else viewModel.setHubMode(false) },
                    onShowCode = viewModel::showCode,
                    onApprove = viewModel::approve,
                    onRemove = viewModel::remove,
                    onHandOver = viewModel::handOver,
                    onSharesTargets = viewModel::setSharesTargets,
                    onOpenRequests = onOpenRequests,
                    onLeave = { confirmLeave = true },
                )
            }
        }
    }

    if (creating) {
        CreateDialog(
            onCreate = { name ->
                creating = false
                explainHub = name
            },
            onDismiss = { creating = false },
        )
    }
    explainHub?.let { name ->
        HubExplanationDialog(
            onStart = {
                explainHub = null
                if (name.isBlank()) viewModel.setHubMode(true) else viewModel.create(name)
            },
            onDismiss = { explainHub = null },
        )
    }
    invite?.let { CodeDialog(it, onDismiss = viewModel::hideCode) }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.household_leave_title)) },
            text = { Text(stringResource(R.string.household_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.leave()
                }) { Text(stringResource(R.string.household_leave)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NotInHousehold(onCreate: () -> Unit, onJoin: () -> Unit) {
    Text(stringResource(R.string.household_intro), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.household_never_shared), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.household_wifi_only), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.household_create)) }
    OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.household_join)) }
}

@Composable
private fun NeedsKey(name: String, onJoin: () -> Unit, onLeave: () -> Unit) {
    Text(stringResource(R.string.household_needs_key, name), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.household_scan_code)) }
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.household_leave)) }
}

@Composable
private fun InHousehold(
    state: HouseholdUiState,
    household: HouseholdState.In,
    onSync: () -> Unit,
    onJoin: () -> Unit,
    onHubMode: (Boolean) -> Unit,
    onShowCode: () -> Unit,
    onApprove: (String) -> Unit,
    onRemove: (String) -> Unit,
    onHandOver: (String) -> Unit,
    onSharesTargets: (Boolean) -> Unit,
    onOpenRequests: () -> Unit,
    onLeave: () -> Unit,
) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var handingOver by rememberSaveable { mutableStateOf<String?>(null) }
    Text(household.household.name, style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(if (household.isHub) R.string.household_role_hub else R.string.household_role_member),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!household.approved) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.household_waiting_for_host), modifier = Modifier.padding(16.dp))
        }
    }

    // Sync status (M11.4, M11.10).
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val last = state.status.lastSyncAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
            Text(
                if (last != null) stringResource(R.string.household_last_sync, last) else stringResource(R.string.household_never_synced),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.waiting > 0) {
                Text(pluralStringResource(R.plurals.household_changes_waiting, state.waiting, state.waiting), style = MaterialTheme.typography.bodyMedium)
            }
            when (state.status.problem) {
                SyncProblem.NO_WIFI -> Text(stringResource(R.string.household_problem_no_wifi))
                SyncProblem.NO_HUB -> Text(stringResource(R.string.household_problem_no_hub))
                SyncProblem.FAILED -> Text(stringResource(R.string.household_problem_failed))
                SyncProblem.CODE_CHANGED -> {
                    Text(stringResource(R.string.household_problem_code_changed))
                    OutlinedButton(onClick = onJoin) { Text(stringResource(R.string.household_scan_code)) }
                }
                null -> Unit
            }
            if (state.status.syncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (household.isHub) {
                Text(
                    stringResource(if (state.status.serving) R.string.household_hub_serving else R.string.household_hub_not_serving),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                OutlinedButton(onClick = onSync, enabled = !state.status.syncing) { Text(stringResource(R.string.household_sync_now)) }
            }
        }
    }


    if (household.approved) {
        Button(onClick = onOpenRequests, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.requests_title)) }
    }

    if (household.isHub) {
        SectionHeader(stringResource(R.string.household_hub_section))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.household_hub_mode), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.household_hub_mode_desc), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.hubMode, onCheckedChange = onHubMode)
        }
        Button(onClick = onShowCode, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.household_show_code)) }
    }

    SectionHeader(stringResource(R.string.household_phones))
    household.devices.forEach { device ->
        DeviceRow(
            device = device,
            isMe = device.id == household.me?.id,
            canManage = household.isHub && device.id != household.me?.id,
            onApprove = { onApprove(device.id) },
            onRemove = { removing = device.id },
            onHandOver = { handingOver = device.id },
        )
    }

    SectionHeader(stringResource(R.string.household_my_targets))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.household_share_targets), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = state.sharesTargets, onCheckedChange = onSharesTargets)
    }
    Text(stringResource(R.string.household_never_shared), style = MaterialTheme.typography.bodySmall)

    Spacer(Modifier.size(8.dp))
    val hubWithOthers = household.isHub && household.devices.any { it.id != household.me?.id }
    if (hubWithOthers) Text(stringResource(R.string.household_hand_over_first), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = onLeave, enabled = !hubWithOthers, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.household_leave))
    }

    removing?.let { id ->
        val name = household.devices.firstOrNull { it.id == id }?.memberName.orEmpty()
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.household_remove_title, name)) },
            text = { Text(stringResource(R.string.household_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    onRemove(id)
                }) { Text(stringResource(R.string.household_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    handingOver?.let { id ->
        val name = household.devices.firstOrNull { it.id == id }?.memberName.orEmpty()
        AlertDialog(
            onDismissRequest = { handingOver = null },
            title = { Text(stringResource(R.string.household_hand_over_title, name)) },
            text = { Text(stringResource(R.string.household_hand_over_text, name)) },
            confirmButton = {
                TextButton(onClick = {
                    handingOver = null
                    onHandOver(id)
                }) { Text(stringResource(R.string.household_hand_over)) }
            },
            dismissButton = { TextButton(onClick = { handingOver = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DeviceRow(
    device: HouseholdDeviceEntity,
    isMe: Boolean,
    canManage: Boolean,
    onApprove: () -> Unit,
    onRemove: () -> Unit,
    onHandOver: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = Color(device.colour), modifier = Modifier.size(14.dp)) {}
                Text(
                    if (isMe) stringResource(R.string.household_me, device.memberName) else device.memberName,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(device.name, style = MaterialTheme.typography.bodySmall)
            if (device.isHub) Text(stringResource(R.string.household_is_hub), style = MaterialTheme.typography.bodySmall)
            if (!device.approved) Text(stringResource(R.string.household_not_confirmed), style = MaterialTheme.typography.bodySmall)
            if (!isMe) {
                device.lastSeenAt?.let {
                    Text(
                        stringResource(R.string.household_device_last_sync, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!device.approved) Button(onClick = onApprove) { Text(stringResource(R.string.household_approve)) }
                    if (device.approved && !device.isHub) TextButton(onClick = onHandOver) { Text(stringResource(R.string.household_make_hub)) }
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.household_remove)) }
                }
            }
        }
    }
}

@Composable
private fun CreateDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.household_create)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.household_name)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.household_next)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** M11.5: shown to the host before hub mode starts. */
@Composable
private fun HubExplanationDialog(onStart: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hub_explain_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.hub_explain_text))
                Text(stringResource(R.string.hub_explain_battery))
                Text(stringResource(R.string.hub_explain_wifi))
                Text(stringResource(R.string.hub_explain_private))
            }
        },
        confirmButton = { TextButton(onClick = onStart) { Text(stringResource(R.string.hub_explain_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CodeDialog(invite: HouseholdInvite, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.household_code_title, invite.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QrCode(HouseholdInvite.encode(invite), stringResource(R.string.household_code_title, invite.name))
                Text(stringResource(R.string.household_code_text), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
