package com.cmp.image

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.cmp.image.cropview.ImageData
import com.cmp.image.demo.CropDemoScreen

expect suspend fun loadImageFromUrl(url: String): ImageData

@Composable
fun App() {
    MaterialTheme {
        CropDemoScreen()
    }
}