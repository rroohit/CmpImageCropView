package com.cmp.image.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmp.image.cropview.CropType
import com.cmp.image.cropview.EdgeType
import com.cmp.image.cropview.ImageCrop
import com.cmp.image.cropview.ImageCropView
import com.cmp.image.cropview.ImageData
import com.cmp.image.cropview.rememberSaveableImageCrop
import com.cmp.image.cropview.toImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropDemoScreen(viewModel: CropDemoViewModel = viewModel { CropDemoViewModel() }) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Image Crop View") }) }
    ) { innerPadding ->
        val image = uiState.displayImage
        if (image == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            DemoContent(
                image = image,
                isLoading = uiState.isLoading,
                cropType = uiState.cropType,
                croppedImages = uiState.croppedImages,
                showResults = uiState.showResults,
                previewImage = uiState.previewImage,
                onCropSaved = viewModel::saveCrop,
                onChangeImage = viewModel::loadNextImage,
                onCropTypeSelected = viewModel::setCropType,
                onShowResults = viewModel::showResults,
                onHideResults = viewModel::hideResults,
                onPreviewImage = viewModel::openPreview,
                onDismissPreview = viewModel::closePreview,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DemoContent(
    image: ImageData,
    isLoading: Boolean,
    cropType: CropType,
    croppedImages: List<ImageData>,
    showResults: Boolean,
    previewImage: ImageData?,
    onCropSaved: (ImageData) -> Unit,
    onChangeImage: () -> Unit,
    onCropTypeSelected: (CropType) -> Unit,
    onShowResults: () -> Unit,
    onHideResults: () -> Unit,
    onPreviewImage: (ImageData) -> Unit,
    onDismissPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // imageCrop must live here in the composition: rememberSaveableImageCrop keys on `image`,
    // so a new ImageCrop is created automatically whenever the user loads a different image.
    val imageCrop = rememberSaveableImageCrop(image)
    val onCrop: () -> Unit = { onCropSaved(imageCrop.onCrop()) }
    val onReset: () -> Unit = { imageCrop.resetView() }

    BoxWithConstraints(modifier = modifier) {
        if (maxWidth >= 600.dp) {
            WideLayout(
                imageCrop = imageCrop,
                cropType = cropType,
                isLoading = isLoading,
                croppedImagesCount = croppedImages.size,
                onCrop = onCrop,
                onReset = onReset,
                onChangeImage = onChangeImage,
                onCropTypeSelected = onCropTypeSelected,
                onShowResults = onShowResults,
            )
        } else {
            CompactLayout(
                imageCrop = imageCrop,
                cropType = cropType,
                isLoading = isLoading,
                croppedImagesCount = croppedImages.size,
                onCrop = onCrop,
                onReset = onReset,
                onChangeImage = onChangeImage,
                onCropTypeSelected = onCropTypeSelected,
                onShowResults = onShowResults,
            )
        }
    }

    if (showResults) {
        ResultsBottomSheet(
            images = croppedImages,
            onResultClick = onPreviewImage,
            onDismiss = onHideResults,
        )
    }

    previewImage?.let { img ->
        ImagePreviewDialog(image = img, onDismiss = onDismissPreview)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Layouts
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactLayout(
    imageCrop: ImageCrop,
    cropType: CropType,
    isLoading: Boolean,
    croppedImagesCount: Int,
    onCrop: () -> Unit,
    onReset: () -> Unit,
    onChangeImage: () -> Unit,
    onCropTypeSelected: (CropType) -> Unit,
    onShowResults: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CropArea(
            imageCrop = imageCrop,
            cropType = cropType,
            isLoading = isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            CropTypeChips(currentCropType = cropType, onCropTypeSelected = onCropTypeSelected)
            Spacer(Modifier.height(12.dp))
            CropActions(onCrop = onCrop, onReset = onReset, onChangeImage = onChangeImage, vertical = false)
            Spacer(Modifier.height(12.dp))
            ResultsButton(count = croppedImagesCount, onClick = onShowResults, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WideLayout(
    imageCrop: ImageCrop,
    cropType: CropType,
    isLoading: Boolean,
    croppedImagesCount: Int,
    onCrop: () -> Unit,
    onReset: () -> Unit,
    onChangeImage: () -> Unit,
    onCropTypeSelected: (CropType) -> Unit,
    onShowResults: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        CropArea(
            imageCrop = imageCrop,
            cropType = cropType,
            isLoading = isLoading,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
        )
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Crop type", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            CropTypeChips(currentCropType = cropType, onCropTypeSelected = onCropTypeSelected)
            Spacer(Modifier.height(20.dp))
            CropActions(onCrop = onCrop, onReset = onReset, onChangeImage = onChangeImage, vertical = true)
            Spacer(Modifier.height(20.dp))
            ResultsButton(count = croppedImagesCount, onClick = onShowResults, modifier = Modifier.fillMaxWidth())
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CropArea(
    imageCrop: ImageCrop,
    cropType: CropType,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ImageCropView(
            imageCrop = imageCrop,
            modifier = Modifier.fillMaxSize(),
            guideLineColor = Color.LightGray,
            guideLineWidth = 2.dp,
            edgeCircleSize = 5.dp,
            showGuideLines = cropType != CropType.PROFILE_CIRCLE,
            cropType = cropType,
            edgeType = EdgeType.CIRCULAR,
            enableZoom = true,
        )
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private data class CropTypeOption(val cropType: CropType, val label: String)

private val cropTypeOptions = listOf(
    CropTypeOption(CropType.FREE_STYLE, "Free"),
    CropTypeOption(CropType.SQUARE, "Square"),
    CropTypeOption(CropType.PROFILE_CIRCLE, "Circle"),
    CropTypeOption(CropType.RATIO_3_2, "3:2"),
    CropTypeOption(CropType.RATIO_4_3, "4:3"),
    CropTypeOption(CropType.RATIO_16_9, "16:9"),
    CropTypeOption(CropType.RATIO_9_16, "9:16"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CropTypeChips(
    currentCropType: CropType,
    onCropTypeSelected: (CropType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cropTypeOptions.forEach { option ->
            FilterChip(
                selected = option.cropType == currentCropType,
                onClick = { onCropTypeSelected(option.cropType) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun CropActions(
    onCrop: () -> Unit,
    onReset: () -> Unit,
    onChangeImage: () -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    if (vertical) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onCrop, modifier = Modifier.fillMaxWidth()) { Text("Crop") }
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
            OutlinedButton(onClick = onChangeImage, modifier = Modifier.fillMaxWidth()) { Text("Change Image") }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onChangeImage, modifier = Modifier.weight(1f)) { Text("Change", maxLines = 1) }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
            Button(onClick = onCrop, modifier = Modifier.weight(1f)) { Text("Crop") }
        }
    }
}

@Composable
private fun ResultsButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, enabled = count > 0, modifier = modifier) {
        Text(if (count > 0) "View results ($count)" else "No results yet")
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Overlays
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsBottomSheet(
    images: List<ImageData>,
    onResultClick: (ImageData) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Cropped images (${images.size})",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(images) { _, image ->
                    Image(
                        bitmap = image.toImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .clickable { onResultClick(image) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(image: ImageData, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = image.toImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }
}