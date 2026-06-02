package com.cmp.image

import com.cmp.image.cropview.ImageData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_DIMENSION = 1080

actual suspend fun loadImageFromUrl(url: String): ImageData = suspendCancellableCoroutine { cont ->
    val nsUrl = NSURL.URLWithString(url) ?: run {
        cont.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
        return@suspendCancellableCoroutine
    }

    val delegate = object : NSObject(), NSURLSessionDataDelegateProtocol {
        private val chunks = mutableListOf<ByteArray>()

        @OptIn(ExperimentalForeignApi::class)
        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData
        ) {
            val len = didReceiveData.length.toInt()
            if (len <= 0) return
            val chunk = ByteArray(len)
            chunk.usePinned { pinned ->
                memcpy(pinned.addressOf(0), didReceiveData.bytes, didReceiveData.length)
            }
            chunks.add(chunk)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?
        ) {
            if (didCompleteWithError != null) {
                cont.resumeWithException(RuntimeException(didCompleteWithError.localizedDescription))
                return
            }
            val totalSize = chunks.sumOf { it.size }
            val allBytes = ByteArray(totalSize)
            var pos = 0
            for (chunk in chunks) {
                chunk.copyInto(allBytes, pos)
                pos += chunk.size
            }
            try {
                val (scaledBytes, w, h) = scaleEncodedImage(allBytes, MAX_DIMENSION)
                cont.resume(ImageData(scaledBytes, w, h))
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }
    }

    val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration,
        delegate,
        NSOperationQueue()
    )
    val task = session.dataTaskWithURL(nsUrl)
    cont.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

private fun scaleEncodedImage(bytes: ByteArray, maxDimension: Int): Triple<ByteArray, Int, Int> {
    val image = SkiaImage.makeFromEncoded(bytes)
    val origW = image.width
    val origH = image.height
    if (origW <= maxDimension && origH <= maxDimension) return Triple(bytes, origW, origH)
    val factor = minOf(maxDimension.toFloat() / origW, maxDimension.toFloat() / origH)
    val targetW = (origW * factor).toInt()
    val targetH = (origH * factor).toInt()
    val surface = Surface.makeRasterN32Premul(targetW, targetH)
    surface.canvas.drawImageRect(
        image,
        Rect.makeXYWH(0f, 0f, origW.toFloat(), origH.toFloat()),
        Rect.makeXYWH(0f, 0f, targetW.toFloat(), targetH.toFloat())
    )
    val scaledBytes = surface.makeImageSnapshot().encodeToData()?.bytes ?: bytes
    return Triple(scaledBytes, targetW, targetH)
}