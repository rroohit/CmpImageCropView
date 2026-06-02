package com.cmp.image.cropview

/**
 * Immutable snapshot of [ImageCrop] interactive state, serializable for persistence.
 *
 * All spatial values are stored as normalized ratios (0..1) relative to the canvas dimensions
 * at the time of capture — canvas-size-independent so the snapshot scales correctly when
 * restored onto a canvas with different dimensions (e.g. after an orientation change).
 *
 * [bitmapWidth]/[bitmapHeight] are used to verify the snapshot belongs to the same image;
 * a mismatch discards the snapshot and resets to defaults.
 */
internal data class CropStateSnapshot(
    val topLeftXRatio: Float,
    val topLeftYRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val zoomScale: Float,
    val zoomOffsetXRatio: Float,
    val zoomOffsetYRatio: Float,
    val bitmapWidth: Int,
    val bitmapHeight: Int
)