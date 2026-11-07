package com.calore.tracker.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.R
import com.calore.tracker.ui.AppViewModelProvider
import java.util.concurrent.Executors

/** The barcode scanner (M2.4): camera preview, torch, and "Type the code" as a fallback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onFound: (foodId: Long) -> Unit,
    onAddYourself: (barcode: String) -> Unit,
    viewModel: ScannerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var typing by rememberSaveable { mutableStateOf(false) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCamera = granted }

    LaunchedEffect(viewModel) {
        viewModel.found.collect(onFound)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (hasCamera && hasTorch) {
                        TextButton(onClick = { torchOn = !torchOn }) {
                            Text(stringResource(if (torchOn) R.string.scanner_torch_off else R.string.scanner_torch_on))
                        }
                    }
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
                    CameraPreview(
                        torchOn = torchOn,
                        onTorchAvailable = { hasTorch = it },
                        onCode = viewModel::onScanned,
                    )
                }
                Text(stringResource(R.string.scanner_hint), style = MaterialTheme.typography.bodyMedium)
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.scanner_permission_text), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.scanner_permission_allow))
                        }
                    }
                }
            }
            if (state is ScanState.LookingUp) {
                Text(stringResource(R.string.scanner_looking_up), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = { typing = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scanner_type_code))
            }
        }
    }

    when (val current = state) {
        is ScanState.NotFound -> ResultDialog(
            text = stringResource(R.string.scanner_not_found, current.barcode),
            confirm = stringResource(R.string.scanner_add_yourself),
            onConfirm = { onAddYourself(current.barcode) },
            onDismiss = viewModel::scanAgain,
        )
        is ScanState.Offline -> ResultDialog(
            text = stringResource(R.string.scanner_offline),
            confirm = stringResource(R.string.retry),
            onConfirm = viewModel::retry,
            onDismiss = viewModel::scanAgain,
        )
        is ScanState.Failed -> ResultDialog(
            text = stringResource(R.string.online_service_error),
            confirm = stringResource(R.string.retry),
            onConfirm = viewModel::retry,
            onDismiss = viewModel::scanAgain,
        )
        else -> Unit
    }

    if (typing) {
        TypeCodeDialog(
            onDone = { code -> viewModel.onTyped(code).also { valid -> if (valid) typing = false } },
            onDismiss = { typing = false },
        )
    }
}

@Composable
internal fun CameraPreview(torchOn: Boolean, onTorchAvailable: (Boolean) -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, BarcodeAnalyzer { code -> previewView.post { onCode(code) } }) }
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis).also {
            onTorchAvailable(it.cameraInfo.hasFlashUnit())
        }
    }
    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ResultDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.scanner_scan_again)) } },
    )
}

@Composable
private fun TypeCodeDialog(onDone: (String) -> Boolean, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scanner_type_code)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.filter { c -> c.isDigit() || c == ' ' }.take(MAX_CODE_LENGTH)
                    invalid = false
                },
                label = { Text(stringResource(R.string.scanner_code_label)) },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.scanner_code_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { invalid = !onDone(code) }) { Text(stringResource(R.string.scanner_look_up)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MAX_CODE_LENGTH = 16
