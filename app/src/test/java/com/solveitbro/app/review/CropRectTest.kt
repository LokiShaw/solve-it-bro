package com.solveitbro.app.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropRectTest {
    private val rect = CropRect(0.2f, 0.2f, 0.8f, 0.8f)

    @Test
    fun `corner touch grabs the corner and inside touch moves`() {
        assertEquals(CropRect.Handle.TopLeft, rect.handleAt(0.21f, 0.19f, 0.05f, 0.05f))
        assertEquals(CropRect.Handle.BottomRight, rect.handleAt(0.8f, 0.8f, 0.05f, 0.05f))
        assertEquals(CropRect.Handle.Move, rect.handleAt(0.5f, 0.5f, 0.05f, 0.05f))
        assertNull(rect.handleAt(0.05f, 0.5f, 0.05f, 0.05f))
    }

    @Test
    fun `moving keeps the box inside the image`() {
        val moved = rect.drag(CropRect.Handle.Move, 0.5f, -0.5f)
        assertRect(CropRect(0.4f, 0f, 1f, 0.6f), moved)
    }

    @Test
    fun `resizing never goes below the minimum size or past the edge`() {
        val squashed = rect.drag(CropRect.Handle.TopLeft, 0.9f, 0.9f)
        assertEquals(0.8f - CropRect.MIN_SIZE, squashed.left, 1e-6f)
        assertEquals(0.8f - CropRect.MIN_SIZE, squashed.top, 1e-6f)
        val grown = rect.drag(CropRect.Handle.BottomRight, 1f, 1f)
        assertRect(CropRect(0.2f, 0.2f, 1f, 1f), grown)
    }

    @Test
    fun `pixels stay inside the bitmap`() {
        assertEquals(CropRect.Pixels(200, 100, 600, 300), rect.toPixels(1000, 500))
        val full = CropRect(0f, 0f, 1f, 1f).toPixels(7, 3)
        assertEquals(CropRect.Pixels(0, 0, 7, 3), full)
    }

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, 1e-5f)
        assertEquals(expected.top, actual.top, 1e-5f)
        assertEquals(expected.right, actual.right, 1e-5f)
        assertEquals(expected.bottom, actual.bottom, 1e-5f)
    }
}
