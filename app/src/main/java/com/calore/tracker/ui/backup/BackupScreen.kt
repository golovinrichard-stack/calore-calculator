package com.calore.tracker.ui.backup

import android.content.ActivityNotFoundException
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.data.backup.BackupCounts
import com.calore.tracker.ui.AppViewModelProvider
import com.calore.tracker.ui.components.SectionHeader
import com.calore.tracker.ui.formatCount
import com.calore.tracker.ui.rememberMediumDateFormatter
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    // Any file can be picked: storage apps label .json files inconsistently, and every file is checked anyway.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message.text(resources))
        }
    }

    fun launchSafely(launch: () -> Unit, failure: BackupMessage) {
        try {
            launch()
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(failure.text(resources)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.work?.let { work ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(work.labelRes()), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            SectionHeader(stringResource(R.string.backup_export_title))
            Text(stringResource(R.string.backup_export_body), style = MaterialTheme.typography.bodyMedium)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Text(stringResource(R.string.backup_export_warning), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                stringResource(R.string.backup_not_included),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { launchSafely({ exportLauncher.launch(viewModel.defaultFileName()) }, BackupMessage.ExportFailed) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_export_button))
            }

            SectionHeader(stringResource(R.string.backup_import_title), modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.backup_import_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { launchSafely({ importLauncher.launch(arrayOf(ANY_MIME_TYPE)) }, BackupMessage.ReadFailed) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_import_button))
            }
        }
    }

    state.pendingImport?.let { pending ->
        ConfirmImportDialog(
            pending = pending,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }
}

@Composable
private fun ConfirmImportDialog(pending: PendingImport, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val dateFormatter = rememberMediumDateFormatter()
    val counts = pending.counts
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val date = pending.exportedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(dateFormatter)
                Text(
                    if (date != null) {
                        stringResource(R.string.backup_confirm_contents_date, date)
                    } else {
                        stringResource(R.string.backup_confirm_contents)
                    },
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(pluralStringResource(R.plurals.backup_count_foods, counts.foods, formatCount(counts.foods)))
                    Text(pluralStringResource(R.plurals.backup_count_diary_entries, counts.diaryEntries, formatCount(counts.diaryEntries)))
                    Text(pluralStringResource(R.plurals.backup_count_weights, counts.weightEntries, formatCount(counts.weightEntries)))
                }
                Text(stringResource(R.string.backup_confirm_warning), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_confirm_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun BackupWork.labelRes(): Int = when (this) {
    BackupWork.EXPORTING -> R.string.backup_working_export
    BackupWork.READING -> R.string.backup_working_read
    BackupWork.IMPORTING -> R.string.backup_working_import
}

private fun BackupMessage.text(resources: Resources): String = when (this) {
    is BackupMessage.Exported -> resources.getString(R.string.backup_exported, counts.summary(resources))
    is BackupMessage.Imported -> resources.getString(R.string.backup_imported, counts.summary(resources))
    BackupMessage.ExportFailed -> resources.getString(R.string.backup_error_export)
    BackupMessage.ReadFailed -> resources.getString(R.string.backup_error_read)
    BackupMessage.NotABackup -> resources.getString(R.string.backup_error_not_backup)
    BackupMessage.NewerVersion -> resources.getString(R.string.backup_error_newer)
    BackupMessage.Damaged -> resources.getString(R.string.backup_error_damaged)
    BackupMessage.ImportFailed -> resources.getString(R.string.backup_error_import)
}

private fun BackupCounts.summary(resources: Resources): String = resources.getString(
    R.string.backup_counts_summary,
    resources.getQuantityString(R.plurals.backup_count_foods, foods, formatCount(foods)),
    resources.getQuantityString(R.plurals.backup_count_diary_entries, diaryEntries, formatCount(diaryEntries)),
    resources.getQuantityString(R.plurals.backup_count_weights, weightEntries, formatCount(weightEntries)),
)

private const val JSON_MIME_TYPE = "application/json"
private const val ANY_MIME_TYPE = "*/*"
