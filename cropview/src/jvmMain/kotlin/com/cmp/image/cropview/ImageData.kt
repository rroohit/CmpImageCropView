package com.cmp.image.cropview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.RenderingHints
import java.awt.image.BufferedImage

public actual class ImageData(
    internal val bufferedImage: BufferedImage
) {
    public actual val width: Int
        get() = bufferedImage.width

    public actual val height: Int
        get() = bufferedImage.height

    public actual fun copy(): ImageData {
        val copy = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        graphics.drawImage(bufferedImage, 0, 0, null)
        graphics.dispose()
        return ImageData(copy)
    }
}

public actual fun cropImage(
    image: ImageData,
    x: Int,
    y: Int,
    width: Int,
    height: Int
): ImageData {
    val croppedImage = image.bufferedImage.getSubimage(x, y, width, height)
    val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()
    graphics.drawImage(croppedImage, 0, 0, null)
    graphics.dispose()
    return ImageData(result)
}

public actual fun scaleImage(
    image: ImageData,
    targetWidth: Int,
    targetHeight: Int
): ImageData {
    val scaledImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics2d = scaledImage.createGraphics()

    graphics2d.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BICUBIC
    )
    graphics2d.setRenderingHint(
        RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY
    )

    graphics2d.drawImage(
        image.bufferedImage,
        0, 0, targetWidth, targetHeight,
        0, 0, image.width, image.height,
        null
    )
    graphics2d.dispose()

    return ImageData(scaledImage)
}

public actual fun ImageData.toImageBitmap(): ImageBitmap = bufferedImage.toComposeImageBitmap()