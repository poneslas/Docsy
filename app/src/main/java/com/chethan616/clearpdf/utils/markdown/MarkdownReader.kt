package com.chethan616.clearpdf.utils.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes as E
import org.intellij.markdown.MarkdownTokenTypes as T
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.impl.ListCompositeNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes as GE
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes as GT
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

/**
 * Reads Markdown (GitHub flavour: tables, task lists, strikethrough, autolinks, alerts) into an
 * [MdDocument], using JetBrains' parser for the syntax and only turning its tree into our blocks.
 *
 * Raw HTML is never interpreted: an HTML block shows as its source, inline tags are dropped
 * (`<br>` becomes a line break), so a file can't smuggle markup into the print page either.
 */
object MarkdownReader {

    fun read(source: String): MdDocument {
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val (frontMatter, body) = splitFrontMatter(normalized)
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(body)
        val builder = Builder(body, LinkMap.buildLinkMap(root, body))
        val blocks = builder.blocks(root)
        return MdDocument(if (frontMatter != null) listOf(MdBlock.FrontMatter(frontMatter)) + blocks else blocks)
    }

    /** `---` on the first line, closed by `---` or `...`: YAML front matter (Jekyll, Hugo, Obsidian). */
    internal fun splitFrontMatter(text: String): Pair<String?, String> {
        if (!text.startsWith("---\n")) return null to text
        val lines = text.split('\n')
        for (i in 1 until lines.size) {
            val l = lines[i].trimEnd()
            if (l == "---" || l == "...") {
                if (i == 1) return null to text // "---\n---": two rules, not front matter
                val fm = lines.subList(1, i).joinToString("\n")
                return fm to lines.drop(i + 1).joinToString("\n")
            }
        }
        return null to text
    }

    /** GitHub's heading slug: lower case, punctuation dropped, spaces to '-'. Letters of any script stay. */
    internal fun slug(text: String): String = buildString {
        for (c in text.trim().lowercase()) when {
            c.isLetterOrDigit() || c == '-' || c == '_' -> append(c)
            c == ' ' -> append('-')
        }
    }

    /** Text with escapes and entities resolved, as plain characters (not HTML). */
    internal fun plain(s: CharSequence): String =
        EntityConverter.replaceEntities(s, processEntities = true, processEscapes = true).unescapeHtml()

