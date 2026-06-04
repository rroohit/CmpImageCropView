package com.cmp.image.cropview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect

public actual class ImageData(
    private val _width: Int = 0,
    private val _height: Int = 0,
    internal val encodedBytes: ByteArray = ByteArray(0),
) {
    public actual val width: Int get() = _width
    public actual val height: Int get() = _height
    public actual fun copy(): ImageData = ImageData(_width, _height, encodedBytes)
}

public actual fun cropImage(image: ImageData, x: Int, y: Int, width: Int, height: Int): ImageData {
    if (image.encodedBytes.isEmpty()) return ImageData(width, height)
    val src = SkiaImage.makeFromEncoded(image.encodedBytes) ?: return ImageData(width, height)
    val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
    Canvas(bitmap).drawImageRect(
        src,
        Rect.makeLTRB(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat()),
        Rect.makeWH(width.toFloat(), height.toFloat()),
    )
    val encoded = SkiaImage.makeFromBitmap(bitmap).encodeToData()?.bytes ?: return ImageData(width, height)
    return ImageData(width, height, encoded)
}

public actual fun scaleImage(image: ImageData, targetWidth: Int, targetHeight: Int): ImageData {
    if (image.encodedBytes.isEmpty()) return ImageData(targetWidth, targetHeight)
    val src = SkiaImage.makeFromEncoded(image.encodedBytes) ?: return ImageData(targetWidth, targetHeight)
    val bitmap = Bitmap().apply { allocN32Pixels(targetWidth, targetHeight) }
    Canvas(bitmap).drawImageRect(
        src,
        Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
        Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
    )
    val encoded = SkiaImage.makeFromBitmap(bitmap).encodeToData()?.bytes ?: return ImageData(targetWidth, targetHeight)
    return ImageData(targetWidth, targetHeight, encoded)
}

public actual fun ImageData.toImageBitmap(): ImageBitmap {
    if (encodedBytes.isEmpty()) return ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    val src = SkiaImage.makeFromEncoded(encodedBytes)
        ?: return ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    val bitmap = Bitmap().apply { allocN32Pixels(src.width, src.height) }
    Canvas(bitmap).drawImage(src, 0f, 0f)
    return bitmap.asComposeImageBitmap()
}