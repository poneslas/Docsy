package com.chethan616.clearpdf.utils.text

import com.chethan616.clearpdf.utils.markdown.MarkdownHtml

/**
 * HTML for highlighted text: [highlighted] for a code block inside another page (Markdown), and
 * [page] for the text viewer's print page, A4, monospace, long lines wrapped. Paper is always
 * light, so it always uses the light palette. All text is escaped.
 */
object TextHtml {

    fun highlighted(text: String, grammar: Grammar?): String {
        if (grammar == null) return MarkdownHtml.esc(text)
        val sb = StringBuilder(text.length + text.length / 4)
        var state = LineState.Initial
        text.split('\n').forEachIndexed { i, line ->
            if (i > 0) sb.append('\n')
            val lt = Highlighter.tokenize(grammar, line, state)
            state = lt.endState
            var pos = 0
            for (t in lt.tokens) {
                if (t.start > pos) sb.append(MarkdownHtml.esc(line.substring(pos, t.start)))
                val st = SyntaxPalette.styleFor(t.scope, dark = false)
                val body = MarkdownHtml.esc(line.substring(t.start, t.end))
                if (st == null) sb.append(body)
                else {
                    sb.append("<span style=\"color:").append(SyntaxPalette.hex(st.argb))
                    if (st.bold) sb.append(";font-weight:600")
                    if (st.italic) sb.append(";font-style:italic")
                    sb.append("\">").append(body).append("</span>")
                }
                pos = t.end
            }
            if (pos < line.length) sb.append(MarkdownHtml.esc(line.substring(pos)))
        }
        return sb.toString()
    }

    fun page(text: String, grammar: Grammar?, title: String): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>").append(MarkdownHtml.esc(title)).append("</title><style>")
        append(
            """
            @page { size: A4; margin: 14mm 12mm 16mm; }
            html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            body { margin: 0; color: #1f2328; }
            pre { margin: 0; font-family: monospace; font-size: 8.5pt; line-height: 1.45;
                  white-space: pre-wrap; overflow-wrap: anywhere; tab-size: 4; }
            """.trimIndent()
        )
        append("</style></head><body><pre>")
        append(highlighted(text, grammar))
        append("</pre></body></html>")
    }
}