    private fun String.unescapeHtml(): String =
        replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private class Builder(val src: String, val links: LinkMap) {
        private val usedAnchors = HashMap<String, Int>()

        fun blocks(node: ASTNode): List<MdBlock> = node.children.flatMap { block(it) }

        private fun block(n: ASTNode): List<MdBlock> = when (n.type) {
            E.ATX_1, E.ATX_2, E.ATX_3, E.ATX_4, E.ATX_5, E.ATX_6 ->
                heading(n.type.name.last().digitToInt(), n.children.firstOrNull { it.type == T.ATX_CONTENT })
            E.SETEXT_1 -> heading(1, n.children.firstOrNull { it.type == T.SETEXT_CONTENT })
            E.SETEXT_2 -> heading(2, n.children.firstOrNull { it.type == T.SETEXT_CONTENT })
            E.PARAGRAPH -> listOf(MdBlock.Paragraph(inline(n.children))).filterNot { (it.text.isBlank) }
            E.CODE_FENCE -> listOf(codeFence(n))
            E.CODE_BLOCK -> listOf(MdBlock.Code(null, codeBlock(n)))
            E.BLOCK_QUOTE, GE.ALERT -> listOf(MdBlock.Quote(blocks(n)))
            GT.ALERT_TITLE -> listOf(MdBlock.Paragraph(alertTitle(n)))
            E.ORDERED_LIST, E.UNORDERED_LIST -> listOf(list(n))
            GE.TABLE -> listOf(table(n))
            T.HORIZONTAL_RULE -> listOf(MdBlock.Rule)
            E.HTML_BLOCK -> listOf(MdBlock.Html(n.getTextInNode(src).toString().trimEnd()))
            GE.BLOCK_MATH -> listOf(MdBlock.Code("math", mathText(n)))
            E.LINK_DEFINITION -> emptyList()
            else -> if (n is LeafASTNode) emptyList() else blocks(n)
        }

        private fun heading(level: Int, content: ASTNode?): List<MdBlock> {
            val text = content?.let { inline(it.children) } ?: MdText("")
            val base = slug(text.text).ifEmpty { "section" }
            val seen = usedAnchors[base]
            usedAnchors[base] = (seen ?: 0) + 1
            return listOf(MdBlock.Heading(level, text, if (seen == null) base else "$base-$seen"))
        }

        private fun alertTitle(n: ASTNode): MdText {
            val kind = n.getTextInNode(src).toString().trim().removePrefix("[!").removeSuffix("]")
            val title = kind.lowercase().replaceFirstChar { it.uppercase() }
            return MdText(title, listOf(MdSpan(0, title.length, MdStyle.Bold)))
        }

        // ── code ──

        private fun codeFence(n: ASTNode): MdBlock.Code {
            val indent = n.getTextInNode(src).takeWhile { it == ' ' }.length
            var children = n.children
            if (children.lastOrNull()?.type == T.CODE_FENCE_END) children = children.dropLast(1)
            var language: String? = null
            var inBody = false
            val sb = StringBuilder()
            for (c in children) {
                if (!inBody) {
                    if (c.type == T.FENCE_LANG) language = plain(c.getTextInNode(src)).trim().substringBefore(' ').ifBlank { null }
                    if (c.type == T.EOL) inBody = true
                } else if (c.type == T.CODE_FENCE_CONTENT || c.type == T.EOL) {
                    sb.append(trimIndent(c.getTextInNode(src).toString(), if (c.type == T.EOL) 0 else indent))
                }
            }
            return MdBlock.Code(language, sb.toString().trimEnd('\n'))
        }

        private fun codeBlock(n: ASTNode): String = buildString {
            for (c in n.children) when (c.type) {
                T.CODE_LINE -> append(trimIndent(c.getTextInNode(src).toString(), 4))
                T.EOL -> append('\n')
            }
        }.trimEnd('\n')

        private fun trimIndent(s: String, indent: Int): String {
            var i = 0
            while (i < indent && i < s.length && s[i] == ' ') i++
            return s.substring(i)
        }

        private fun mathText(n: ASTNode): String =
            n.children.drop(1).dropLast(1).joinToString("") { it.getTextInNode(src) }.trim().trim('`').trim()

        // ── lists and tables ──

        private fun list(n: ASTNode): MdBlock.ListBlock {
            val ordered = n.type == E.ORDERED_LIST
            val items = n.children.filter { it.type == E.LIST_ITEM }
            val start = if (!ordered) 1 else items.firstOrNull()
                ?.children?.firstOrNull { it.type == T.LIST_NUMBER }
                ?.getTextInNode(src)?.toString()?.trim()?.dropLast(1)?.toIntOrNull() ?: 1
            return MdBlock.ListBlock(
                ordered = ordered,
                start = start,
                loose = (n as? ListCompositeNode)?.loose == true,
                items = items.map { item ->
                    val box = item.children.firstOrNull { it.type == GT.CHECK_BOX }?.getTextInNode(src)
                    ListItem(
                        checked = box?.let { it.length > 1 && it[1] != ' ' },
                        blocks = item.children.filterNot { it is LeafASTNode }.flatMap { block(it) }
                    )
                }
            )
        }

        private fun table(n: ASTNode): MdBlock.Table {
            val align = n.children.firstOrNull { it.type == GT.TABLE_SEPARATOR }?.let { sep ->
                val cells = sep.getTextInNode(src).split('|')
                cells.filterIndexed { i, c -> c.isNotBlank() || i in 1 until cells.lastIndex }.map {
                    val t = it.trim()
                    when {
                        t.startsWith(':') && t.endsWith(':') -> MdAlign.Center
                        t.endsWith(':') -> MdAlign.End
                        else -> MdAlign.Start
                    }
                }
            }.orEmpty()
            fun cells(row: ASTNode) = row.children.filter { it.type == GT.CELL }.map { inline(it.children) }
            val header = n.children.firstOrNull { it.type == GE.HEADER }?.let { cells(it) }.orEmpty()
            val cols = maxOf(header.size, align.size, 1)
            // A short row is padded and a long one cut, so every row has the header's columns.
            fun <V> List<V>.fit(pad: V) = take(cols) + List((cols - size).coerceAtLeast(0)) { pad }
            return MdBlock.Table(
                header = header.fit(MdText("")),
                align = align.fit(MdAlign.Start),
                rows = n.children.filter { it.type == GE.ROW }.map { cells(it).fit(MdText("")) }
            )
        }

        // ── inline ──

        fun inline(nodes: List<ASTNode>): MdText {
            val out = InlineOut()
            nodes.forEach { inlineNode(it, out) }
            return out.build()
        }

        private fun inlineNode(n: ASTNode, out: InlineOut) {
            when (n.type) {
                E.EMPH -> out.styled(MdStyle.Italic) { n.children.drop(1).dropLast(1).forEach { inlineNode(it, out) } }
                E.STRONG -> out.styled(MdStyle.Bold) { n.children.drop(2).dropLast(2).forEach { inlineNode(it, out) } }
                GE.STRIKETHROUGH -> out.styled(MdStyle.Strike) { trimDelimiters(n.children, GT.TILDE).forEach { inlineNode(it, out) } }
                GE.HIGHLIGHT -> out.styled(MdStyle.Mark) { trimDelimiters(n.children, GT.EQUALS).forEach { inlineNode(it, out) } }
                E.CODE_SPAN -> {
                    val code = n.children.drop(1).dropLast(1).joinToString("") { it.getTextInNode(src) }
                        .replace('\n', ' ').let { if (it.length > 2 && it.startsWith(' ') && it.endsWith(' ')) it.substring(1, it.length - 1) else it }
                    out.styled(MdStyle.Code) { out.literal(code) }
                }
                GE.INLINE_MATH -> out.styled(MdStyle.Code) { out.literal(mathText(n)) }
                E.INLINE_LINK -> {
                    val label = n.children.firstOrNull { it.type == E.LINK_TEXT }
                    val dest = n.children.firstOrNull { it.type == E.LINK_DESTINATION }
                        ?.let { plain(LinkMap.normalizeDestination(it.getTextInNode(src), true)) }.orEmpty()
                    if (label == null) n.children.forEach { inlineNode(it, out) }
                    else out.styled(MdStyle.Link(dest)) { label.children.drop(1).dropLast(1).forEach { inlineNode(it, out) } }
                }
                E.FULL_REFERENCE_LINK, E.SHORT_REFERENCE_LINK -> {
                    val labelNode = n.children.firstOrNull { it.type == E.LINK_LABEL }
                    val info = labelNode?.let { links.getLinkInfo(it.getTextInNode(src)) }
                    val textNode = n.children.firstOrNull { it.type == E.LINK_TEXT } ?: labelNode
                    if (info == null || textNode == null) out.text(n.getTextInNode(src))
                    else out.styled(MdStyle.Link(plain(info.destination))) { textNode.children.drop(1).dropLast(1).forEach { inlineNode(it, out) } }
                }
                E.IMAGE -> {
                    val link = n.children.firstOrNull { it.type == E.INLINE_LINK || it.type == E.FULL_REFERENCE_LINK || it.type == E.SHORT_REFERENCE_LINK }
                    val url = when (link?.type) {
                        E.INLINE_LINK -> link.children.firstOrNull { it.type == E.LINK_DESTINATION }
                            ?.let { plain(LinkMap.normalizeDestination(it.getTextInNode(src), true)) }
                        null -> null
                        else -> link.children.firstOrNull { it.type == E.LINK_LABEL }
                            ?.let { links.getLinkInfo(it.getTextInNode(src)) }?.let { plain(it.destination) }
                    }.orEmpty()
                    val labelNode = link?.children?.firstOrNull { it.type == E.LINK_TEXT || it.type == E.LINK_LABEL }
                    val alt = labelNode?.let { plainOf(it) }?.trim().orEmpty()
                    out.styled(MdStyle.Image(url)) { out.literal(alt) }
                }
                E.AUTOLINK -> {
                    val raw = n.getTextInNode(src).toString().removePrefix("<").removeSuffix(">")
                    val url = if (!raw.contains(':') && raw.contains('@')) "mailto:$raw" else raw
                    out.styled(MdStyle.Link(url)) { out.literal(raw) }
                }
                GT.GFM_AUTOLINK -> {
                    val raw = n.getTextInNode(src).toString()
                    if (n.parentOfType(E.LINK_TEXT, E.LINK_LABEL)) out.literal(raw)
                    else out.styled(MdStyle.Link(if (raw.contains("://") || raw.startsWith("mailto:")) raw else "https://$raw")) { out.literal(raw) }
                }
                T.HTML_TAG -> if (BR.matches(n.getTextInNode(src))) out.lineBreak()
                T.HARD_LINE_BREAK -> out.lineBreak()
                T.EOL, T.WHITE_SPACE -> out.space()
                T.BLOCK_QUOTE -> Unit // a lazy-continuation '>' inside a paragraph
                else -> if (n is LeafASTNode) out.text(n.getTextInNode(src)) else n.children.forEach { inlineNode(it, out) }
            }
        }

        private fun trimDelimiters(children: List<ASTNode>, delimiter: IElementType): List<ASTNode> {
            var l = 0
            var r = children.size - 1
            while (l < r && children[l].type == delimiter && children[r].type == delimiter) { l++; r-- }
            return if (l > r) emptyList() else children.subList(l, r + 1)
        }

        private fun plainOf(n: ASTNode): String = when {
            n is LeafASTNode -> if (n.type in MARKUP) "" else plain(n.getTextInNode(src))
            else -> n.children.joinToString("") { plainOf(it) }
        }

        private fun ASTNode.parentOfType(vararg types: IElementType): Boolean {
            var p = parent
            while (p != null) { if (p.type in types) return true; p = p.parent }
            return false
        }
    }

