package com.calore.tracker.ui.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes barcodes from camera frames with ZXing (M2.4): EAN-13, EAN-8, UPC-A, UPC-E, and QR
 * codes for the household (M11). Frames are only read in memory, never stored or sent.
 */
class BarcodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.QR_CODE,
                ),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            decodeFrame(data, plane.rowStride, it.width, it.height)?.let(onCode)
        }
    }

    /** A luminance frame, as is and turned: the sensor image is usually sideways to the user. */
    internal fun decodeFrame(data: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        if (!fits(data, rowStride, width, height)) return null
        return decode(data, rowStride, width, height) ?: decode(rotate90(data, rowStride, width, height), height, height, width)
    }

    private fun fits(data: ByteArray, rowStride: Int, width: Int, height: Int): Boolean =
        width > 0 && height > 0 && rowStride >= width && data.size >= rowStride * (height - 1) + width

    private fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    companion object {
        /** The luminance plane turned 90° clockwise; the result has no row padding. */
        fun rotate90(data: ByteArray, rowStride: Int, width: Int, height: Int): ByteArray {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                val row = y * rowStride
                for (x in 0 until width) {
                    rotated[x * height + (height - 1 - y)] = data[row + x]
                }
            }
            return rotated
        }
    }
}
