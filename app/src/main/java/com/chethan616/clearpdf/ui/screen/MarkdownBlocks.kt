package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.utils.markdown.MdAlign
import com.chethan616.clearpdf.utils.markdown.MdBlock
import com.chethan616.clearpdf.utils.markdown.MdSpan
import com.chethan616.clearpdf.utils.markdown.MdStyle
import com.chethan616.clearpdf.utils.markdown.MdText

/** The page's colours, from the paper (light or dark), not the app theme. */
@Immutable
internal data class MdColors(
    val paper: Color,
    val ink: Color,
    val sub: Color,
    val accent: Color,
    val codeBg: Color,
    val border: Color,
    val headerBg: Color,
    val stripe: Color,
    val markBg: Color
) {
    companion object {
        fun of(dark: Boolean, ink: Color, sub: Color, accent: Color) = if (dark) MdColors(
            paper = Color(0xFF111114), ink = ink, sub = sub, accent = accent,
            codeBg = Color(0xFF1E1F24), border = Color(0xFF34363D), headerBg = Color(0xFF1B1C21),
            stripe = Color(0xFF16171B), markBg = Color(0x66B08800)
        ) else MdColors(
            paper = Color(0xFFFFFFFF), ink = ink, sub = sub, accent = accent,
            codeBg = Color(0xFFF2F3F6), border = Color(0xFFD9DCE2), headerBg = Color(0xFFF3F4F7),
            stripe = Color(0xFFF9FAFB), markBg = Color(0xFFFFF1A8)
        )
    }
}

private val BodySize = 16.sp
private val BodyLine = 25.sp
private val Mono = FontFamily.Monospace

/** One top-level block. Nested blocks (in quotes and lists) go through the same function. */
@Composable
internal fun MdBlockView(block: MdBlock, c: MdColors, imageLabel: String, onLink: (String) -> Unit, depth: Int = 0) {
    when (block) {
        is MdBlock.Heading -> {
            val size = when (block.level) { 1 -> 26.sp; 2 -> 22.sp; 3 -> 19.sp; 4 -> 17.sp; 5 -> 15.sp; else -> 14.sp }
            Column(Modifier.fillMaxWidth().padding(top = if (block.level <= 2) 10.dp else 4.dp)) {
                BasicText(
                    annotated(block.text, c, imageLabel, onLink),
                    style = TextStyle(
                        if (block.level == 6) c.sub else c.ink, size,
                        fontWeight = if (block.level <= 3) FontWeight.Bold else FontWeight.SemiBold,
                        lineHeight = size * 1.25f
                    )
                )
                if (block.level <= 2) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(c.border))
            }
        }
        is MdBlock.Paragraph -> Paragraph(block.text, c, imageLabel, onLink)
        is MdBlock.Code -> CodeBox(block.code, block.language, c)
        is MdBlock.FrontMatter -> CodeBox(block.raw, "yaml", c, muted = true)
        is MdBlock.Html -> CodeBox(block.raw, "html", c, muted = true)
        is MdBlock.Rule -> Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(2.dp).background(c.border))
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.border))
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val quoted = c.copy(ink = c.sub)
                block.blocks.forEach { MdBlockView(it, quoted, imageLabel, onLink, depth) }
            }
        }
        is MdBlock.ListBlock -> ListView(block, c, imageLabel, onLink, depth)
        is MdBlock.Table -> TableView(block, c, imageLabel, onLink)
    }
}

@Composable
private fun Paragraph(text: MdText, c: MdColors, imageLabel: String, onLink: (String) -> Unit) {
    BasicText(annotated(text, c, imageLabel, onLink), style = TextStyle(c.ink, BodySize, lineHeight = BodyLine))
}

@Composable
private fun CodeBox(code: String, language: String?, c: MdColors, muted: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.codeBg).padding(vertical = 10.dp)
    ) {
        if (!language.isNullOrBlank()) BasicText(
            language, Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp),
            style = TextStyle(c.sub, 11.sp, fontWeight = FontWeight.SemiBold)
        )
        // Code keeps its lines: it scrolls sideways inside its box, the page never does.
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            BasicText(code, style = TextStyle(if (muted) c.sub else c.ink, 13.5.sp, fontFamily = Mono, lineHeight = 20.sp), softWrap = false)
        }
    }
}