    private val BR = Regex("(?i)<br\\s*/?>")
    private val MARKUP = setOf(T.EMPH, T.BACKTICK, T.ESCAPED_BACKTICKS, T.EXCLAMATION_MARK, T.LBRACKET, T.RBRACKET, T.LPAREN, T.RPAREN)

    /** Collects inline text, collapsing soft whitespace the way a browser would. */
    private class InlineOut {
        private val sb = StringBuilder()
        private val spans = ArrayList<MdSpan>()
        private var pendingSpace = false

        fun text(raw: CharSequence) = literal(plain(raw))

        fun literal(s: String) {
            if (s.isEmpty()) return
            if (pendingSpace && sb.isNotEmpty() && sb.last() != '\n') sb.append(' ')
            pendingSpace = false
            sb.append(s)
        }

        fun space() { pendingSpace = true }

        fun lineBreak() {
            pendingSpace = false
            while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
            sb.append('\n')
        }

        fun styled(style: MdStyle, body: () -> Unit) {
            // A space owed before the styled run belongs outside it.
            if (pendingSpace && sb.isNotEmpty() && sb.last() != '\n') { sb.append(' '); pendingSpace = false }
            val start = sb.length
            body()
            if (sb.length > start || style is MdStyle.Image) spans.add(MdSpan(start, sb.length, style))
        }

        fun build(): MdText {
            // Trailing whitespace is never owed; a trailing hard break is dropped.
            var end = sb.length
            while (end > 0 && sb[end - 1] == '\n') end--
            return MdText(sb.substring(0, end), spans.map { MdSpan(it.start.coerceAtMost(end), it.end.coerceAtMost(end), it.style) })
        }
    }
}
