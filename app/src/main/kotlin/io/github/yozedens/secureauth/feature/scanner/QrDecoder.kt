package io.github.yozedens.secureauth.feature.scanner

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.IOException

/** ZXing QR decoding for camera frames and picked images (design §16). Local only. */
object QrDecoder {

    private const val MAX_IMAGE_SIDE = 2048

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    /** Decodes the luminance (Y) plane of a camera frame. Does not close [image]. */
    fun decode(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val source = PlanarYUVLuminanceSource(
            data,
            plane.rowStride,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        return decode(source)
    }

    /** Result of decoding a picked image. */
    sealed interface ImageResult {
        data class Found(val text: String) : ImageResult
        data object NoCode : ImageResult
        data object Unreadable : ImageResult
    }

    /** Decodes a picked image, downscaled so large screenshots do not exhaust memory. */
    fun decode(resolver: ContentResolver, uri: Uri): ImageResult {
        val bitmap = try {
            loadScaled(resolver, uri)
        } catch (ignored: IOException) {
            null
        } catch (ignored: SecurityException) {
            null
        } ?: return ImageResult.Unreadable
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val text = decode(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
            return if (text != null) ImageResult.Found(text) else ImageResult.NoCode
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(source: LuminanceSource): String? =
        try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (ignored: ReaderException) {
            null
        }

    private fun loadScaled(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
