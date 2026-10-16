package com.chethan616.clearpdf.imageeditor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic "photos": cream paper lit unevenly, a blue pen stroke, and the things a real photo of a
 * signature brings along — a warm desk at the edge, a dark patch, a speck.
 */
class SignatureExtractorTest {

    private val w = 400
    private val h = 300

    /** Cream paper, brighter on the left than on the right (a soft shadow across the sheet). */
    private fun paper(): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val light = 1f - 0.35f * x / w
        rgb((245 * light).toInt(), (238 * light).toInt(), (215 * light).toInt())
    }

    private fun IntArray.rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        for (y in y0 until y1) for (x in x0 until x1) this[y * w + x] = c
    }

    /** A zig-zag pen line 4 px thick between x = 100 and x = 300. */
    private fun IntArray.stroke(c: Int) {
        for (x in 100 until 300) {
            val y = 150 + ((x / 20) % 2 * 2 - 1) * ((x % 20) - 10) * 3
            for (dy in -2 until 2) this[(y + dy) * w + x] = c
        }
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun alphaAt(r: SignatureExtractor.Result, x: Int, y: Int) = (r.pixels[y * r.width + x] ushr 24) and 0xFF

    @Test fun keepsTheInkAndMakesThePaperTransparent() {
        val px = paper().apply { stroke(rgb(30, 40, 120)) }
        val r = SignatureExtractor.extract(px, w, h)!!
        // Trimmed to the stroke (200 px wide plus a small margin), not the whole sheet.
        assertTrue("width ${r.width}", r.width in 200..230)
        assertTrue("height ${r.height}", r.height < 120)
        // Corners are paper: transparent. Some pixel is solid ink.
        assertEquals(0, alphaAt(r, 0, 0))
        assertEquals(0, alphaAt(r, r.width - 1, r.height - 1))
        assertTrue(r.pixels.any { (it ushr 24) == 0xFF })
        // The pen's own colour is kept: blue, not black.
        val b = r.inkRgb and 0xFF; val red = (r.inkRgb shr 16) and 0xFF
        assertTrue("ink #%06x".format(r.inkRgb), b > red + 40)
    }

    @Test fun dropsTheDeskTheShadowPatchAndSpecks() {
        val px = paper().apply {
            stroke(rgb(30, 40, 120))
            rect(0, 260, w, h, rgb(150, 100, 60))     // warm desk along the bottom edge
            rect(20, 20, 70, 70, rgb(60, 60, 60))      // a filled dark patch on the sheet
            rect(350, 40, 352, 42, rgb(20, 20, 20))    // a speck
        }
        val r = SignatureExtractor.extract(px, w, h)!!
        assertTrue("width ${r.width}", r.width in 200..230)
        assertTrue("height ${r.height}", r.height < 120)
    }

    @Test fun solidInkRecolours() {
        val px = paper().apply { stroke(rgb(30, 40, 120)) }
        val r = SignatureExtractor.extract(px, w, h, SignatureExtractor.Params(ink = SignatureExtractor.Ink.Solid(0x000000)))!!
        assertEquals(0, r.inkRgb)
        assertTrue(r.pixels.all { it and 0xFFFFFF == 0 })
    }

    @Test fun blankPaperHasNoSignature() {
        assertNull(SignatureExtractor.extract(paper(), w, h))
    }
}
