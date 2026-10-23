package com.chethan616.clearpdf.utils.text

/**
 * "Format" for JSON and XML: re-indents the text without parsing it into a tree, so nothing about
 * the content changes (key order, duplicate keys, number spelling, comments) and a file that is
 * not quite valid still formats instead of failing. A minified one-line file becomes readable.
 */
object TextFormat {

    private const val Indent = "  "

    fun json(src: String): String {
        val out = StringBuilder(src.length + src.length / 2)
        var depth = 0
        var i = 0
        val n = src.length
        fun newline() { out.append('\n'); repeat(depth) { out.append(Indent) } }
        while (i < n) {
            val c = src[i]
            when {
                c == '"' -> {
                    // A string verbatim, escapes included.
                    val start = i++
                    while (i < n && src[i] != '"') { if (src[i] == '\\') i++; i++ }
                    out.append(src, start, (i + 1).coerceAtMost(n))
                }
                c == '/' && i + 1 < n && src[i + 1] == '/' -> {
                    val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                    out.append(src, i, end).also { i = end - 1 }
                    if (end < n) newline()
                }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                    out.append(src, i, end); i = end - 1
                }
                c == '{' || c == '[' -> {
                    // An empty object or array stays on one line.
                    var j = i + 1
                    while (j < n && src[j].isWhitespace()) j++
                    if (j < n && (src[j] == '}' || src[j] == ']')) { out.append(c).append(src[j]); i = j }
                    else { out.append(c); depth++; newline() }
                }
                c == '}' || c == ']' -> { depth = (depth - 1).coerceAtLeast(0); trimTrailingSpace(out); newline(); out.append(c) }
                c == ',' -> { out.append(','); newline() }
                c == ':' -> out.append(": ")
                c.isWhitespace() -> Unit
                else -> out.append(c)
            }
            i++
        }
        trimTrailingSpace(out)
        return out.toString()
    }

    private fun trimTrailingSpace(sb: StringBuilder) {
        var end = sb.length
        while (end > 0 && (sb[end - 1] == ' ' || sb[end - 1] == '\n')) end--
        sb.setLength(end)
    }

    private val xmlToken = Regex("""<!--.*?-->|<!\[CDATA\[.*?\]\]>|<\?.*?\?>|<![^>]*>|<[^>]+>|[^<]+""", RegexOption.DOT_MATCHES_ALL)

    fun xml(src: String): String {
        val out = StringBuilder(src.length + src.length / 2)
        var depth = 0
        var lastWasOpen = false
        var pendingText: String? = null
        fun line(s: String) {
            if (out.isNotEmpty()) out.append('\n')
            repeat(depth) { out.append(Indent) }
            out.append(s)
        }
        for (m in xmlToken.findAll(src)) {
            val t = m.value
            when {
                !t.startsWith("<") -> {
                    val text = t.trim()
                    if (text.isNotEmpty()) pendingText = text
                }
                t.startsWith("</") -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    val text = pendingText
                    // <a>text</a> stays on one line.
                    if (lastWasOpen && text != null && !text.contains('\n')) out.append(text).append(t)
                    else { text?.let { depth++; line(it); depth-- }; line(t) }
                    pendingText = null
                    lastWasOpen = false
                }
                t.startsWith("<!--") || t.startsWith("<?") || t.startsWith("<!") || t.endsWith("/>") -> {
                    pendingText?.let { line(it) }; pendingText = null
                    line(t); lastWasOpen = false
                }
                else -> {
                    pendingText?.let { line(it) }; pendingText = null
                    line(t); depth++; lastWasOpen = true
                }
            }
        }
        pendingText?.let { line(it) }
        return out.toString()
    }
}
