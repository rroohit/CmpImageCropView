package com.cmp.image

import com.cmp.image.cropview.ImageData

// WasmJS image loading requires a @JsFun wrapper or Ktor-client to bridge the JS/Wasm boundary.
actual suspend fun loadImageFromUrl(url: String): ImageData = ImageData(800, 600)