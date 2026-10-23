package com.chethan616.clearpdf.utils.markdown

/**
 * A Markdown file as the viewer reads it: a flat list of blocks, each holding text with style
 * ranges. It is deliberately UI-free, so the on-screen renderer ([MarkdownBlocks]) and the print
 * renderer ([MarkdownHtml]) draw the same document and can't drift apart, and the parser is
 * testable on the JVM.
 */
data class MdDocument(val blocks: List<MdBlock>)

/** Text with non-overlapping-or-nested style ranges, the shape an `AnnotatedString` wants. */
data class MdText(val text: String, val spans: List<MdSpan> = emptyList()) {
    val isBlank: Boolean get() = text.isBlank()
}

data class MdSpan(val start: Int, val end: Int, val style: MdStyle)

sealed interface MdStyle {
    data object Bold : MdStyle
    data object Italic : MdStyle
    data object Strike : MdStyle
    data object Code : MdStyle
    data object Mark : MdStyle
    data class Link(val url: String) : MdStyle
    /** An image: the covered text is its alt text. The viewer works offline, so it shows a label. */
    data class Image(val url: String) : MdStyle
}

enum class MdAlign { Start, Center, End }

sealed interface MdBlock {
    /** [anchor] is the GitHub-style slug, so `[see](#install)` links can scroll to it. */
    data class Heading(val level: Int, val text: MdText, val anchor: String) : MdBlock
    data class Paragraph(val text: MdText) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val loose: Boolean, val items: List<ListItem>) : MdBlock
    data class Table(val header: List<MdText>, val align: List<MdAlign>, val rows: List<List<MdText>>) : MdBlock
    data object Rule : MdBlock
    /** Raw HTML block. Never interpreted: the viewer shows its source. */
    data class Html(val raw: String) : MdBlock
    /** YAML front matter (`---` … `---` at the very top), shown as a small code box. */
    data class FrontMatter(val raw: String) : MdBlock
}

/** [checked] is null for a plain item, true/false for a GFM task-list item. */
data class ListItem(val checked: Boolean?, val blocks: List<MdBlock>)
