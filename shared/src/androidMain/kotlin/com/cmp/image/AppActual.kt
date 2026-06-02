package com.cmp.image

import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.cmp.image.cropview.ImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_DIMENSION = 1080

actual suspend fun loadImageFromUrl(url: String): ImageData = withContext(Dispatchers.IO) {
    val bytes = fetchBytes(url)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val scaled = scaleToMaxDimension(bitmap, MAX_DIMENSION)
    ImageData(scaled)
}

private fun fetchBytes(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.instanceFollowRedirects = true
    return connection.inputStream.use { it.readBytes() }
}

private fun scaleToMaxDimension(
    bitmap: android.graphics.Bitmap,
    maxDimension: Int,
): android.graphics.Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDimension && h <= maxDimension) return bitmap
    val factor = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
    return bitmap.scale((w * factor).toInt(), (h * factor).toInt())
}
