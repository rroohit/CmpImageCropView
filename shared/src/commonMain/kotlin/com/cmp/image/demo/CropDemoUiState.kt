package com.cmp.image.demo

import com.cmp.image.cropview.CropType
import com.cmp.image.cropview.ImageData

data class CropDemoUiState(
    val displayImage: ImageData? = null,
    val isLoading: Boolean = true,
    val cropType: CropType = CropType.FREE_STYLE,
    val croppedImages: List<ImageData> = emptyList(),
    val showResults: Boolean = false,
    val previewImage: ImageData? = null,
)