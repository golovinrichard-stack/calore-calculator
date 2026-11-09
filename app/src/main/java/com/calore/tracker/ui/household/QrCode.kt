package com.calore.tracker.ui.household

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code made on the phone with ZXing (M11.2); black on white so every camera reads it. */
@Composable
fun QrCode(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text) }
    Image(bitmap = bitmap, contentDescription = contentDescription, filterQuality = FilterQuality.None, modifier = modifier.size(260.dp))
}

private fun qrBitmap(text: String): ImageBitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val bitmap = createBitmap(matrix.width, matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) bitmap[x, y] = if (matrix[x, y]) BLACK else WHITE
    }
    return bitmap.asImageBitmap()
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