@Composable
private fun ListView(list: MdBlock.ListBlock, c: MdColors, imageLabel: String, onLink: (String) -> Unit, depth: Int) {
    val markerWidth = if (list.ordered) (14 + 9 * (list.start + list.items.size - 1).toString().length).dp else 22.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (list.loose) 10.dp else 4.dp)) {
        list.items.forEachIndexed { i, item ->
            Row {
                Box(Modifier.width(markerWidth).height(BodyLineDp), contentAlignment = Alignment.CenterStart) {
                    when {
                        item.checked != null -> Icon(
                            if (item.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                            Modifier.size(19.dp), if (item.checked) c.accent else c.sub
                        )
                        list.ordered -> BasicText("${list.start + i}.", style = TextStyle(c.sub, BodySize, fontWeight = FontWeight.Medium))
                        else -> BasicText(BULLETS[depth % BULLETS.size], style = TextStyle(c.sub, BodySize, fontWeight = FontWeight.Bold))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (list.loose) 10.dp else 4.dp)) {
                    item.blocks.forEach { MdBlockView(it, c, imageLabel, onLink, if (it is MdBlock.ListBlock) depth + 1 else depth) }
                }
            }
        }
    }
}

private val BodyLineDp = 25.dp
private val BULLETS = listOf("•", "◦", "▪")

// ── tables ─────────────────────────────────────────────────────────────────────────────────────

/**
 * A table keeps its columns: when it is wider than the page it scrolls sideways inside its own
 * rounded box (edge shading says there is more), and the page itself never scrolls sideways.
 * Columns size to their content between [MinCol] and [MaxCol], long cells wrap; a table that
 * fits is stretched to the page width.
 */
