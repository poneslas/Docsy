package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.chethan616.clearpdf.utils.text.Grammar
import com.chethan616.clearpdf.utils.text.Highlighter
import com.chethan616.clearpdf.utils.text.LineState
import com.chethan616.clearpdf.utils.text.SyntaxPalette

/** One line, highlighted with [grammar] starting from [state], in the page's palette. */
internal fun highlightLine(line: String, grammar: Grammar?, state: LineState, dark: Boolean): AnnotatedString {
    if (grammar == null || line.isEmpty()) return AnnotatedString(line)
    val tokens = Highlighter.tokenize(grammar, line, state).tokens
    return buildAnnotatedString {
        append(line)
        for (t in tokens) SyntaxPalette.styleFor(t.scope, dark)?.let { addStyle(it.toSpan(), t.start, t.end) }
    }
}

/** A whole block (a Markdown code fence), line by line so multi-line comments and tags carry over. */
internal fun highlightBlock(text: String, grammar: Grammar?, dark: Boolean): AnnotatedString {
    if (grammar == null) return AnnotatedString(text)
    return buildAnnotatedString {
        var state = LineState.Initial
        var offset = 0
        text.split('\n').forEachIndexed { i, line ->
            if (i > 0) { append('\n'); offset++ }
            val lt = Highlighter.tokenize(grammar, line, state)
            state = lt.endState
            append(line)
            for (t in lt.tokens) SyntaxPalette.styleFor(t.scope, dark)?.let { addStyle(it.toSpan(), offset + t.start, offset + t.end) }
            offset += line.length
        }
    }
}

private fun SyntaxPalette.Style.toSpan() = SpanStyle(
    color = Color(argb.toInt()),
    fontWeight = if (bold) FontWeight.SemiBold else null,
    fontStyle = if (italic) FontStyle.Italic else null
)
