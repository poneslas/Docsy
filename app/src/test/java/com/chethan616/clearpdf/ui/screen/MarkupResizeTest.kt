package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupResizeTest {

    private val box = Offset(200f, 100f)

    @Test
    fun dragAlongDiagonalScalesProportionally() {
        assertEquals(1.1f, diagonalScale(box, Offset(20f, 10f)), 1e-5f)
        assertEquals(0.5f, diagonalScale(box, Offset(-100f, -50f)), 1e-5f)
        assertEquals(1f, diagonalScale(box, Offset.Zero), 1e-5f)
    }

    @Test
    fun sidewaysJitterIsIgnored() {
        // (-10, 20) is perpendicular to the (200, 100) diagonal: the finger wobbles across it.
        assertEquals(1f, diagonalScale(box, Offset(-10f, 20f)), 1e-5f)
        // A drag along the diagonal plus the same wobble scales exactly as the clean drag.
        assertEquals(diagonalScale(box, Offset(20f, 10f)), diagonalScale(box, Offset(10f, 30f)), 1e-5f)
    }

    @Test
    fun degenerateBoxDoesNotProduceNaN() {
        val f = diagonalScale(Offset.Zero, Offset(5f, 5f))
        assertFalse(f.isNaN())
        assertFalse(f.isInfinite())
    }

    private fun text(fontSize: Float) =
        PdfMarkup.TextBoxMarkup(id = 1L, position = Offset(10f, 10f), text = "Подпись", color = Color.Black, fontSize = fontSize)

    private val start = Rect(10f, 10f, 210f, 110f)

    @Test
    fun textBoxScalesFontByDiagonalAndStaysInPlace() {
        val r = text(40f).resizedFrom(start, Offset(20f, 10f)) as PdfMarkup.TextBoxMarkup
        assertEquals(44f, r.fontSize, 1e-3f)
        assertEquals(Offset(10f, 10f), r.position)
    }

    @Test
    fun textBoxIgnoresSidewaysJitter() {
        val r = text(40f).resizedFrom(start, Offset(-10f, 20f)) as PdfMarkup.TextBoxMarkup
        assertEquals(40f, r.fontSize, 1e-3f)
    }

    @Test
    fun textBoxFontIsClamped() {
        val huge = text(100f).resizedFrom(start, Offset(5000f, 5000f)) as PdfMarkup.TextBoxMarkup
        assertEquals(480f, huge.fontSize, 1e-3f)
        val tiny = text(40f).resizedFrom(start, Offset(-1000f, -1000f)) as PdfMarkup.TextBoxMarkup
        assertEquals(8f, tiny.fontSize, 1e-3f)
        // The scale factor itself is capped at 10x even when the font cap is not reached.
        val capped = text(10f).resizedFrom(start, Offset(5000f, 5000f)) as PdfMarkup.TextBoxMarkup
        assertEquals(100f, capped.fontSize, 1e-3f)
    }

    @Test
    fun rectangleKeepsTopLeftAndMinimumSize() {
        val rect = PdfMarkup.RectMarkup(start = Offset(10f, 10f), end = Offset(110f, 60f), color = Color.Red)
        val r = rect.resizedFrom(Rect(10f, 10f, 110f, 60f), Offset(30f, -100f)) as PdfMarkup.RectMarkup
        assertEquals(Offset(10f, 10f), r.start)
        assertEquals(140f, r.end.x, 1e-3f)
        assertEquals(18f, r.end.y, 1e-3f)
        assertTrue(r.end.y > r.start.y)
    }
}
