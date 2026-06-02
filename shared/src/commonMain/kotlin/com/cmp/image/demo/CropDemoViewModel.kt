package com.cmp.image.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmp.image.cropview.CropType
import com.cmp.image.cropview.ImageData
import com.cmp.image.loadImageFromUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DEMO_IMAGE_URLS = listOf(
    "https://picsum.photos/seed/nature/1200/800",
    "https://picsum.photos/seed/city/1200/800",
    "https://picsum.photos/seed/animals/800/1200",
    "https://picsum.photos/seed/landscape/1200/800",
    "https://picsum.photos/seed/portrait/800/1200",
)

class CropDemoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CropDemoUiState())
    val uiState: StateFlow<CropDemoUiState> = _uiState.asStateFlow()

    private var currentUrlIndex = 0

    init {
        loadImage(DEMO_IMAGE_URLS[currentUrlIndex])
    }

    fun loadNextImage() {
        currentUrlIndex = (currentUrlIndex + 1) % DEMO_IMAGE_URLS.size
        loadImage(DEMO_IMAGE_URLS[currentUrlIndex])
    }

    fun saveCrop(image: ImageData) {
        _uiState.update { it.copy(croppedImages = it.croppedImages + image) }
    }

    fun setCropType(cropType: CropType) {
        _uiState.update { it.copy(cropType = cropType) }
    }

    fun showResults() = _uiState.update { it.copy(showResults = true) }
    fun hideResults() = _uiState.update { it.copy(showResults = false) }

    fun openPreview(image: ImageData) = _uiState.update { it.copy(previewImage = image) }
    fun closePreview() = _uiState.update { it.copy(previewImage = null) }

    private fun loadImage(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val image = loadImageFromUrl(url)
                _uiState.update { it.copy(displayImage = image, isLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}