@Composable
private fun TableView(t: MdBlock.Table, c: MdColors, imageLabel: String, onLink: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.border, shape)) {
        val viewport = constraints.maxWidth
        val scroll = rememberScrollState()
        Box(Modifier.horizontalScroll(scroll)) {
            TableGrid(t, c, imageLabel, onLink, viewport)
        }
        Box(Modifier.matchParentSize()) {
            EdgeShade(scroll, c, start = true, Modifier.align(Alignment.CenterStart))
            EdgeShade(scroll, c, start = false, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun EdgeShade(scroll: ScrollState, c: MdColors, start: Boolean, modifier: Modifier) {
    val show = if (start) scroll.canScrollBackward else scroll.canScrollForward
    if (!show) return
    val shade = Color.Black.copy(alpha = if (c.paper.red < 0.5f) 0.45f else 0.10f)
    Box(
        modifier.width(14.dp).fillMaxHeight().background(
            Brush.horizontalGradient(if (start) listOf(shade, Color.Transparent) else listOf(Color.Transparent, shade))
        )
    )
}

private val MinCol = 56.dp
private val MaxCol = 280.dp
private val CellPadH = 12.dp
private val CellPadV = 8.dp

@Composable
private fun TableGrid(t: MdBlock.Table, c: MdColors, imageLabel: String, onLink: (String) -> Unit, viewport: Int) {
    val cols = t.align.size
    // Where the lines go, written by the measure pass and read by the draw pass right after it.
    val grid = remember { TableLines() }
    Layout(
        content = {
            (listOf(t.header) + t.rows).forEachIndexed { r, row ->
                row.forEachIndexed { i, cell ->
                    val align = when (t.align[i]) { MdAlign.Start -> TextAlign.Start; MdAlign.Center -> TextAlign.Center; MdAlign.End -> TextAlign.End }
                    BasicText(
                        annotated(cell, c, imageLabel, onLink),
                        Modifier.padding(horizontal = CellPadH, vertical = CellPadV),
                        style = TextStyle(
                            c.ink, 14.sp, lineHeight = 20.sp, textAlign = align,
                            fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                }
            }
        },
        modifier = Modifier.drawBehind {
            val ys = grid.rowY
            val xs = grid.colX
            if (ys.size < 2 || xs.size < 2) return@drawBehind
            val w = xs.last().toFloat()
            drawRect(c.headerBg, Offset.Zero, Size(w, ys[1].toFloat()))
            for (r in 2 until ys.size - 1 step 2) drawRect(c.stripe, Offset(0f, ys[r].toFloat()), Size(w, (ys[r + 1] - ys[r]).toFloat()))
            val line = 1.dp.toPx()
            for (r in 1 until ys.size - 1) drawRect(c.border, Offset(0f, ys[r] - line / 2), Size(w, line))
            for (x in 1 until xs.size - 1) drawRect(c.border, Offset(xs[x] - line / 2, 0f), Size(line, ys.last().toFloat()))
        }
    ) { measurables, _ ->
        val minCol = MinCol.roundToPx()
        val maxCol = MaxCol.roundToPx()
        val widths = IntArray(cols) { minCol }
        measurables.forEachIndexed { k, m ->
            val col = k % cols
            widths[col] = maxOf(widths[col], m.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(maxCol))
        }
        val natural = widths.sum()
        if (natural < viewport && natural > 0) {
            // Stretch to the page, each column in proportion, the last one taking the rounding.
            var used = 0
            for (i in 0 until cols) {
                widths[i] = if (i == cols - 1) viewport - used else (widths[i].toLong() * viewport / natural).toInt()
                used += widths[i]
            }
        }
        val placeables = measurables.mapIndexed { k, m -> m.measure(Constraints.fixedWidth(widths[k % cols])) }
        val rowCount = placeables.size / cols
        val rowY = IntArray(rowCount + 1)
        for (r in 0 until rowCount) {
            var h = 0
            for (i in 0 until cols) h = maxOf(h, placeables[r * cols + i].height)
            rowY[r + 1] = rowY[r] + h
        }
        val colX = IntArray(cols + 1).also { for (i in 0 until cols) it[i + 1] = it[i] + widths[i] }
        grid.rowY = rowY
        grid.colX = colX
        layout(colX.last(), rowY.last()) {
            placeables.forEachIndexed { k, p: Placeable -> p.place(colX[k % cols], rowY[k / cols]) }
        }
    }
}

private class TableLines {
    var rowY = IntArray(0)
    var colX = IntArray(0)
}

// ── inline text ────────────────────────────────────────────────────────────────────────────────

/** [MdText] as an `AnnotatedString`; links are clickable and report their target to [onLink]. */
internal fun annotated(t: MdText, c: MdColors, imageLabel: String, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    val opens = t.spans.sortedWith(compareBy<MdSpan>({ it.start }, { -it.end }))
    val stack = ArrayDeque<MdSpan>()
    var next = 0
    fun closeUpTo(pos: Int) {
        while (stack.isNotEmpty() && stack.last().end <= pos) { stack.removeLast(); pop() }
    }
    var i = 0
    while (i <= t.text.length) {
        closeUpTo(i)
        var skipTo = -1
        while (next < opens.size && opens[next].start == i) {
            val s = opens[next++]
            val style = s.style
            if (style is MdStyle.Image) {
                // Offline viewer: a picture is shown as its label, never fetched.
                val alt = t.text.substring(s.start, s.end).ifBlank { imageLabel }
                pushStyle(SpanStyle(color = c.sub, fontStyle = FontStyle.Italic))
                append("🖼 ")
                append(alt)
                pop()
                skipTo = maxOf(skipTo, s.end)
                continue
            }
            when (style) {
                is MdStyle.Link -> pushLink(
                    LinkAnnotation.Clickable(
                        style.url,
                        TextLinkStyles(SpanStyle(color = c.accent, textDecoration = TextDecoration.Underline))
                    ) { onLink(style.url) }
                )
                else -> pushStyle(spanStyle(style, c))
            }
            stack.addLast(s)
        }
        if (skipTo > i) { i = skipTo; continue }
        if (i < t.text.length) append(t.text[i])
        i++
    }
    closeUpTo(Int.MAX_VALUE)
}

private fun spanStyle(s: MdStyle, c: MdColors): SpanStyle = when (s) {
    MdStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    MdStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    MdStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    MdStyle.Code -> SpanStyle(fontFamily = Mono, fontSize = 0.88.em, background = c.codeBg)
    MdStyle.Mark -> SpanStyle(background = c.markBg)
    is MdStyle.Link, is MdStyle.Image -> SpanStyle()
}
