package com.cmp.image

import com.cmp.image.cropview.ImageData
import kotlinx.coroutines.await
import org.jetbrains.skia.Image as SkiaImage
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

// Declare the browser global fetch directly — its init parameter is optional in JS,
// bypassing the kotlin-browser Window.fetch overload that requires init.
private external fun fetch(url: String): Promise<dynamic>

actual suspend fun loadImageFromUrl(url: String): ImageData {
    return try {
        val response = fetch(url).await()
        @Suppress("UNCHECKED_CAST")
        val buffer = (response.arrayBuffer() as Promise<ArrayBuffer>).await()
        val arr = Int8Array(buffer)
        // asDynamic()[i] bypasses the missing operator get in newer kotlin-browser
        val bytes = ByteArray(arr.length) { i -> (arr.asDynamic()[i] as Int).toByte() }
        val skiaImage = SkiaImage.makeFromEncoded(bytes)
        ImageData(skiaImage.width, skiaImage.height, bytes)
    } catch (_: Throwable) {
        ImageData(800, 600)
    }
}
