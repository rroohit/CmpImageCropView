package com.cmp.image.cropview

/**
 * Aspect-ratio constraints for [ImageCropView]. [PROFILE_CIRCLE] also renders a circular overlay
 * on top of the standard square selection.
 */
public enum class CropType {
    FREE_STYLE,
    SQUARE,
    PROFILE_CIRCLE,
    RATIO_3_2,
    RATIO_4_3,
    RATIO_16_9,
    RATIO_9_16;

    /**
     * Returns the width-to-height ratio as a [Float], or `null` for [FREE_STYLE], [SQUARE],
     * and [PROFILE_CIRCLE] (which use dedicated constraint logic rather than a numeric ratio).
     */
    public fun aspectRatio(): Float? = when (this) {
        RATIO_3_2 -> 3f / 2f
        RATIO_4_3 -> 4f / 3f
        RATIO_16_9 -> 16f / 9f
        RATIO_9_16 -> 9f / 16f
        else -> null
    }
}