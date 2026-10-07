package com.solveitbro.app.review

import kotlin.math.abs
import kotlin.math.roundToInt

/** Crop box in image coordinates normalised to 0..1, so it survives any display size. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Move }

    data class Pixels(val x: Int, val y: Int, val width: Int, val height: Int)

    /** Which handle a touch at ([x], [y]) grabs; corners win within [slopX]/[slopY]. */
    fun handleAt(x: Float, y: Float, slopX: Float, slopY: Float): Handle? {
        val corners = listOf(
            Handle.TopLeft to (left to top),
            Handle.TopRight to (right to top),
            Handle.BottomLeft to (left to bottom),
            Handle.BottomRight to (right to bottom),
        )
        corners.firstOrNull { (_, c) -> abs(x - c.first) <= slopX && abs(y - c.second) <= slopY }
            ?.let { return it.first }
        return if (x in left..right && y in top..bottom) Handle.Move else null
    }

    fun drag(handle: Handle, dx: Float, dy: Float): CropRect = when (handle) {
        Handle.Move -> {
            val x = (left + dx).coerceIn(0f, 1f - width)
            val y = (top + dy).coerceIn(0f, 1f - height)
            CropRect(x, y, x + width, y + height)
        }
        Handle.TopLeft -> copy(left = moveLeft(dx), top = moveTop(dy))
        Handle.TopRight -> copy(right = moveRight(dx), top = moveTop(dy))
        Handle.BottomLeft -> copy(left = moveLeft(dx), bottom = moveBottom(dy))
        Handle.BottomRight -> copy(right = moveRight(dx), bottom = moveBottom(dy))
    }

    fun toPixels(imageWidth: Int, imageHeight: Int): Pixels {
        val x = (left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
        val y = (top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
        val w = (width * imageWidth).roundToInt().coerceIn(1, imageWidth - x)
        val h = (height * imageHeight).roundToInt().coerceIn(1, imageHeight - y)
        return Pixels(x, y, w, h)
    }

    private fun moveLeft(dx: Float) = (left + dx).coerceIn(0f, right - MIN_SIZE)
    private fun moveRight(dx: Float) = (right + dx).coerceIn(left + MIN_SIZE, 1f)
    private fun moveTop(dy: Float) = (top + dy).coerceIn(0f, bottom - MIN_SIZE)
    private fun moveBottom(dy: Float) = (bottom + dy).coerceIn(top + MIN_SIZE, 1f)

    companion object {
        const val MIN_SIZE = 0.1f
        val Default = CropRect(0.05f, 0.05f, 0.95f, 0.95f)
    }
}
