package com.kyant.pdfcore.text

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont
import java.io.File

/** The look a fallback face should match: weight, slant and family. */
data class PdfFontStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val serif: Boolean = false,
    val mono: Boolean = false
) {
    companion object {
        val Regular = PdfFontStyle()
        val Bold = PdfFontStyle(bold = true)

        /** The style of an existing PDF font, read from its name and descriptor. */
        fun of(font: PDFont?): PdfFontStyle {
            val name = (font?.name ?: "").substringAfter('+').lowercase()
            val d = font?.fontDescriptor
            val bold = listOf("bold", "black", "heavy", "semibold", "demi").any { name.contains(it) } ||
                d?.isForceBold == true || (d?.fontWeight ?: 400f) >= 600f
            val italic = name.contains("italic") || name.contains("oblique") || d?.isItalic == true || (d?.italicAngle ?: 0f) != 0f
            val mono = listOf("courier", "mono", "consol", "menlo").any { name.contains(it) } || d?.isFixedPitch == true
            val serif = !mono && (d?.isSerif == true ||
                listOf("times", "serif", "georgia", "garamond", "cambria", "book", "minion", "palatino", "baskerville").any { name.contains(it) } &&
                !name.contains("sans"))
            return PdfFontStyle(bold, italic, serif, mono)
        }
    }
}

/**
 * Picks a PDF font that can draw a string, for one document.
 *
 * The 14 standard fonts (Helvetica, Times, Courier) are tried first: they are never embedded, so
 * Latin text costs nothing. They only cover WinAnsi (about Latin-1), so anything beyond it
 * (Cyrillic, Greek, Latin Extended…) gets a subset of a system TrueType face of the same style
 * embedded instead, which keeps the text real: selectable, searchable and copyable.
 *
 * Returns null when no face here can draw the text; callers then fall back to
 * [PdfTextImageFallback] (scripts that need shaping are drawn as an image by Android).
 */
class PdfFontResolver(private val doc: PDDocument) {

    private val loaded = HashMap<String, PDFont?>()

    fun forText(text: String, style: PdfFontStyle = PdfFontStyle.Regular): PDFont? {
        val std = standard(style)
        if (canShow(std, text)) return std
        val weight = when { style.bold && style.italic -> "BoldItalic"; style.bold -> "Bold"; style.italic -> "Italic"; else -> "Regular" }
        val names = buildList {
            if (style.serif) { add("NotoSerif-$weight.ttf"); add("NotoSerif-Regular.ttf") }
            if (style.mono) { add("DroidSansMono.ttf"); add("CutiveMono.ttf") }
            add("Roboto-$weight.ttf"); add("RobotoStatic-$weight.ttf"); add("Roboto-Regular.ttf")
            add("NotoSans-$weight.ttf"); add("NotoSans-Regular.ttf"); add("DroidSans.ttf")
        }
        for (n in names.distinct()) {
            val font = loaded.getOrPut(n) {
                val f = File("/system/fonts/$n")
                if (f.exists()) runCatching { PDType0Font.load(doc, f) }.getOrNull() else null
            } ?: continue
            if (canShow(font, text)) return font
        }
        return null
    }

    companion object {
        fun standard(style: PdfFontStyle): PDFont = when {
            style.mono -> when { style.bold && style.italic -> PDType1Font.COURIER_BOLD_OBLIQUE; style.bold -> PDType1Font.COURIER_BOLD; style.italic -> PDType1Font.COURIER_OBLIQUE; else -> PDType1Font.COURIER }
            style.serif -> when { style.bold && style.italic -> PDType1Font.TIMES_BOLD_ITALIC; style.bold -> PDType1Font.TIMES_BOLD; style.italic -> PDType1Font.TIMES_ITALIC; else -> PDType1Font.TIMES_ROMAN }
            else -> when { style.bold && style.italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE; style.bold -> PDType1Font.HELVETICA_BOLD; style.italic -> PDType1Font.HELVETICA_OBLIQUE; else -> PDType1Font.HELVETICA }
        }

        /** Whether [font] can draw every character of [text] (embedded subsets often can't). */
        fun canShow(font: PDFont, text: String): Boolean = try {
            if (font is PDType3Font || font.isVertical) false
            else {
                var ok = true
                var i = 0
                while (i < text.length && ok) {
                    val cp = text.codePointAt(i)
                    i += Character.charCount(cp)
                    val bytes = font.encode(String(Character.toChars(cp)))
                    if (cp != ' '.code) {
                        var code = 0
                        for (b in bytes) code = (code shl 8) or (b.toInt() and 0xFF)
                        if (font is PDVectorFont && !font.hasGlyph(code)) ok = false
                        else if (font.getWidth(code) <= 0f) ok = false
                    }
                }
                ok
            }
        } catch (_: Exception) {
            false
        }
    }
}
