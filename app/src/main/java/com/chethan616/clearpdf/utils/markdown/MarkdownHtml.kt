package com.chethan616.clearpdf.utils.markdown

import com.chethan616.clearpdf.utils.text.Grammars
import com.chethan616.clearpdf.utils.text.TextHtml

/**
 * The print page for an [MdDocument]: self-contained HTML (no scripts, no remote resources) laid
 * out for A4 paper. It is built from the same blocks the screen draws, never from the source, and
 * every piece of document text is escaped, so a file can't inject markup into it.
 */
object MarkdownHtml {

    fun render(doc: MdDocument, title: String): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>")
        append(esc(title))
        append("</title><style>")
        append(CSS)
        append("</style></head><body>")
        doc.blocks.forEach { block(it) }
        append("</body></html>")
    }

    private fun StringBuilder.block(b: MdBlock) {
        when (b) {
            is MdBlock.Heading -> append("<h${b.level} id=\"${esc(b.anchor)}\">").inline(b.text).append("</h${b.level}>")
            is MdBlock.Paragraph -> append("<p>").inline(b.text).append("</p>")
            // Highlighted with the text viewer's rules; the highlighter escapes the text itself.
            is MdBlock.Code -> append("<pre><code>").append(TextHtml.highlighted(b.code, Grammars.forLanguage(b.language))).append("</code></pre>")
            is MdBlock.FrontMatter -> append("<pre><code>").append(TextHtml.highlighted(b.raw, Grammars.Yaml)).append("</code></pre>")
            is MdBlock.Html -> append("<pre><code>").append(TextHtml.highlighted(b.raw, Grammars.Xml)).append("</code></pre>")
            is MdBlock.Quote -> { append("<blockquote>"); b.blocks.forEach { block(it) }; append("</blockquote>") }
            is MdBlock.Rule -> append("<hr>")
            is MdBlock.ListBlock -> {
                val tag = if (b.ordered) "ol" else "ul"
                append('<').append(tag)
                if (b.ordered && b.start != 1) append(" start=\"").append(b.start).append('"')
                if (b.items.any { it.checked != null }) append(" class=\"tasks\"")
                append('>')
                for (item in b.items) {
                    append("<li>")
                    item.checked?.let { append(if (it) "<span class=\"box on\">&#10003;</span>" else "<span class=\"box\"></span>") }
                    item.blocks.forEachIndexed { i, child ->
                        // A tight list's paragraphs sit inline in the item, as a browser renders them.
                        if (!b.loose && child is MdBlock.Paragraph) {
                            if (i > 0) append("<br>")
                            inline(child.text)
                        } else block(child)
                    }
                    append("</li>")
                }
                append("</").append(tag).append('>')
            }
            is MdBlock.Table -> {
                append("<div class=\"table").append(if (b.align.size > 8) " wide" else "").append("\"><table><thead><tr>")
                b.header.forEachIndexed { i, cell -> append("<th").align(b.align[i]).append('>').inline(cell).append("</th>") }
                append("</tr></thead><tbody>")
                for (row in b.rows) {
                    append("<tr>")
                    row.forEachIndexed { i, cell -> append("<td").align(b.align[i]).append('>').inline(cell).append("</td>") }
                    append("</tr>")
                }
                append("</tbody></table></div>")
            }
        }
    }

    private fun StringBuilder.align(a: MdAlign): StringBuilder = when (a) {
        MdAlign.Start -> this
        MdAlign.Center -> append(" style=\"text-align:center\"")
        MdAlign.End -> append(" style=\"text-align:right\"")
    }

    /**
     * Spans nest or sit apart (that is how [MarkdownReader] emits them), so walking the text with a
     * stack of open spans yields well-formed tags.
     */
    private fun StringBuilder.inline(t: MdText): StringBuilder {
        val opens = t.spans.withIndex().sortedWith(compareBy({ it.value.start }, { -it.value.end }, { it.index }))
        val stack = ArrayDeque<MdSpan>()
        var next = 0
        fun closeUpTo(pos: Int) {
            while (stack.isNotEmpty() && stack.last().end <= pos) append(closeTag(stack.removeLast().style))
        }
        for (i in 0..t.text.length) {
            closeUpTo(i)
            while (next < opens.size && opens[next].value.start == i) {
                val s = opens[next++].value
                if (s.style is MdStyle.Image) {
                    // Offline: a picture can only be shown when it is inline data.
                    if (s.style.url.startsWith("data:image/")) append("<img src=\"").append(esc(s.style.url)).append("\" alt=\"")
                        .append(esc(t.text.substring(s.start, s.end))).append("\">")
                    else append("<span class=\"img\">").append(esc(t.text.substring(s.start, s.end).ifBlank { "image" })).append("</span>")
                    continue
                }
                append(openTag(s.style))
                stack.addLast(s)
            }
            if (i < t.text.length && !insideImage(t, i)) {
                val c = t.text[i]
                if (c == '\n') append("<br>") else append(escChar(c))
            }
        }
        closeUpTo(Int.MAX_VALUE)
        return this
    }

    private fun insideImage(t: MdText, i: Int) = t.spans.any { it.style is MdStyle.Image && i >= it.start && i < it.end }

    private fun openTag(s: MdStyle): String = when (s) {
        MdStyle.Bold -> "<strong>"
        MdStyle.Italic -> "<em>"
        MdStyle.Strike -> "<del>"
        MdStyle.Code -> "<code>"
        MdStyle.Mark -> "<mark>"
        is MdStyle.Link -> "<a href=\"${esc(s.url)}\">"
        is MdStyle.Image -> ""
    }

    private fun closeTag(s: MdStyle): String = when (s) {
        MdStyle.Bold -> "</strong>"
        MdStyle.Italic -> "</em>"
        MdStyle.Strike -> "</del>"
        MdStyle.Code -> "</code>"
        MdStyle.Mark -> "</mark>"
        is MdStyle.Link -> "</a>"
        is MdStyle.Image -> ""
    }

    internal fun esc(s: String): String = buildString(s.length) { s.forEach { append(escChar(it)) } }

    private fun escChar(c: Char): String = when (c) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        '"' -> "&quot;"
        '\'' -> "&#39;"
        else -> c.toString()
    }

    /**
     * Paper only, so always light. Wide tables can't scroll on paper: their cells wrap, the type
     * gets smaller, and a table with many columns is scaled down to fit the page width.
     */
    private val CSS = """
        @page { size: A4; margin: 16mm 15mm 18mm; }
        html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
        body { font-family: sans-serif; font-size: 11pt; line-height: 1.5; color: #1f2328; margin: 0; overflow-wrap: break-word; }
        h1, h2, h3, h4, h5, h6 { line-height: 1.25; margin: 1.2em 0 .5em; break-after: avoid; }
        h1 { font-size: 1.9em; padding-bottom: .25em; border-bottom: 1px solid #d1d9e0; }
        h2 { font-size: 1.45em; padding-bottom: .2em; border-bottom: 1px solid #d1d9e0; }
        h3 { font-size: 1.2em; } h4 { font-size: 1em; } h5 { font-size: .9em; } h6 { font-size: .85em; color: #59636e; }
        body > :first-child { margin-top: 0; }
        p, ul, ol, blockquote, pre, .table { margin: 0 0 .8em; }
        ul, ol { padding-left: 1.6em; }
        ul.tasks { list-style: none; padding-left: .4em; }
        .box { display: inline-block; width: .85em; height: .85em; border: 1.5px solid #59636e; border-radius: 3px;
               margin-right: .45em; vertical-align: -.1em; text-align: center; font-size: .8em; line-height: .9em; }
        .box.on { background: #0969da; border-color: #0969da; color: #fff; }
        blockquote { margin-left: 0; padding: 0 1em; color: #59636e; border-left: .25em solid #d1d9e0; }
        code { font-family: monospace; font-size: .88em; background: #eff1f3; border-radius: 4px; padding: .1em .3em; }
        pre { background: #f6f8fa; border-radius: 6px; padding: .8em 1em; white-space: pre-wrap; overflow-wrap: anywhere; }
        pre code { background: none; padding: 0; font-size: .85em; }
        a { color: #0969da; text-decoration: underline; }
        mark { background: #fff8c5; }
        hr { border: 0; border-top: 2px solid #d1d9e0; margin: 1.2em 0; }
        img { max-width: 100%; }
        .img { color: #59636e; font-style: italic; }
        .img::before { content: "\1F5BC  "; font-style: normal; }
        .table table { border-collapse: collapse; width: 100%; font-size: 9.5pt; font-variant-numeric: tabular-nums; }
        .table th, .table td { border: 1px solid #c8d1da; padding: 4px 7px; vertical-align: top; text-align: left;
               overflow-wrap: break-word; }
        .table a, .table code { overflow-wrap: anywhere; }
        .table th { background: #f0f3f6; font-weight: 600; }
        .table tbody tr:nth-child(2n) td { background: #f8f9fb; }
        .table.wide table { font-size: 8pt; }
        .table.wide th, .table.wide td { padding: 2px 4px; }
        thead { display: table-header-group; }
        tr, img { break-inside: avoid; }
    """.trimIndent()
}
