package com.chethan616.clearpdf.utils.text

/**
 * Colours for TextMate scopes, light and dark, shared by the screen (Compose) and the print page
 * (HTML) so both read the same. The hues are VS Code's Light+ and Dark+ themes (MIT), the ones its
 * own grammars are coloured with; the log levels get their own, so an error stands out.
 *
 * A token's scope may hold several names ("comment log.date"); the first entry below that any of
 * them starts with decides, so specific entries come before general ones.
 */
object SyntaxPalette {

    data class Style(val argb: Long, val bold: Boolean = false, val italic: Boolean = false)

    private class Entry(val prefix: String, val light: Style, val dark: Style)

    private fun e(prefix: String, light: Long, dark: Long, bold: Boolean = false, italic: Boolean = false) =
        Entry(prefix, Style(light, bold, italic), Style(dark, bold, italic))

    private val entries = listOf(
        e("log.error", 0xFFCD3131, 0xFFF14C4C, bold = true),
        e("log.warning", 0xFFB5651D, 0xFFE5A63C, bold = true),
        e("log.info", 0xFF098658, 0xFF6DC99B),
        e("log.debug", 0xFF0451A5, 0xFF75BEFF),
        e("log.verbose", 0xFF8A8F98, 0xFF8B949E),
        e("log.date", 0xFF8A8F98, 0xFF8B949E),
        e("log.exceptiontype", 0xFFCD3131, 0xFFF14C4C, italic = true),
        e("log.exception", 0xFF8A8F98, 0xFF8B949E, italic = true),
        e("log.string", 0xFFA31515, 0xFFCE9178),
        e("log.namespace", 0xFF267F99, 0xFF4EC9B0),
        e("log.constant", 0xFF098658, 0xFFB5CEA8),
        e("comment", 0xFF008000, 0xFF6A9955, italic = true),
        e("string", 0xFFA31515, 0xFFCE9178),
        e("constant.character.entity", 0xFFEE0000, 0xFFD7BA7D),
        e("constant.numeric", 0xFF098658, 0xFFB5CEA8),
        e("constant", 0xFF0000FF, 0xFF569CD6),
        e("support.type.property-name", 0xFF0451A5, 0xFF9CDCFE),
        e("keyword.other.definition", 0xFF0451A5, 0xFF9CDCFE),
        e("keyword", 0xFFAF00DB, 0xFFC586C0),
        e("storage", 0xFF0000FF, 0xFF569CD6),
        e("entity.name.tag", 0xFF800000, 0xFF569CD6),
        e("entity.name.section", 0xFF800000, 0xFF569CD6, bold = true),
        e("entity.other.attribute-name", 0xFFE50000, 0xFF9CDCFE),
        e("entity.other.document", 0xFF8A8F98, 0xFF8B949E, bold = true),
        e("entity.name.type", 0xFF267F99, 0xFF4EC9B0),
        e("entity.name.function", 0xFF795E26, 0xFFDCDCAA),
        e("variable", 0xFF001080, 0xFF9CDCFE),
        e("punctuation.definition.tag", 0xFF800000, 0xFF808080),
        e("markup.underline.link", 0xFF0451A5, 0xFF4493F8)
    )

    private val cacheLight = HashMap<String, Style?>()
    private val cacheDark = HashMap<String, Style?>()

    fun styleFor(scope: String, dark: Boolean): Style? {
        val cache = if (dark) cacheDark else cacheLight
        return synchronized(cache) {
            cache.getOrPut(scope) {
                val names = scope.split(' ')
                entries.firstOrNull { en -> names.any { it.startsWith(en.prefix) } }?.let { if (dark) it.dark else it.light }
            }
        }
    }

    /** `#rrggbb` for HTML. */
    fun hex(argb: Long): String = "#%06x".format(argb and 0xFFFFFF)
}
