package com.alweb.photoalbumlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alweb.photoalbumlauncher.data.Photo

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Full-screen pager over the album. Each page pinch-zooms and pans; the pager stops
 * swiping while a page is zoomed in, so panning does not fight page changes.
 */
@Composable
fun PhotoViewerScreen(
    photos: List<Photo>,
    initialIndex: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size },
    )
    var chromeVisible by remember { mutableStateOf(false) }
    var currentPageZoomed by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !currentPageZoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val isActive = page == pagerState.currentPage
            ZoomablePhoto(
                photo = photos[page],
                isActive = isActive,
                onZoomedChange = { zoomed -> if (isActive) currentPageZoomed = zoomed },
                onTap = { chromeVisible = !chromeVisible },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    photo: Photo,
    isActive: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Keep the image inside its frame: at scale 1 there is no slack, and the
    // allowance grows with the scale factor.
    fun clampOffset(candidate: Offset, forScale: Float): Offset {
        val maxX = (containerSize.width * (forScale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (forScale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    // Pinch always transforms, but a one-finger drag is only ours once zoomed in.
    // Otherwise this would swallow the drag and the pager could never turn a page.
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        val factor = newScale / oldScale
        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)

        // Anchor the zoom on the pinch centroid, then apply the drag.
        val nextOffset =
            offset * factor + (centroid - center) * (1f - factor) + panChange
        scale = newScale
        offset = clampOffset(nextOffset, newScale)
    }

    // Swiping a zoomed page out of view should leave it reset when it comes back.
    LaunchedEffect(isActive) {
        if (!isActive) {
            scale = MIN_SCALE
            offset = Offset.Zero
        }
    }

    LaunchedEffect(scale, isActive) {
        if (isActive) onZoomedChange(scale > MIN_SCALE + 0.01f)
    }

    AsyncImage(
        model = photo.uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapPosition ->
                        if (scale > MIN_SCALE + 0.01f) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            val center = Offset(
                                containerSize.width / 2f,
                                containerSize.height / 2f,
                            )
                            // Pull the tapped point toward the middle of the screen.
                            offset = clampOffset(
                                (center - tapPosition) * (DOUBLE_TAP_SCALE - 1f),
                                DOUBLE_TAP_SCALE,
                            )
                            scale = DOUBLE_TAP_SCALE
                        }
                    },
                )
            }
            .transformable(
                state = transformState,
                canPan = { scale > MIN_SCALE + 0.01f },
                lockRotationOnZoomPan = true,
            )
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
    )
}
