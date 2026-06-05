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
) {
    /**
     * Flattens this snapshot into a list of Bundle-compatible primitives so it can be persisted
     * by Compose's `SaveableStateRegistry` (which on Android only accepts Bundle-storable types,
     * not arbitrary data classes). Paired with [fromList]; keep the ordering in sync.
     */
    fun toList(): List<Any> = listOf(
        topLeftXRatio,
        topLeftYRatio,
        widthRatio,
        heightRatio,
        zoomScale,
        zoomOffsetXRatio,
        zoomOffsetYRatio,
        bitmapWidth,
        bitmapHeight
    )

    companion object {
        /** Reconstructs a snapshot from the primitive list produced by [toList]. */
        fun fromList(values: List<*>): CropStateSnapshot = CropStateSnapshot(
            topLeftXRatio = values[0] as Float,
            topLeftYRatio = values[1] as Float,
            widthRatio = values[2] as Float,
            heightRatio = values[3] as Float,
            zoomScale = values[4] as Float,
            zoomOffsetXRatio = values[5] as Float,
            zoomOffsetYRatio = values[6] as Float,
            bitmapWidth = values[7] as Int,
            bitmapHeight = values[8] as Int
        )
    }
}