package com.calore.tracker.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.calore.tracker.R

/**
 * Asks for permission to post notifications (Android 13+), with a short explanation first, and
 * only when the user switches on something that needs it (M1.6). Returns the function that
 * starts the request; [onResult] gets true when notifications can be shown.
 */
@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    var explaining by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted && Notifications.canPost(context))
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = {
                explaining = false
                onResult(false)
            },
            title = { Text(stringResource(R.string.notify_permission_title)) },
            text = { Text(stringResource(R.string.notify_permission_text)) },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text(stringResource(R.string.notify_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explaining = false
                    onResult(false)
                }) { Text(stringResource(R.string.not_now)) }
            },
        )
    }

    return {
        when {
            Notifications.canPost(context) -> onResult(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> explaining = true
            // Older Android: notifications were switched off in the system settings.
            else -> onResult(false)
        }
    }
}
