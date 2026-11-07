package com.calore.tracker.ui.cycle

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.calore.tracker.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's lock state, provided by the activity. */
val LocalAppLock = staticCompositionLocalOf { AppLockState() }

/**
 * The app lock (M7.6): unlocked with the fingerprint or face, or the phone's PIN, pattern or
 * password. It locks again whenever the app leaves the screen. In memory only.
 */
class AppLockState {
    private val open = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = open.asStateFlow()

    fun unlock() {
        open.value = true
    }

    fun lock() {
        open.value = false
    }
}

object AppLocks {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** A lock needs a screen lock (PIN, pattern, password) or a fingerprint or face on the phone. */
    fun canLock(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the system prompt; [onUnlocked] runs after a fingerprint, face, PIN, pattern or password. */
    fun prompt(activity: FragmentActivity, title: String, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            },
        )
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(AUTHENTICATORS).build())
    }

    fun Context.findActivity(): FragmentActivity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }
}

/** Shown instead of locked content; asks to unlock right away and on the button. */
@Composable
fun LockedContent(state: AppLockState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_title)
    fun ask() {
        with(AppLocks) { context.findActivity() }?.let { AppLocks.prompt(it, title) { state.unlock() } }
    }
    LaunchedEffect(Unit) { ask() }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lock_text), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { ask() }) { Text(stringResource(R.string.lock_unlock)) }
    }
}
