package com.cmp.image.cropview

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

public actual class ImageData(
    internal val bitmap: Bitmap
) {
    public actual val width: Int
        get() = bitmap.width

    public actual val height: Int
        get() = bitmap.height

    public actual fun copy(): ImageData {
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        return ImageData(bitmap.copy(config, true))
    }
}

public actual fun cropImage(
    image: ImageData,
    x: Int,
    y: Int,
    width: Int,
    height: Int
): ImageData {
    val croppedBitmap = Bitmap.createBitmap(image.bitmap, x, y, width, height)
    return ImageData(croppedBitmap)
}

public actual fun scaleImage(
    image: ImageData,
    targetWidth: Int,
    targetHeight: Int
): ImageData {
    val scaledBitmap = image.bitmap.scale(targetWidth, targetHeight, false)
    return ImageData(scaledBitmap)
}

public actual fun ImageData.toImageBitmap(): ImageBitmap = bitmap.asImageBitmap()