package com.calore.tracker.ui.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calore.tracker.data.repository.BarcodeResult
import com.calore.tracker.data.repository.FoodRepository
import com.calore.tracker.domain.Barcodes
import com.calore.tracker.ui.navigation.dateArg
import com.calore.tracker.ui.navigation.mealArg
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock

sealed interface ScanState {
    data object Scanning : ScanState
    data class LookingUp(val barcode: String) : ScanState
    data class NotFound(val barcode: String) : ScanState
    data class Offline(val barcode: String) : ScanState
    data class Failed(val barcode: String) : ScanState
}

/** Scan or type a barcode, then find the product on the phone or on Open Food Facts (M2.4). */
class ScannerViewModel(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    clock: Clock,
) : ViewModel() {

    val date = savedStateHandle.dateArg(clock)
    val meal = savedStateHandle.mealArg()

    private val state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val uiState: StateFlow<ScanState> = state.asStateFlow()

    private val foundChannel = Channel<Long>(Channel.CONFLATED)
    /** The id of the food to open. */
    val found: Flow<Long> = foundChannel.receiveAsFlow()

    /** From the camera. Codes that aren't product barcodes (such as QR codes) are ignored here. */
    fun onScanned(raw: String) {
        val barcode = Barcodes.normalize(raw) ?: return
        if (state.value != ScanState.Scanning) return
        lookUp(barcode)
    }

    /** From "Type the code"; returns false when the code isn't a valid barcode. */
    fun onTyped(text: String): Boolean {
        val barcode = Barcodes.normalize(text) ?: return false
        lookUp(barcode)
        return true
    }

    fun scanAgain() {
        state.value = ScanState.Scanning
    }

    fun retry() {
        val barcode = when (val current = state.value) {
            is ScanState.Offline -> current.barcode
            is ScanState.Failed -> current.barcode
            else -> return
        }
        lookUp(barcode)
    }

    private fun lookUp(barcode: String) {
        state.value = ScanState.LookingUp(barcode)
        viewModelScope.launch {
            state.value = when (val result = foods.findByBarcode(barcode)) {
                is BarcodeResult.Found -> {
                    foundChannel.send(result.foodId)
                    // Stay busy: the screen is about to close, and the camera may see the code again.
                    ScanState.LookingUp(barcode)
                }
                BarcodeResult.NotFound -> ScanState.NotFound(barcode)
                BarcodeResult.Offline -> ScanState.Offline(barcode)
                BarcodeResult.Failed -> ScanState.Failed(barcode)
            }
        }
    }
}
