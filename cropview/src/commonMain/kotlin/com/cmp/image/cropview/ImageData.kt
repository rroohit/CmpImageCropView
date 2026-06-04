package com.cmp.image.cropview

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-agnostic container for image data. Holds pixel dimensions and the underlying
 * platform image (Bitmap on Android, BufferedImage on JVM, encoded bytes on iOS/web).
 */
public expect class ImageData {
    public val width: Int
    public val height: Int

    public fun copy(): ImageData
}

/** Crops a rectangular region from [image] and returns it as a new [ImageData]. */
public expect fun cropImage(
    image: ImageData,
    x: Int,
    y: Int,
    width: Int,
    height: Int
): ImageData

/** Scales [image] to the given dimensions and returns it as a new [ImageData]. */
public expect fun scaleImage(
    image: ImageData,
    targetWidth: Int,
    targetHeight: Int
): ImageData

/** Converts this image to a Compose [ImageBitmap] for rendering. */
public expect fun ImageData.toImageBitmap(): ImageBitmap