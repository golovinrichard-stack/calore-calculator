package com.calore.tracker.ui.scanner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.calore.tracker.R

/** Whether a scanning screen may use the camera, and how to ask for it. */
@Stable
class CameraAccess internal constructor(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set

    /** Android shows no prompt any more (the user said no twice, or "Don't ask again"): only the settings can allow it. */
    var blocked by mutableStateOf(false)
        internal set

    internal var launch: () -> Unit = {}

    fun request() = launch()
}

/**
 * Asks for the camera the first time the screen opens, and again on the button. When Android
 * won't show the prompt any more, [CameraAccess.blocked] is true and the screen offers the app's
 * settings page instead. Coming back from the settings reads the permission again.
 */
@Composable
fun rememberCameraAccess(): CameraAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val access = remember { CameraAccess(hasCamera(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access.granted = granted
        access.blocked = !granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    access.launch = {
        askedOnce = true
        launcher.launch(Manifest.permission.CAMERA)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access.granted = hasCamera(context)
        if (access.granted) access.blocked = false
    }
    LaunchedEffect(Unit) {
        if (!access.granted && !askedOnce) access.request()
    }
    return access
}

/** Shown instead of the camera picture: why the camera is needed, and the button that fixes it. */
@Composable
fun CameraPermissionCard(text: String, access: CameraAccess, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (access.blocked) {
                Text(stringResource(R.string.camera_blocked_text), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.open_settings)) }
            } else {
                Button(onClick = access::request) { Text(stringResource(R.string.scanner_permission_allow)) }
            }
        }
    }
}

private fun hasCamera(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The system page where a screen lock (PIN, pattern, password, fingerprint or face) is set up. */
fun openSecuritySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** The system page of this app, where its permissions are switched on. */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
