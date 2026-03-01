package com.underwaterai.enhance.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.underwaterai.enhance.ui.theme.AccentCyan
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Before/after image comparison slider with pinch-to-zoom.
 *
 * Single-finger horizontal drag moves the slider divider.
 * Two-finger pinch zooms both images in sync (1x–5x).
 * Two-finger pan scrolls both images in sync.
 */
@Composable
fun ImageComparisonSlider(
    originalBitmap: Bitmap,
    enhancedBitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    var sliderFraction by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val originalImage = remember(originalBitmap) { originalBitmap.asImageBitmap() }
    val enhancedImage = remember(enhancedBitmap) { enhancedBitmap.asImageBitmap() }

    val zoomPanModifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = offsetX
        translationY = offsetY
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { size ->
                containerWidthPx = size.width
                containerHeightPx = size.height
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    firstDown.consume()

                    var isMultiTouch = false

                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }

                        if (pointers.size >= 2) {
                            isMultiTouch = true
                            // Pinch-to-zoom + pan
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val maxOffsetX = (newScale - 1f) * containerWidthPx / 2f
                            val maxOffsetY = (newScale - 1f) * containerHeightPx / 2f

                            scale = newScale
                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)

                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pointers.size == 1 && !isMultiTouch) {
                            // Single-finger drag → move slider
                            val change = pointers[0]
                            if (change.positionChanged() && containerWidthPx > 0) {
                                val dx = change.position.x - change.previousPosition.x
                                sliderFraction = (sliderFraction + dx / containerWidthPx).coerceIn(0f, 1f)
                                change.consume()
                            }
                        }
                    } while (pointers.isNotEmpty())

                    // Reset zoom on double-tap-like quick release (if was single touch and scale > 1)
                }
            }
    ) {
        // Left image (Original) — clipped from 0 to sliderFraction
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    clip = true
                    shape = SliderClipShape(0f, sliderFraction)
                }
        ) {
            Image(
                bitmap = originalImage,
                contentDescription = "Original image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(zoomPanModifier)
            )
        }

        // Right image (Enhanced) — clipped from sliderFraction to 1
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    clip = true
                    shape = SliderClipShape(sliderFraction, 1f)
                }
        ) {
            Image(
                bitmap = enhancedImage,
                contentDescription = "Enhanced image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(zoomPanModifier)
            )
        }

        // Divider line
        val dividerOffsetXDp = with(density) {
            (sliderFraction * containerWidthPx).toDp() - 1.dp
        }
        Box(
            modifier = Modifier
                .offset(x = dividerOffsetXDp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.85f))
        )

        // Circular handle
        val handleSize = 32.dp
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (sliderFraction * containerWidthPx).roundToInt() -
                                with(density) { (handleSize / 2).roundToPx() },
                        y = containerHeightPx / 2 -
                                with(density) { (handleSize / 2).roundToPx() }
                    )
                }
                .size(handleSize)
                .clip(CircleShape)
                .background(AccentCyan),
            contentAlignment = Alignment.Center
        ) {
            Row {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // "Original" label (top-left)
        Text(
            text = "Original",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 10.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // "Enhanced" label (top-right)
        Text(
            text = "Enhanced",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 10.dp, top = 10.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // "Pinch to zoom" hint at bottom center
        Text(
            text = "Pinch to zoom",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Clips to a horizontal band — only the region from [leftFraction] to [rightFraction]
 * of the total width is visible.
 */
private class SliderClipShape(
    private val leftFraction: Float,
    private val rightFraction: Float
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        return androidx.compose.ui.graphics.Outline.Rectangle(
            androidx.compose.ui.geometry.Rect(
                left = size.width * leftFraction,
                top = 0f,
                right = size.width * rightFraction,
                bottom = size.height
            )
        )
    }
}
