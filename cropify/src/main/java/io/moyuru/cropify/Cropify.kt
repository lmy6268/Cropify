package io.moyuru.cropify

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.roundToInt

@Composable
fun Cropify(
  bitmap: ImageBitmap,
  state: CropifyState,
  onImageCropped: (ImageBitmap) -> Unit,
  modifier: Modifier = Modifier,
  option: CropifyOption = CropifyOption(),
) {
  val density = LocalDensity.current
  val tolerance = remember { density.run { 24.dp.toPx() } }
  var touchRegion = remember<TouchRegion?> { null }

  BoxWithConstraints(
    modifier = modifier
      .pointerInput(bitmap, option.frameAspectRatio) {
        detectDragGestures(
          onDragStart = { touchRegion = detectTouchRegion(it, state.frameRect, tolerance) },
          onDragEnd = { touchRegion = null }
        ) { change, dragAmount ->
          touchRegion?.let {
            when (it) {
              is TouchRegion.Vertex -> state.scaleFrameRect(it, option.frameAspectRatio, dragAmount, tolerance * 2)
              TouchRegion.Inside -> state.translateFrameRect(dragAmount)
            }
            change.consume()
          }
        }
      }
  ) {
    LaunchedEffect(state.shouldCrop) {
      if (!state.shouldCrop) return@LaunchedEffect
      val scale = bitmap.width / state.imageRect.width
      val cropped = Bitmap.createBitmap(
        bitmap.asAndroidBitmap(),
        ((state.frameRect.left - state.imageRect.left) * scale).roundToInt(),
        ((state.frameRect.top - state.imageRect.top) * scale).roundToInt(),
        (state.frameRect.width * scale).roundToInt(),
        (state.frameRect.height * scale).roundToInt(),
      ).asImageBitmap()
      state.shouldCrop = false
      onImageCropped(cropped)
    }
    LaunchedEffect(bitmap, option.frameAspectRatio, constraints) {
      val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
      state.imageRect = calculateImagePosition(bitmap, canvasSize)
      state.frameRect = calculateFrameRect(state.imageRect, canvasSize, option.frameAspectRatio)
    }
    ImageCanvas(
      bitmap = bitmap,
      offset = state.imageRect.topLeft,
      size = state.imageRect.size,
      option = option,
      modifier = Modifier.matchParentSize(),
    )
    ImageOverlay(
      offset = state.frameRect.topLeft,
      size = state.frameRect.size,
      tolerance = tolerance,
      option = option,
      modifier = Modifier.matchParentSize()
    )
  }
}

internal fun calculateImagePosition(bitmap: ImageBitmap, canvasSize: Size): Rect {
  val imageSize = calculateImageSize(bitmap, canvasSize)
  return Rect(
    Offset((canvasSize.width - imageSize.width) / 2, (canvasSize.height - imageSize.height) / 2),
    imageSize
  )
}

internal fun calculateImageSize(bitmap: ImageBitmap, canvasSize: Size): Size {
  return if (bitmap.width > bitmap.height)
    Size(canvasSize.width, canvasSize.width * bitmap.height / bitmap.width.toFloat())
  else
    Size(canvasSize.height * bitmap.width / bitmap.height.toFloat(), canvasSize.height)
}

internal fun calculateFrameRect(
  imageRect: Rect,
  canvasSize: Size,
  frameAspectRatio: AspectRatio?,
): Rect {
  val shortSide = min(imageRect.width, imageRect.height)
  return if (frameAspectRatio == null) {
    Rect(center = imageRect.center, radius = shortSide * 0.8f / 2)
  } else {
    val scale = shortSide / max(imageRect.width, imageRect.width * frameAspectRatio.value)
    val size = Size(imageRect.width * scale * 0.8f, imageRect.width * scale * frameAspectRatio.value * 0.8f)
    Rect(Offset((canvasSize.width - size.width) / 2, (canvasSize.height - size.height) / 2), size)
  }
}

internal fun detectTouchRegion(tapPosition: Offset, frameRect: Rect, tolerance: Float): TouchRegion? {
  return when {
    Rect(frameRect.topLeft, tolerance).contains(tapPosition) -> TouchRegion.Vertex.TOP_LEFT
    Rect(frameRect.topRight, tolerance).contains(tapPosition) -> TouchRegion.Vertex.TOP_RIGHT
    Rect(frameRect.bottomLeft, tolerance).contains(tapPosition) -> TouchRegion.Vertex.BOTTOM_LEFT
    Rect(frameRect.bottomRight, tolerance).contains(tapPosition) -> TouchRegion.Vertex.BOTTOM_RIGHT
    Rect(frameRect.center, frameRect.width / 2 - tolerance).contains(tapPosition) -> TouchRegion.Inside
    else -> null
  }
}