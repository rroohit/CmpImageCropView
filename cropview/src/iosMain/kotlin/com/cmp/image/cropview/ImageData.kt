package com.cmp.image.cropview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

public actual class ImageData(
    internal val encodedBytes: ByteArray,
    public actual val width: Int,
    public actual val height: Int
) {
    public actual fun copy(): ImageData = ImageData(encodedBytes.copyOf(), width, height)
}

public actual fun cropImage(image: ImageData, x: Int, y: Int, width: Int, height: Int): ImageData {
    val src = SkiaImage.makeFromEncoded(image.encodedBytes)
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.drawImageRect(
        src,
        Rect.makeXYWH(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()),
        Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat())
    )
    val data = surface.makeImageSnapshot().encodeToData()?.bytes ?: image.encodedBytes
    return ImageData(data, width, height)
}

public actual fun scaleImage(image: ImageData, targetWidth: Int, targetHeight: Int): ImageData {
    val src = SkiaImage.makeFromEncoded(image.encodedBytes)
    val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
    surface.canvas.drawImageRect(
        src,
        Rect.makeXYWH(0f, 0f, src.width.toFloat(), src.height.toFloat()),
        Rect.makeXYWH(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
    )
    val scaled = surface.makeImageSnapshot()
    val data = scaled.encodeToData()?.bytes ?: image.encodedBytes
    return ImageData(data, targetWidth, targetHeight)
}

public actual fun ImageData.toImageBitmap(): ImageBitmap =
    SkiaImage.makeFromEncoded(encodedBytes).toComposeImageBitmap()