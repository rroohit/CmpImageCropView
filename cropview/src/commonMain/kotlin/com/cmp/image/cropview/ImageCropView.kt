package com.cmp.image.cropview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt


// ─────────────────────────────────────────────────────────────────────────────
// State holder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * State holder for [ImageCropView]. Holds the crop rectangle, zoom level, and pan offset.
 * Always obtain via [rememberSaveableImageCrop] (rotation-safe) or [rememberImageCrop].
 *
 * ```kotlin
 * val imageCrop = rememberSaveableImageCrop(imageData)
 * ImageCropView(imageCrop = imageCrop, cropType = CropType.SQUARE)
 * val result: ImageData = imageCrop.onCrop()
 * ```
 */
public class ImageCrop(
    internal var imageData: ImageData
) : OnCrop {

    /** Internal state holder that owns all mutable crop geometry and zoom state. */
    internal val cropUtil: CropUtil = CropUtil(imageData)

    override fun onCrop(cropSourceImage: Boolean): ImageData {
        cropUtil.updateBitmapImage(imageData)
        return if (cropSourceImage) cropUtil.cropSourceImage() else cropUtil.cropImage()
    }

    override fun resetView() {
        cropUtil.resetZoom()
        cropUtil.resetCropIRect()
    }

    public companion object {
        /**
         * A [Saver] that persists the crop rectangle, zoom, and pan across configuration changes
         * (e.g. rotation). Pass to [rememberSaveable] or use [rememberSaveableImageCrop] directly.
         */
        public fun saver(imageData: ImageData): Saver<ImageCrop, Any> = Saver(
            save = { imageCrop -> imageCrop.cropUtil.toSnapshot() },
            restore = { snapshot ->
                ImageCrop(imageData).also { imageCrop ->
                    if (snapshot is CropStateSnapshot && snapshot.bitmapWidth == imageData.width && snapshot.bitmapHeight == imageData.height) {
                        imageCrop.cropUtil.pendingSnapshot = snapshot
                    }
                }
            }
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Factory composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates and [remember]s an [ImageCrop] instance for [imageData].
 *
 * The instance is recreated whenever [imageData] changes (e.g. the user loads a new image).
 * Crop state is **not** preserved across Activity recreation; use [rememberSaveableImageCrop]
 * for rotation-safe state.
 */
@Composable
public fun rememberImageCrop(imageData: ImageData): ImageCrop =
    remember(imageData) { ImageCrop(imageData) }

/**
 * Creates and remembers an [ImageCrop] whose state (crop rect, zoom, pan) survives
 * rotation and Activity recreation. Resets automatically when [imageData] changes.
 */
@Composable
public fun rememberSaveableImageCrop(imageData: ImageData): ImageCrop =
    rememberSaveable(imageData, saver = ImageCrop.saver(imageData)) { ImageCrop(imageData) }


// ─────────────────────────────────────────────────────────────────────────────
// Composable view
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders an interactive image crop UI driven by [imageCrop].
 *
 * The user can drag the crop rectangle's corners and body, and — when [enableZoom] is `true` —
 * pinch-zoom and double-tap the image. Call [OnCrop.onCrop] on [imageCrop] to retrieve the result.
 *
 * @param cropType  Aspect-ratio constraint; see [CropType].
 * @param edgeType  Corner handle style; see [EdgeType].
 * @param enableZoom  Enables pinch-to-zoom and double-tap zoom on the image.
 */
@Composable
public fun ImageCropView(
    imageCrop: ImageCrop,
    modifier: Modifier = Modifier,
    guideLineColor: Color = Color(0xFFD1CBE2),
    guideLineWidth: Dp = 2.dp,
    edgeCircleSize: Dp = 8.dp,
    showGuideLines: Boolean = true,
    cropType: CropType = CropType.FREE_STYLE,
    edgeType: EdgeType = EdgeType.CIRCULAR,
    enableZoom: Boolean = false
) {
    val cropUtil = imageCrop.cropUtil

    if (cropUtil.cropType == null || cropType != cropUtil.cropType) {
        cropUtil.updateCropType(cropType)
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val imageAspectRatio = imageCrop.imageData.width.toFloat() / imageCrop.imageData.height.toFloat()
        val containerAspectRatio = maxWidth.value / maxHeight.value

        val (targetWidth, targetHeight) = if (imageAspectRatio > containerAspectRatio) {
            // Image is wider than container — fit by width
            maxWidth to (maxWidth / imageAspectRatio)
        } else {
            // Image is taller than container — fit by height
            (maxHeight * imageAspectRatio) to maxHeight
        }

        Canvas(
            modifier = Modifier
                .size(width = targetWidth, height = targetHeight)
                .onSizeChanged { intSize ->
                    cropUtil.onCanvasSizeChanged(intSize = intSize)
                }
                .pointerInput(imageCrop, enableZoom) {
                    var lastTapTime = 0L
                    var lastTapPosition = Offset.Zero

                    if (!enableZoom) {
                        detectDragGestures(
                            onDragStart = { touchPoint ->
                                cropUtil.onDragStart(touchPoint)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                cropUtil.onDrag(change.position)
                            },
                            onDragEnd = {
                                cropUtil.onDragEnd()
                            }
                        )
                    } else {
                        val doubleTapThresholdMs = 300L
                        val doubleTapRadiusPx = 80f

                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            firstDown.consume()

                            val downTime = getCurrentTimeMillis()
                            val downPosition = firstDown.position

                            val touchOnCropRect = cropUtil.isTouchOnCropRect(downPosition)
                            val zoomed = cropUtil.zoomScale > 1.0f

                            var gestureMode: Int
                            when {
                                touchOnCropRect -> {
                                    cropUtil.onDragStart(downPosition)
                                    gestureMode = 1
                                }
                                zoomed -> {
                                    cropUtil.onImagePanStart(downPosition)
                                    gestureMode = 2
                                }
                                else -> gestureMode = 0
                            }

                            var hasMoved = false
                            var zoomInitialized = false
                            var previousCentroid = Offset.Zero
                            var previousCentroidDistance = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes

                                if (changes.all { !it.pressed }) {
                                    when (gestureMode) {
                                        1 -> cropUtil.onDragEnd()
                                        2 -> cropUtil.onImagePanEnd()
                                    }

                                    if (!hasMoved && gestureMode != 3) {
                                        val upTime = getCurrentTimeMillis()
                                        if ((upTime - downTime) < doubleTapThresholdMs) {
                                            val timeSinceLast = upTime - lastTapTime
                                            val d = downPosition - lastTapPosition
                                            val distFromLast = sqrt(d.x * d.x + d.y * d.y)
                                            if (timeSinceLast < doubleTapThresholdMs &&
                                                distFromLast < doubleTapRadiusPx
                                            ) {
                                                cropUtil.onDoubleTapZoom(downPosition)
                                                lastTapTime = 0L
                                            } else {
                                                lastTapTime = upTime
                                                lastTapPosition = downPosition
                                            }
                                        }
                                    }
                                    break
                                }

                                val activeChanges = changes.filter { it.pressed }
                                val fingerCount = activeChanges.size

                                if (fingerCount >= 2) {
                                    if (gestureMode == 1) cropUtil.onDragEnd()
                                    if (gestureMode == 2) cropUtil.onImagePanEnd()
                                    gestureMode = 3

                                    val currentPositions = activeChanges.map { it.position }
                                    val currentCentroid = calculateCentroid(currentPositions)
                                    val currentDistance = calculateAverageDistance(
                                        currentPositions, currentCentroid
                                    )

                                    if (!zoomInitialized) {
                                        zoomInitialized = true
                                        previousCentroid = currentCentroid
                                        previousCentroidDistance = currentDistance
                                    } else {
                                        val scaleChange = if (previousCentroidDistance > 0f) {
                                            currentDistance / previousCentroidDistance
                                        } else 1f
                                        val panChange = currentCentroid - previousCentroid
                                        cropUtil.onZoomChange(currentCentroid, scaleChange, panChange)
                                        previousCentroid = currentCentroid
                                        previousCentroidDistance = currentDistance
                                    }
                                    activeChanges.forEach { it.consume() }

                                } else if (fingerCount == 1) {
                                    val change = activeChanges.first()

                                    if (gestureMode == 3) {
                                        zoomInitialized = false
                                        change.consume()
                                        continue
                                    }

                                    if (change.positionChanged()) {
                                        hasMoved = true
                                        when (gestureMode) {
                                            1 -> { cropUtil.onDrag(change.position); change.consume() }
                                            2 -> { cropUtil.onImagePanDrag(change.position); change.consume() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },

            onDraw = {
                clipRect {
                    withTransform({
                        translate(left = cropUtil.zoomOffset.x, top = cropUtil.zoomOffset.y)
                        scale(
                            scaleX = cropUtil.zoomScale,
                            scaleY = cropUtil.zoomScale,
                            pivot = Offset(size.width / 2f, size.height / 2f)
                        )
                    }) {
                        drawImageData(imageCrop.imageData)
                    }
                }

                if (cropType == CropType.PROFILE_CIRCLE) {
                    val circleRadius = cropUtil.iRect.size.width / 2f
                    val circlePath = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center = Offset(
                                    cropUtil.iRect.topLeft.x + circleRadius,
                                    cropUtil.iRect.topLeft.y + circleRadius
                                ),
                                radius = circleRadius - guideLineWidth.toPx()
                            )
                        )
                    }
                    clipPath(circlePath, clipOp = ClipOp.Difference) {
                        drawRect(SolidColor(Color.Black.copy(alpha = 0.5f)))
                    }
                }

                drawCropRectangleView(
                    guideLineColor = guideLineColor,
                    guideLineWidth = guideLineWidth,
                    iRect = cropUtil.iRect
                )

                if (showGuideLines) {
                    drawGuideLines(
                        noOfGuideLines = 2,
                        guideLineColor = guideLineColor,
                        guideLineWidth = guideLineWidth,
                        iRect = cropUtil.iRect
                    )
                }

                if (edgeType == EdgeType.CIRCULAR) {
                    drawCircularEdges(
                        edgeCircleSize = edgeCircleSize,
                        guideLineColor = guideLineColor,
                        iRect = cropUtil.iRect
                    )
                } else {
                    drawSquareBrackets(
                        guideLineColor = guideLineColor,
                        guideLineWidthGiven = guideLineWidth,
                        iRect = cropUtil.iRect
                    )
                }
            }
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Interface
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Provides imperative access to the crop operation and view reset.
 * Implemented by [ImageCrop].
 */
public interface OnCrop {
    /**
     * Performs the crop and returns the resulting [ImageData].
     *
     * @param cropSourceImage When `true` (default), maps the crop rect back to source-image
     *                        coordinates and crops at full resolution. When `false`, crops the
     *                        canvas-scaled version (lower quality, use only if you need canvas-pixel output).
     */
    public fun onCrop(cropSourceImage: Boolean = true): ImageData

    /** Resets the crop rectangle and zoom to their initial state. */
    public fun resetView()
}


// ─────────────────────────────────────────────────────────────────────────────
// Private geometry helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun calculateCentroid(positions: List<Offset>): Offset {
    if (positions.isEmpty()) return Offset.Zero
    var sumX = 0f
    var sumY = 0f
    for (pos in positions) { sumX += pos.x; sumY += pos.y }
    return Offset(sumX / positions.size, sumY / positions.size)
}

private fun calculateAverageDistance(positions: List<Offset>, centroid: Offset): Float {
    if (positions.size < 2) return 0f
    var total = 0f
    for (pos in positions) {
        val dx = pos.x - centroid.x
        val dy = pos.y - centroid.y
        total += sqrt(dx * dx + dy * dy)
    }
    return total / positions.size
}