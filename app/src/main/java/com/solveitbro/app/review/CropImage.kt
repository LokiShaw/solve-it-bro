package com.solveitbro.app.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/** Shows [bitmap] fitted to the available space with a draggable crop box on top. */
@Composable
fun CropImage(
    bitmap: ImageBitmap,
    crop: CropRect,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCropChange by rememberUpdatedState(onCropChange)
    val touchSlop = with(LocalDensity.current) { 32.dp.toPx() }

    BoxWithConstraints(modifier) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / bitmap.width, boxH / bitmap.height)
        val imgW = bitmap.width * scale
        val imgH = bitmap.height * scale
        val offX = (boxW - imgW) / 2
        val offY = (boxH - imgH) / 2
        var handle by remember { mutableStateOf<CropRect.Handle?>(null) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imgW, imgH, offX, offY) {
                    detectDragGestures(
                        onDragStart = { p ->
                            handle = currentCrop.handleAt(
                                x = (p.x - offX) / imgW,
                                y = (p.y - offY) / imgH,
                                slopX = touchSlop / imgW,
                                slopY = touchSlop / imgH,
                            )
                        },
                        onDragEnd = { handle = null },
                        onDragCancel = { handle = null },
                        onDrag = { change, amount ->
                            val active = handle
                            if (active != null) {
                                change.consume()
                                currentOnCropChange(currentCrop.drag(active, amount.x / imgW, amount.y / imgH))
                            }
                        },
                    )
                },
        ) {
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(offX.roundToInt(), offY.roundToInt()),
                dstSize = IntSize(imgW.roundToInt(), imgH.roundToInt()),
            )

            val l = offX + crop.left * imgW
            val t = offY + crop.top * imgH
            val r = offX + crop.right * imgW
            val b = offY + crop.bottom * imgH
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset(offX, offY), Size(imgW, t - offY))
            drawRect(dim, Offset(offX, b), Size(imgW, offY + imgH - b))
            drawRect(dim, Offset(offX, t), Size(l - offX, b - t))
            drawRect(dim, Offset(r, t), Size(offX + imgW - r, b - t))
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
            for (corner in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
                drawCircle(Color.White, radius = 9.dp.toPx(), center = corner)
            }
        }
    }
}
