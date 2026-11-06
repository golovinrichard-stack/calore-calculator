package com.calore.tracker.ui.scanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M2.4: the analyzer decodes real EAN-13 images, upright and sideways, with ZXing. */
class BarcodeAnalyzerTest {

    private val analyzer = BarcodeAnalyzer {}

    /** A grey-scale frame with the barcode drawn by ZXing's own writer, with a quiet zone. */
    private fun frame(code: String, format: BarcodeFormat, width: Int, height: Int): ByteArray {
        val matrix = MultiFormatWriter().encode(code, format, width, height)
        return ByteArray(width * height) { i -> if (matrix[i % width, i / width]) 0 else 255.toByte() }
    }

    @Test
    fun decodesAnUprightEan13() {
        val data = frame("8593807736873", BarcodeFormat.EAN_13, 400, 200)
        assertEquals("8593807736873", analyzer.decodeFrame(data, 400, 400, 200))
    }

    @Test
    fun decodesASidewaysEan13() {
        // Bars running across the frame, as the camera sees a barcode held upright.
        val upright = frame("85936126", BarcodeFormat.EAN_8, 300, 160)
        val sideways = rotateCounterClockwise(upright, 300, 160)
        assertEquals("85936126", analyzer.decodeFrame(sideways, 160, 160, 300))
    }

    @Test
    fun rowPaddingIsRespected() {
        val width = 400
        val height = 200
        val stride = 448
        val data = frame("8593807736873", BarcodeFormat.EAN_13, width, height)
        val padded = ByteArray(stride * height) { i -> if (i % stride < width) data[(i / stride) * width + i % stride] else 0 }
        assertEquals("8593807736873", analyzer.decodeFrame(padded, stride, width, height))
    }

    @Test
    fun anEmptyFrameHasNoCode() {
        assertNull(analyzer.decodeFrame(ByteArray(320 * 240) { 255.toByte() }, 320, 320, 240))
        assertNull("too small for its size", analyzer.decodeFrame(ByteArray(10), 320, 320, 240))
    }

    @Test
    fun rotate90TurnsClockwise() {
        // 3 × 2:  1 2 3      rotated (2 × 3):  4 1
        //         4 5 6                        5 2
        //                                      6 3
        val data = byteArrayOf(1, 2, 3, 4, 5, 6)
        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), BarcodeAnalyzer.rotate90(data, 3, 3, 2))
    }

    private fun rotateCounterClockwise(data: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) out[(width - 1 - x) * height + y] = data[y * width + x]
        return out
    }
}
