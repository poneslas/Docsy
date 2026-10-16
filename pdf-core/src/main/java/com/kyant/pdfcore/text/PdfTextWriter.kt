package com.kyant.pdfcore.text

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.util.Matrix

/**
 * Writes new text onto a page (text boxes, watermarks) in a face that can draw it: a standard
 * font for Latin, an embedded system face for Cyrillic, Greek and the rest of what a TrueType font
 * covers ([PdfFontResolver]), and Android's own layout as an image for scripts no PDF font here can
 * draw ([PdfTextImageFallback]). Text is never replaced with `?`.
 *
 * Make one per document, so each embedded face is loaded (and subset) once however many pages use it.
 */
class PdfTextWriter(private val doc: PDDocument, private val style: PdfFontStyle = PdfFontStyle.Regular) {

    private val fonts = PdfFontResolver(doc)

    /** Width in points of [text] at [size], as [drawLine] draws it. */
    fun width(text: String, size: Float): Float {
        if (text.isEmpty()) return 0f
        val font = fonts.forText(text, style)
            ?: return PdfTextImageFallback.measure(text, size, style.bold, style.italic, style.serif, style.mono)
        return runCatching { font.getStringWidth(text) / 1000f * size }.getOrDefault(0f)
    }

    /**
     * Draws one line of [text] at [size] with its baseline starting at ([x], [y]) in the current
     * user space, in colour [rgb]. Call it outside a text object (no open `BT`).
     */
    fun drawLine(cs: PDPageContentStream, text: String, x: Float, y: Float, size: Float, rgb: Int) {
        if (text.isEmpty() || size <= 0f) return
        val font = fonts.forText(text, style)
        if (font == null) {
            PdfTextImageFallback.draw(doc, cs, text, Matrix(size, 0f, 0f, size, x, y), rgb, style.bold, style.italic, style.serif, style.mono)
            return
        }
        cs.beginText()
        cs.setNonStrokingColor((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
        cs.setFont(font, size)
        cs.newLineAtOffset(x, y)
        cs.showText(text)
        cs.endText()
    }
}
