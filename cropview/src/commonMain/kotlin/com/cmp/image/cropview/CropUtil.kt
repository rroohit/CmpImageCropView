package com.cmp.image.cropview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class CropUtil(private var mImageData: ImageData) {

    internal var cropType: CropType? = null
    private var imageData: ImageData? = mImageData

    internal var canvasSize: CanvasSize by mutableStateOf(CanvasSize())
    internal var iRect: IRect by mutableStateOf(IRect())

    private var touchRect: IRect by mutableStateOf(IRect())
    private var isTouchedInsideRectMove: Boolean by mutableStateOf(false)
    private var rectEdgeTouched: RectEdge by mutableStateOf(RectEdge.NULL)
    private var irectTopleft: Offset by mutableStateOf(Offset(0.0f, 0.0f))
    private var touchAreaRectTopLeft: Offset by mutableStateOf(Offset(0.0f, 0.0f))

    private val paddingForTouchRect = 70F
    private val minLimit: Float = paddingForTouchRect * 3F

    private var maxSquareLimit: Float = 0F
        set(value) {
            minSquareLimit = value * 0.2F
            field = value
        }
    private var minSquareLimit: Float = maxSquareLimit * 0.3F

    /** Current zoom scale factor. 1.0 = no zoom. */
    internal var zoomScale: Float by mutableFloatStateOf(1.0f)
        private set

    /** Current pan offset of the zoomed image, in canvas coordinates. */
    internal var zoomOffset: Offset by mutableStateOf(Offset.Zero)
        private set

    private val minZoom: Float = 1.0f
    private val maxZoom: Float = 5.0f

    private var lastPointUpdated: Offset? = null

    init {
        resetCropIRect()
    }

    /**
     * Snapshot set by [rememberSaveableImageCrop]'s Saver restore path before any canvas
     * layout has occurred. Consumed and cleared on the first [onCanvasSizeChanged] call.
     */
    internal var pendingSnapshot: CropStateSnapshot? = null

    /**
     * Cache of the last valid snapshot. Guards against the Saver's safe lambda being called
     * after the canvas is torn down (canvasSize = 0×0), ensuring we always return a valid snapshot.
     */
    private var cachedSnapshot: CropStateSnapshot? = null

    /**
     * Handles canvas size changes. Three cases:
     *
     * 1. **Pending restore** — a [CropStateSnapshot] is waiting (Activity recreated).
     *    Reconstruct saved geometry and rescale to the new canvas size.
     * 2. **First layout** — canvas was 0×0; initialize from scratch.
     * 3. **Live resize** — canvas changed while the composition survived (e.g. rotation with
     *    `configChanges`). Rescale existing state proportionally.
     */
    internal fun onCanvasSizeChanged(intSize: IntSize) {
        if (intSize.width == 0 || intSize.height == 0) return
        val newCanvasSize = CanvasSize(intSize.width.toFloat(), intSize.height.toFloat())
        val snapshot = pendingSnapshot
        when {
            snapshot != null -> {
                pendingSnapshot = null
                applySnapshot(snapshot, newCanvasSize)
            }
            canvasSize.width == 0f || canvasSize.height == 0f -> {
                canvasSize = newCanvasSize
                resetZoom()
                resetCropIRect()
            }
            else -> rescaleToNewCanvas(newCanvasSize)
        }
    }

    private fun applySnapshot(snapshot: CropStateSnapshot, newCanvasSize: CanvasSize) {
        if (snapshot.bitmapWidth != mImageData.width || snapshot.bitmapHeight != mImageData.height) {
            canvasSize = newCanvasSize
            resetZoom()
            resetCropIRect()
            return
        }
        irectTopleft = Offset(snapshot.topLeftXRatio, snapshot.topLeftYRatio)
        iRect = IRect(topLeft = irectTopleft, size = Size(snapshot.widthRatio, snapshot.heightRatio))
        canvasSize = CanvasSize(1f, 1f)
        zoomScale = snapshot.zoomScale
        zoomOffset = Offset(snapshot.zoomOffsetXRatio, snapshot.zoomOffsetYRatio)
        rescaleToNewCanvas(newCanvasSize)
    }

    private fun rescaleToNewCanvas(newSize: CanvasSize) {
        val oldW = canvasSize.width
        val oldH = canvasSize.height
        val newW = newSize.width
        val newH = newSize.height

        val centerXNorm = (irectTopleft.x + iRect.size.width / 2f) / oldW
        val centerYNorm = (irectTopleft.y + iRect.size.height / 2f) / oldH

        canvasSize = newSize

        val newCenterX = centerXNorm * newW
        val newCenterY = centerYNorm * newH
        val currentType = getCurrCropType()

        when {
            currentType == CropType.SQUARE || currentType == CropType.PROFILE_CIRCLE -> {
                maxSquareLimit = min(newW, newH)
                val sideFraction = iRect.size.width / min(oldW, oldH)
                val newSide = (sideFraction * min(newW, newH)).coerceIn(minSquareLimit, maxSquareLimit)
                val newTopLeftX = (newCenterX - newSide / 2f).coerceIn(0f, newW - newSide)
                val newTopLeftY = (newCenterY - newSide / 2f).coerceIn(0f, newH - newSide)
                irectTopleft = Offset(newTopLeftX, newTopLeftY)
                iRect = IRect(topLeft = irectTopleft, size = Size(newSide, newSide))
            }
            currentType.aspectRatio() != null -> {
                val ratio = currentType.aspectRatio()!!
                val heightFraction = iRect.size.height / oldH
                var newRectH = (heightFraction * newH).coerceAtLeast(minLimit)
                var newRectW = newRectH * ratio
                if (newRectW > newW * 0.98f) { newRectW = newW * 0.98f; newRectH = newRectW / ratio }
                if (newRectH > newH * 0.98f) { newRectH = newH * 0.98f; newRectW = newRectH * ratio }
                val newTopLeftX = (newCenterX - newRectW / 2f).coerceIn(0f, (newW - newRectW).coerceAtLeast(0f))
                val newTopLeftY = (newCenterY - newRectH / 2f).coerceIn(0f, (newH - newRectH).coerceAtLeast(0f))
                irectTopleft = Offset(newTopLeftX, newTopLeftY)
                iRect = IRect(topLeft = irectTopleft, size = Size(newRectW, newRectH))
            }
            else -> {
                val wFraction = iRect.size.width / oldW
                val hFraction = iRect.size.height / oldH
                val newRectW = (wFraction * newW).coerceAtLeast(minLimit)
                val newRectH = (hFraction * newH).coerceAtLeast(minLimit)
                val newTopLeftX = (newCenterX - newRectW / 2f).coerceIn(0f, (newW - newRectW).coerceAtLeast(0f))
                val newTopLeftY = (newCenterY - newRectH / 2f).coerceIn(0f, (newH - newRectH).coerceAtLeast(0f))
                irectTopleft = Offset(newTopLeftX, newTopLeftY)
                iRect = IRect(topLeft = irectTopleft, size = Size(newRectW, newRectH))
            }
        }

        zoomOffset = constrainOffset(
            Offset(x = zoomOffset.x * (newW / oldW), y = zoomOffset.y * (newH / oldH)),
            zoomScale
        )
        updateTouchRect()
    }

    internal fun resetCropIRect() {
        val canWidth = canvasSize.width
        val canHeight = canvasSize.height
        val currentType = getCurrCropType()

        when {
            currentType == CropType.SQUARE || currentType == CropType.PROFILE_CIRCLE -> {
                val squareSize = getSquareSize(canWidth, canHeight)
                irectTopleft = getSquarePosition(canWidth, canHeight, squareSize.width)
                iRect = IRect(topLeft = irectTopleft, size = squareSize)
            }
            currentType.aspectRatio() != null -> {
                val ratio = currentType.aspectRatio()!!
                val ratioSize = getAspectRatioSize(canWidth, canHeight, ratio)
                irectTopleft = getCenteredPosition(canWidth, canHeight, ratioSize)
                iRect = IRect(topLeft = irectTopleft, size = ratioSize)
            }
            else -> {
                irectTopleft = Offset(x = 0.0F, y = 0.0F)
                iRect = IRect(topLeft = irectTopleft, size = Size(canWidth, canHeight))
            }
        }

        updateTouchRect()
    }

    private fun getSquareSize(width: Float, height: Float): Size {
        val squareSize = minOf(width, height) - 100F
        maxSquareLimit = squareSize + 100F
        return Size(squareSize, squareSize)
    }

    private fun getSquarePosition(width: Float, height: Float, squareSize: Float): Offset {
        val x = (width - squareSize) / 2
        val y = (height - squareSize) / 2
        return Offset(x, y)
    }

    private fun getAspectRatioSize(canvasWidth: Float, canvasHeight: Float, ratio: Float): Size {
        val padding = 100F
        val availableWidth = canvasWidth - padding
        val availableHeight = canvasHeight - padding
        val width: Float
        val height: Float
        if (availableWidth / availableHeight > ratio) {
            height = availableHeight
            width = height * ratio
        } else {
            width = availableWidth
            height = width / ratio
        }
        return Size(width, height)
    }

    private fun getCenteredPosition(canvasWidth: Float, canvasHeight: Float, size: Size): Offset {
        val x = (canvasWidth - size.width) / 2
        val y = (canvasHeight - size.height) / 2
        return Offset(x, y)
    }

    private fun constrainToAspectRatio(
        proposedWidth: Float,
        proposedHeight: Float,
        ratio: Float
    ): Pair<Float, Float> {
        val w: Float
        val h: Float
        if (proposedWidth / ratio <= proposedHeight) {
            w = proposedWidth.coerceAtLeast(minLimit)
            h = (w / ratio).coerceAtLeast(minLimit)
        } else {
            h = proposedHeight.coerceAtLeast(minLimit)
            w = (h * ratio).coerceAtLeast(minLimit)
        }
        return w to h
    }

    private fun updateTouchRect() {
        val insidePadding = paddingForTouchRect * 2
        touchRect = IRect(
            topLeft = Offset(irectTopleft.x + paddingForTouchRect, irectTopleft.y + paddingForTouchRect),
            size = Size(iRect.size.width - insidePadding, iRect.size.height - insidePadding)
        )
    }

    internal fun onDragStart(touchPoint: Offset) {
        isTouchedInsideRectMove = isTouchInputInsideTheTouchRect(touchPoint)
        rectEdgeTouched = getRectEdge(touchPoint)
        lastPointUpdated = touchPoint
    }

    internal fun onDrag(dragPoint: Offset) {
        if (isTouchedInsideRectMove) {
            processIRectDrag(dragPoint = dragPoint)
        } else {
            when (rectEdgeTouched) {
                RectEdge.TOP_LEFT -> topLeftCornerDrag(dragPoint)
                RectEdge.TOP_RIGHT -> topRightCornerDrag(dragPoint)
                RectEdge.BOTTOM_LEFT -> bottomLeftCornerDrag(dragPoint)
                RectEdge.BOTTOM_RIGHT -> bottomRightCornerDrag(dragPoint)
                else -> Unit
            }
        }
    }

    internal fun onDragEnd() {
        isTouchedInsideRectMove = false
        lastPointUpdated = null
        rectEdgeTouched = RectEdge.NULL
    }

    private var lastPanPoint: Offset? = null

    internal fun onZoomChange(centroid: Offset, scaleChange: Float, panChange: Offset) {
        val newScale = (zoomScale * scaleChange).coerceIn(minZoom, maxZoom)
        val scaleFactor = newScale / zoomScale
        val pivot = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val newOffset = Offset(
            x = (centroid.x - pivot.x) * (1f - scaleFactor) + zoomOffset.x * scaleFactor + panChange.x,
            y = (centroid.y - pivot.y) * (1f - scaleFactor) + zoomOffset.y * scaleFactor + panChange.y
        )
        zoomScale = newScale
        zoomOffset = constrainOffset(newOffset, newScale)
    }

    private fun constrainOffset(offset: Offset, scale: Float): Offset {
        val maxOffsetX = (canvasSize.width / 2f) * (scale - 1f)
        val maxOffsetY = (canvasSize.height / 2f) * (scale - 1f)
        return Offset(
            x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    internal fun resetZoom() {
        zoomScale = 1.0f
        zoomOffset = Offset.Zero
        lastPanPoint = null
    }

    internal fun onDoubleTapZoom(tapPoint: Offset) {
        if (zoomScale > minZoom) {
            zoomScale = minZoom
            zoomOffset = Offset.Zero
        } else {
            val targetScale = 2.0f
            val pivot = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            val newOffset = Offset(
                x = (tapPoint.x - pivot.x) * (1f - targetScale),
                y = (tapPoint.y - pivot.y) * (1f - targetScale)
            )
            zoomScale = targetScale
            zoomOffset = constrainOffset(newOffset, targetScale)
        }
    }

    internal fun onImagePanStart(touchPoint: Offset) {
        lastPanPoint = touchPoint
    }

    internal fun onImagePanDrag(dragPoint: Offset) {
        lastPanPoint?.let { last ->
            if (last != dragPoint) {
                val newOffset = Offset(
                    x = zoomOffset.x + (dragPoint.x - last.x),
                    y = zoomOffset.y + (dragPoint.y - last.y)
                )
                zoomOffset = constrainOffset(newOffset, zoomScale)
            }
        }
        lastPanPoint = dragPoint
    }

    internal fun onImagePanEnd() {
        lastPanPoint = null
    }

    internal fun isTouchOnCropRect(touchPoint: Offset): Boolean {
        if (canvasSize.width == 0f || canvasSize.height == 0f) return true
        val cornerPad = paddingForTouchRect * 3f
        val left = iRect.topLeft.x - cornerPad
        val top = iRect.topLeft.y - cornerPad
        val right = iRect.topLeft.x + iRect.size.width + cornerPad
        val bottom = iRect.topLeft.y + iRect.size.height + cornerPad
        return touchPoint.x in left..right && touchPoint.y in top..bottom
    }

    private fun canvasPointToImagePoint(canvasPoint: Offset): Offset {
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        return Offset(
            x = cx + (canvasPoint.x - zoomOffset.x - cx) / zoomScale,
            y = cy + (canvasPoint.y - zoomOffset.y - cy) / zoomScale
        )
    }

    private fun processIRectDrag(dragPoint: Offset) {
        dragDiffCalculation(dragPoint)?.let { diffOffset ->
            val offsetCheck = Offset(
                x = irectTopleft.x + diffOffset.x,
                y = irectTopleft.y + diffOffset.y
            )

            if (offsetCheck.x >= 0F && offsetCheck.y >= 0F && isDragPointInsideTheCanvas(offsetCheck)) {
                updateIRectTopLeftPoint(offsetCheck)
            } else {
                val x = offsetCheck.x
                val y = offsetCheck.y
                var newOffset: Offset? = null

                if (y <= 0F && x > 0.0F && (x + iRect.size.width in 0F..canvasSize.width)) {
                    newOffset = Offset(x, 0.0F)
                } else if (x <= 0F && y > 0F && (y + iRect.size.height in 0F..canvasSize.height)) {
                    newOffset = Offset(0.0F, y)
                } else if ((x + iRect.size.width >= canvasSize.width) && y >= 0F && (y + iRect.size.height in 0F..canvasSize.height)) {
                    newOffset = Offset(canvasSize.width - iRect.size.width, y)
                } else if ((y + iRect.size.height >= canvasSize.height) && x > 0F && (x + iRect.size.width in 0F..canvasSize.width)) {
                    newOffset = Offset(x, canvasSize.height - iRect.size.height)
                }

                if (newOffset != null) updateIRectTopLeftPoint(newOffset)
            }
        }
    }

    private fun topLeftCornerDrag(dragPoint: Offset) {
        dragDiffCalculation(dragPoint)?.let { dragDiff ->
            val fixedRight = irectTopleft.x + iRect.size.width
            val fixedBottom = irectTopleft.y + iRect.size.height

            var newX = (irectTopleft.x + dragDiff.x).coerceAtLeast(0f).coerceAtMost(fixedRight - minLimit)
            var newY = (irectTopleft.y + dragDiff.y).coerceAtLeast(0f).coerceAtMost(fixedBottom - minLimit)

            val newWidth = fixedRight - newX
            val newHeight = fixedBottom - newY

            val sizeOfIRect = when {
                cropType == CropType.PROFILE_CIRCLE || cropType == CropType.SQUARE -> {
                    val sqSide = min(newWidth, newHeight).coerceAtLeast(minLimit)
                    newX = fixedRight - sqSide
                    newY = fixedBottom - sqSide
                    if (newX < 0f) newX = 0f
                    if (newY < 0f) newY = 0f
                    val finalSide = min(fixedRight - newX, fixedBottom - newY)
                    newX = fixedRight - finalSide
                    newY = fixedBottom - finalSide
                    Size(finalSide, finalSide)
                }
                cropType?.aspectRatio() != null -> {
                    val ratio = cropType!!.aspectRatio()!!
                    val (w, h) = constrainToAspectRatio(newWidth, newHeight, ratio)
                    newX = (fixedRight - w).coerceAtLeast(0f)
                    newY = (fixedBottom - h).coerceAtLeast(0f)
                    val clampedW = fixedRight - newX
                    val clampedH = fixedBottom - newY
                    val fitW = min(clampedW, clampedH * ratio)
                    val fitH = fitW / ratio
                    newX = fixedRight - fitW
                    newY = fixedBottom - fitH
                    Size(fitW, fitH)
                }
                else -> Size(newWidth, newHeight)
            }

            irectTopleft = Offset(newX, newY)
            iRect = iRect.copy(topLeft = irectTopleft, size = sizeOfIRect)
            updateTouchRect()
        }
    }

    private fun calculateNewSize(currentSize: Float, dragDiff: Float): Float {
        return if (dragDiff < 0F) currentSize + abs(dragDiff) else max(
            currentSize - abs(dragDiff),
            minLimit
        )
    }

    private fun topRightCornerDrag(dragPoint: Offset) {
        dragDiffCalculation(dragPoint)?.let { dragDiff ->
            val (canvasWidth, _) = canvasSize
            val fixedLeft = irectTopleft.x
            val fixedBottom = irectTopleft.y + iRect.size.height

            val newRight = (irectTopleft.x + iRect.size.width + dragDiff.x).coerceAtMost(canvasWidth).coerceAtLeast(fixedLeft + minLimit)
            var newTop = (irectTopleft.y + dragDiff.y).coerceAtLeast(0f).coerceAtMost(fixedBottom - minLimit)

            val newWidth = newRight - fixedLeft
            val newHeight = fixedBottom - newTop

            val sizeOfIRect = when {
                cropType == CropType.PROFILE_CIRCLE || cropType == CropType.SQUARE -> {
                    val sqSide = min(newWidth, newHeight).coerceAtLeast(minLimit)
                    newTop = fixedBottom - sqSide
                    if (newTop < 0f) newTop = 0f
                    val finalSide = min(fixedLeft + canvasWidth - fixedLeft, fixedBottom - newTop).coerceAtMost(sqSide)
                    newTop = fixedBottom - finalSide
                    Size(finalSide, finalSide)
                }
                cropType?.aspectRatio() != null -> {
                    val ratio = cropType!!.aspectRatio()!!
                    val (w, h) = constrainToAspectRatio(newWidth, newHeight, ratio)
                    newTop = (fixedBottom - h).coerceAtLeast(0f)
                    val clampedH = fixedBottom - newTop
                    val clampedW = min(w, canvasWidth - fixedLeft)
                    val fitW = min(clampedW, clampedH * ratio)
                    val fitH = fitW / ratio
                    newTop = fixedBottom - fitH
                    Size(fitW, fitH)
                }
                else -> Size(newWidth, newHeight)
            }

            irectTopleft = Offset(fixedLeft, newTop)
            iRect = iRect.copy(topLeft = irectTopleft, size = sizeOfIRect)
            updateTouchRect()
        }
    }

    private fun bottomLeftCornerDrag(dragPoint: Offset) {
        dragDiffCalculation(dragPoint)?.let { dragDiff ->
            val (_, canvasHeight) = canvasSize
            val fixedTop = irectTopleft.y
            val fixedRight = irectTopleft.x + iRect.size.width

            var newLeft = (irectTopleft.x + dragDiff.x).coerceAtLeast(0f).coerceAtMost(fixedRight - minLimit)
            val newBottom = (irectTopleft.y + iRect.size.height + dragDiff.y).coerceAtMost(canvasHeight).coerceAtLeast(fixedTop + minLimit)

            val newWidth = fixedRight - newLeft
            val newHeight = newBottom - fixedTop

            val sizeOfIRect = when {
                cropType == CropType.PROFILE_CIRCLE || cropType == CropType.SQUARE -> {
                    val sqSide = min(newWidth, newHeight).coerceAtLeast(minLimit)
                    newLeft = fixedRight - sqSide
                    if (newLeft < 0f) newLeft = 0f
                    val finalSide = min(fixedRight - newLeft, canvasHeight - fixedTop).coerceAtMost(sqSide)
                    newLeft = fixedRight - finalSide
                    Size(finalSide, finalSide)
                }
                cropType?.aspectRatio() != null -> {
                    val ratio = cropType!!.aspectRatio()!!
                    val (w, h) = constrainToAspectRatio(newWidth, newHeight, ratio)
                    newLeft = (fixedRight - w).coerceAtLeast(0f)
                    val clampedW = fixedRight - newLeft
                    val clampedH = min(h, canvasHeight - fixedTop)
                    val fitH = min(clampedH, clampedW / ratio)
                    val fitW = fitH * ratio
                    newLeft = fixedRight - fitW
                    Size(fitW, fitH)
                }
                else -> Size(newWidth, newHeight)
            }

            irectTopleft = Offset(newLeft, fixedTop)
            iRect = iRect.copy(topLeft = irectTopleft, size = sizeOfIRect)
            updateTouchRect()
        }
    }

    private fun bottomRightCornerDrag(dragPoint: Offset) {
        dragDiffCalculation(dragPoint)?.let { dragDiff ->
            val (canvasWidth, canvasHeight) = canvasSize
            val fixedLeft = irectTopleft.x
            val fixedTop = irectTopleft.y

            val newRight = (irectTopleft.x + iRect.size.width + dragDiff.x).coerceAtMost(canvasWidth).coerceAtLeast(fixedLeft + minLimit)
            val newBottom = (irectTopleft.y + iRect.size.height + dragDiff.y).coerceAtMost(canvasHeight).coerceAtLeast(fixedTop + minLimit)

            val newWidth = newRight - fixedLeft
            val newHeight = newBottom - fixedTop

            val sizeOfIRect = when {
                cropType == CropType.PROFILE_CIRCLE || cropType == CropType.SQUARE -> {
                    val sqSide = min(newWidth, newHeight).coerceAtLeast(minLimit)
                    Size(sqSide, sqSide)
                }
                cropType?.aspectRatio() != null -> {
                    val ratio = cropType!!.aspectRatio()!!
                    val (w, h) = constrainToAspectRatio(newWidth, newHeight, ratio)
                    val fitW = min(w, canvasWidth - fixedLeft)
                    val fitH = min(h, canvasHeight - fixedTop)
                    val finalW = min(fitW, fitH * ratio)
                    val finalH = finalW / ratio
                    Size(finalW, finalH)
                }
                else -> Size(newWidth, newHeight)
            }

            iRect = iRect.copy(topLeft = irectTopleft, size = sizeOfIRect)
            updateTouchRect()
        }
    }

    private fun updateIRectTopLeftPoint(offset: Offset) {
        irectTopleft = offset
        touchAreaRectTopLeft = Offset(
            x = irectTopleft.x + paddingForTouchRect,
            y = irectTopleft.y + paddingForTouchRect
        )
        iRect = iRect.copy(topLeft = irectTopleft)
        touchRect = touchRect.copy(topLeft = touchAreaRectTopLeft)
    }

    private fun isDragPointInsideTheCanvas(dragPoint: Offset): Boolean {
        val x = dragPoint.x + iRect.size.width
        val y = dragPoint.y + iRect.size.height
        return x in 0F..canvasSize.width && y in 0F..canvasSize.height
    }

    private fun dragDiffCalculation(dragPoint: Offset): Offset? {
        if (lastPointUpdated != null && lastPointUpdated != dragPoint) {
            val dx = dragPoint.x - lastPointUpdated!!.x
            val dy = dragPoint.y - lastPointUpdated!!.y
            lastPointUpdated = dragPoint
            return Offset(dx, dy)
        }
        lastPointUpdated = dragPoint
        return null
    }

    private fun getRectEdge(touchPoint: Offset): RectEdge {
        val topleftX = iRect.topLeft.x
        val topleftY = iRect.topLeft.y
        val rectWidth = topleftX + iRect.size.width
        val rectHeight = topleftY + iRect.size.height
        val padding = minLimit

        val onRight = touchPoint.x in (rectWidth - padding..rectWidth + padding)
        val onBottom = touchPoint.y in (rectHeight - padding..rectHeight + padding)
        val onLeft = touchPoint.x in (topleftX - padding..topleftX + padding)
        val onTop = touchPoint.y in (topleftY - padding..topleftY + padding)

        return when {
            onRight && onBottom -> RectEdge.BOTTOM_RIGHT
            onBottom && onLeft -> RectEdge.BOTTOM_LEFT
            onRight && onTop -> RectEdge.TOP_RIGHT
            onLeft && onTop -> RectEdge.TOP_LEFT
            else -> RectEdge.NULL
        }
    }

    private fun isTouchInputInsideTheTouchRect(touchPoint: Offset): Boolean {
        val xStart = touchRect.topLeft.x
        val xEnd = touchRect.topLeft.x + touchRect.size.width
        val yStart = touchRect.topLeft.y
        val yEnd = touchRect.topLeft.y + touchRect.size.height
        return touchPoint.x in xStart..xEnd && touchPoint.y in yStart..yEnd
    }

    internal fun cropImage(): ImageData {
        val canvasWidth = canvasSize.width.toInt()
        val canvasHeight = canvasSize.height.toInt()
        val cropRect = getRectFromPoints()
        val sourceImage = imageData ?: mImageData

        if (canvasWidth <= 0 || canvasHeight <= 0) return sourceImage

        val scaledImage = scaleImage(sourceImage, canvasWidth, canvasHeight)
        val topLeftImage = canvasPointToImagePoint(Offset(cropRect.left, cropRect.top))
        val bottomRightImage = canvasPointToImagePoint(Offset(cropRect.right, cropRect.bottom))

        val cropLeft = topLeftImage.x.toInt().coerceAtLeast(0)
        val cropTop = topLeftImage.y.toInt().coerceAtLeast(0)
        var cropWidth = (bottomRightImage.x - topLeftImage.x).toInt().coerceIn(1, canvasWidth)
        var cropHeight = (bottomRightImage.y - topLeftImage.y).toInt().coerceIn(1, canvasHeight)

        if (cropLeft + cropWidth > canvasWidth) cropWidth = canvasWidth - cropLeft
        if (cropTop + cropHeight > canvasHeight) cropHeight = canvasHeight - cropTop
        cropWidth = cropWidth.coerceAtLeast(1)
        cropHeight = cropHeight.coerceAtLeast(1)

        var croppedImage = cropImage(scaledImage, cropLeft, cropTop, cropWidth, cropHeight)
        croppedImage = when {
            cropType == CropType.SQUARE || cropType == CropType.PROFILE_CIRCLE ->
                scaleImage(croppedImage, maxSquareLimit.toInt(), maxSquareLimit.toInt())
            cropType?.aspectRatio() != null -> {
                val ratio = cropType!!.aspectRatio()!!
                val targetWidth: Int
                val targetHeight: Int
                if (canvasWidth.toFloat() / canvasHeight.toFloat() > ratio) {
                    targetHeight = canvasHeight
                    targetWidth = (canvasHeight * ratio).toInt()
                } else {
                    targetWidth = canvasWidth
                    targetHeight = (canvasWidth / ratio).toInt()
                }
                scaleImage(croppedImage, targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1))
            }
            else -> croppedImage
        }
        return croppedImage
    }

    internal fun cropSourceImage(): ImageData {
        val sourceImage = imageData ?: mImageData
        val canvasCropRect = getRectFromPoints()

        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return sourceImage

        val topLeftImage = canvasPointToImagePoint(Offset(canvasCropRect.left, canvasCropRect.top))
        val bottomRightImage = canvasPointToImagePoint(Offset(canvasCropRect.right, canvasCropRect.bottom))

        val scaleX = sourceImage.width.toFloat() / canvasSize.width
        val scaleY = sourceImage.height.toFloat() / canvasSize.height

        val sourceCropLeft = (topLeftImage.x * scaleX).toInt().coerceAtLeast(0)
        val sourceCropTop = (topLeftImage.y * scaleY).toInt().coerceAtLeast(0)
        var sourceCropWidth = ((bottomRightImage.x - topLeftImage.x) * scaleX).toInt()
        var sourceCropHeight = ((bottomRightImage.y - topLeftImage.y) * scaleY).toInt()

        sourceCropWidth = sourceCropWidth.coerceAtMost(sourceImage.width - sourceCropLeft)
        sourceCropHeight = sourceCropHeight.coerceAtMost(sourceImage.height - sourceCropTop)

        if (sourceCropWidth <= 0 || sourceCropHeight <= 0) return sourceImage

        return cropImage(sourceImage, sourceCropLeft, sourceCropTop, sourceCropWidth, sourceCropHeight)
    }

    internal fun updateCropType(type: CropType) {
        cropType = type
        resetCropIRect()
    }

    internal fun toSnapshot(): CropStateSnapshot? {
        if (canvasSize.width == 0f || canvasSize.height == 0f) return cachedSnapshot
        val snapshot = CropStateSnapshot(
            topLeftXRatio    = irectTopleft.x / canvasSize.width,
            topLeftYRatio    = irectTopleft.y / canvasSize.height,
            widthRatio       = iRect.size.width / canvasSize.width,
            heightRatio      = iRect.size.height / canvasSize.height,
            zoomScale        = zoomScale,
            zoomOffsetXRatio = zoomOffset.x / canvasSize.width,
            zoomOffsetYRatio = zoomOffset.y / canvasSize.height,
            bitmapWidth      = mImageData.width,
            bitmapHeight     = mImageData.height
        )
        cachedSnapshot = snapshot
        return snapshot
    }

    private fun getCurrCropType(): CropType = cropType ?: CropType.FREE_STYLE

    internal fun updateBitmapImage(image: ImageData) {
        imageData = image
    }

    private fun getRectFromPoints(): Rect {
        val size = iRect.size
        return Rect(
            irectTopleft.x,
            irectTopleft.y,
            size.width + irectTopleft.x,
            size.height + irectTopleft.y,
        )
    }
}