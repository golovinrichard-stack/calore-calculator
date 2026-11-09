package com.calore.tracker.ui.household

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.household.HouseholdRepository
import com.calore.tracker.data.household.HouseholdState
import com.calore.tracker.data.household.HouseholdSync
import com.calore.tracker.domain.household.HouseholdInvite
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.scanner.CameraPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface JoinState {
    data object Scanning : JoinState
    data object NotACode : JoinState
    data object OtherHousehold : JoinState

    /** [rejoin]: a new code for this phone's household (after the host removed someone or after a restore). */
    data class Found(val invite: HouseholdInvite, val rejoin: Boolean) : JoinState
}

/** Scanning the host's QR code (M11.2, the M2 scanner in QR mode). */
class HouseholdJoinViewModel(
    private val household: HouseholdRepository,
    private val sync: HouseholdSync,
) : ViewModel() {
    private val state = MutableStateFlow<JoinState>(JoinState.Scanning)
    val uiState: StateFlow<JoinState> = state.asStateFlow()

    private val joinedChannel = Channel<Unit>(Channel.CONFLATED)
    val joined: Flow<Unit> = joinedChannel.receiveAsFlow()

    fun onScanned(text: String) {
        if (state.value != JoinState.Scanning) return
        val invite = HouseholdInvite.decode(text)
        if (invite == null) {
            // Product barcodes are ignored quietly; other QR codes get a message.
            if (text.trim().any { !it.isDigit() }) state.value = JoinState.NotACode
            return
        }
        viewModelScope.launch {
            val current = when (val s = household.current()) {
                is HouseholdState.In -> s.household.id
                is HouseholdState.NeedsKey -> s.household.id
                HouseholdState.None -> null
            }
            state.value = when {
                current == null -> JoinState.Found(invite, rejoin = false)
                current == invite.householdId -> JoinState.Found(invite, rejoin = true)
                else -> JoinState.OtherHousehold
            }
        }
    }

    fun scanAgain() {
        state.value = JoinState.Scanning
    }

    fun join(invite: HouseholdInvite, sharesTargets: Boolean) {
        viewModelScope.launch {
            household.join(invite, sharesTargets)
            joinedChannel.send(Unit)
            sync.syncNow()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdJoinScreen(
    onBack: () -> Unit,
    onJoined: () -> Unit,
    viewModel: HouseholdJoinViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCamera = granted }
    LaunchedEffect(viewModel) { viewModel.joined.collect { onJoined() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.household_join)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasCamera) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    CameraPreview(torchOn = false, onTorchAvailable = {}, onCode = viewModel::onScanned)
                }
                Text(stringResource(R.string.household_join_hint), style = MaterialTheme.typography.bodyMedium)
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.household_camera_text), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.scanner_permission_allow))
                        }
                    }
                }
            }
        }
    }

    when (val current = state) {
        JoinState.NotACode -> Message(stringResource(R.string.household_not_a_code), onDismiss = viewModel::scanAgain)
        JoinState.OtherHousehold -> Message(stringResource(R.string.household_other_household), onDismiss = viewModel::scanAgain)
        is JoinState.Found -> JoinDialog(current, onJoin = { shares -> viewModel.join(current.invite, shares) }, onDismiss = viewModel::scanAgain)
        JoinState.Scanning -> Unit
    }
}

@Composable
private fun Message(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.scanner_scan_again)) } },
    )
}

@Composable
private fun JoinDialog(found: JoinState.Found, onJoin: (sharesTargets: Boolean) -> Unit, onDismiss: () -> Unit) {
    var shares by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (found.rejoin) stringResource(R.string.household_rejoin_title, found.invite.name)
                else stringResource(R.string.household_join_title, found.invite.name),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (found.rejoin) {
                    Text(stringResource(R.string.household_rejoin_text))
                } else {
                    Text(stringResource(R.string.household_join_text))
                    Text(stringResource(R.string.household_never_shared))
                    // M11.9: asked once, when joining.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shares, onCheckedChange = { shares = it })
                        Text(stringResource(R.string.household_share_targets), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(shares) }) {
                Text(stringResource(if (found.rejoin) R.string.household_rejoin else R.string.household_join_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
