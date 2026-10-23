package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * Find for the text and Markdown viewers: a match is a range in one piece of text ([key]: a line
 * index, or the Markdown text object itself) shown in list row [item]. Matches are drawn as a
 * background over whatever styling the text already has, in the Word viewer's colours.
 */
internal data class FindHit(val key: Any, val item: Int, val range: IntRange)

/** Every case-insensitive occurrence of [q] in [text], appended to [out] until it holds [max]. */
internal inline fun findIn(text: String, q: String, out: MutableList<FindHit>, max: Int, hit: (IntRange) -> FindHit) {
    var i = text.indexOf(q, ignoreCase = true)
    while (i >= 0 && out.size < max) {
        out.add(hit(i until i + q.length))
        i = text.indexOf(q, i + q.length, ignoreCase = true)
    }
}

internal const val MaxFindHits = 5000

/** [this] with [hits] marked, [current] stronger. Returns [this] untouched when there are none. */
internal fun AnnotatedString.withHits(hits: List<IntRange>?, current: IntRange?, dark: Boolean): AnnotatedString {
    if (hits.isNullOrEmpty()) return this
    val hitBg = if (dark) Color(0x66FFD60A) else Color(0x80FFD60A)
    val curBg = Color(0xFFFF9F0A).copy(alpha = if (dark) 0.75f else 0.6f)
    return AnnotatedString.Builder(this).apply {
        for (h in hits) {
            val s = h.first.coerceIn(0, length)
            val e = (h.last + 1).coerceIn(0, length)
            if (s < e) addStyle(SpanStyle(background = if (h == current) curBg else hitBg), s, e)
        }
    }.toAnnotatedString()
}
