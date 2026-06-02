package com.cmp.image

import com.cmp.image.cropview.ImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.imageio.ImageIO

private const val MAX_DIMENSION = 1080

actual suspend fun loadImageFromUrl(url: String): ImageData = withContext(Dispatchers.IO) {
    val bytes = fetchBytes(url)
    val img = ImageIO.read(ByteArrayInputStream(bytes))
    val scaled = scaleToMaxDimension(img, MAX_DIMENSION)
    ImageData(scaled)
}

private fun fetchBytes(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.instanceFollowRedirects = true
    return connection.inputStream.use { it.readBytes() }
}

private fun scaleToMaxDimension(img: BufferedImage, maxDimension: Int): BufferedImage {
    val w = img.width
    val h = img.height
    if (w <= maxDimension && h <= maxDimension) return img
    val factor = minOf(maxDimension.toDouble() / w, maxDimension.toDouble() / h)
    val nw = (w * factor).toInt()
    val nh = (h * factor).toInt()
    val result = BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB)
    val g = result.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.drawImage(img, 0, 0, nw, nh, null)
    g.dispose()
    return result
}
