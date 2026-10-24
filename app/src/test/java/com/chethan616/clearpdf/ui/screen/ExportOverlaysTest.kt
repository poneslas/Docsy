package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.chethan616.clearpdf.ui.viewmodel.ExportOverlay
import com.chethan616.clearpdf.ui.viewmodel.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportOverlaysTest {

    private fun assertPoint(x: Float, y: Float, p: NormalizedPoint) {
        assertEquals(x, p.x, 1e-5f)
        assertEquals(y, p.y, 1e-5f)
    }

    private val rect = PdfMarkup.RectMarkup(start = Offset(100f, 200f), end = Offset(300f, 600f), color = Color.Red, alpha = 0.5f)
    private val text = PdfMarkup.TextBoxMarkup(id = 7L, position = Offset(500f, 1000f), text = "Иванов", color = Color.Blue, fontSize = 40f)

    @Test
    fun pageFillingTheCanvasMapsToNormalizedCoordinates() {
        val overlays = buildExportOverlays(
            annotationsByPage = mapOf(0 to listOf(rect, text)),
            ocrBlocksByPage = emptyMap(),
            pageCanvasSizes = mapOf(0 to Size(1000f, 2000f)),
            pageBitmapSizes = mapOf(0 to Size(500f, 1000f))
        )
        val list = overlays.getValue(0)
        assertEquals(2, list.size)

        val r = list[0] as ExportOverlay.RectShape
        assertPoint(0.1f, 0.1f, r.start)
        assertPoint(0.3f, 0.3f, r.end)
        assertEquals(Color.Red.toArgb(), r.colorArgb)
        assertEquals(0.5f, r.alpha, 0f)

        val t = list[1] as ExportOverlay.TextStamp
        assertPoint(0.5f, 0.5f, t.position)
        assertEquals("Иванов", t.text)
        assertEquals(Color.Blue.toArgb(), t.colorArgb)
        // Font size is relative to the page height.
        assertEquals(0.02f, t.fontSizeNorm, 1e-6f)
    }

    @Test
    fun letterboxedPageSubtractsTheMarginsAndClampsToThePage() {
        // A portrait page (1:2) on a square canvas sits in a 500px-wide frame starting at x = 250.
        val inFrame = PdfMarkup.RectMarkup(start = Offset(300f, 100f), end = Offset(500f, 500f), color = Color.Red)
        val outside = PdfMarkup.RectMarkup(start = Offset(0f, -50f), end = Offset(900f, 1200f), color = Color.Red)
        val list = buildExportOverlays(
            annotationsByPage = mapOf(3 to listOf(inFrame, outside)),
            ocrBlocksByPage = emptyMap(),
            pageCanvasSizes = mapOf(3 to Size(1000f, 1000f)),
            pageBitmapSizes = mapOf(3 to Size(500f, 1000f))
        ).getValue(3)

        val a = list[0] as ExportOverlay.RectShape
        assertPoint(0.1f, 0.1f, a.start)
        assertPoint(0.5f, 0.5f, a.end)
        val b = list[1] as ExportOverlay.RectShape
        assertPoint(0f, 0f, b.start)
        assertPoint(1f, 1f, b.end)
    }

    @Test
    fun pagesWithoutSizeOrMarkupsAndBlankTextAreSkipped() {
        val blank = text.copy(text = "   ")
        val overlays = buildExportOverlays(
            annotationsByPage = mapOf(0 to listOf(rect), 1 to emptyList(), 2 to listOf(blank)),
            ocrBlocksByPage = emptyMap(),
            pageCanvasSizes = mapOf(1 to Size(100f, 100f), 2 to Size(100f, 100f)),
            pageBitmapSizes = emptyMap()
        )
        assertTrue(overlays.isEmpty())
    }
}
