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
import com.calore.tracker.R
import com.calore.tracker.ui.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val perAppLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
}
