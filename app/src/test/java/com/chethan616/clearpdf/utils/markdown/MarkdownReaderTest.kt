package com.chethan616.clearpdf.utils.markdown

import com.chethan616.clearpdf.utils.TextDecoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownReaderTest {

    private fun read(md: String) = MarkdownReader.read(md).blocks

    @Test
    fun headingsGetGithubAnchorsAndDuplicatesAreNumbered() {
        val b = read("# Установка и запуск\n\n## Setup\n\n## Setup\n")
        assertEquals(MdBlock.Heading(1, MdText("Установка и запуск"), "установка-и-запуск"), b[0])
        assertEquals("setup", (b[1] as MdBlock.Heading).anchor)
        assertEquals("setup-1", (b[2] as MdBlock.Heading).anchor)
    }

    @Test
    fun inlineStylesBecomeRangesOverPlainText() {
        val p = read("Some **bold** and *it* with `code` and ~~gone~~.")[0] as MdBlock.Paragraph
        assertEquals("Some bold and it with code and gone.", p.text.text)
        fun range(style: MdStyle) = p.text.spans.single { it.style == style }.let { p.text.text.substring(it.start, it.end) }
        assertEquals("bold", range(MdStyle.Bold))
        assertEquals("it", range(MdStyle.Italic))
        assertEquals("code", range(MdStyle.Code))
        assertEquals("gone", range(MdStyle.Strike))
    }

    @Test
    fun softBreaksCollapseAndEscapesResolve() {
        val p = read("one\ntwo   \\*three\\* &amp; four")[0] as MdBlock.Paragraph
        assertEquals("one two *three* & four", p.text.text)
    }

    @Test
    fun linksKeepTheirTargets() {
        val p = read("See [docs](https://example.com/a?b=1&c=2), <https://x.org> and [ref][r].\n\n[r]: #setup\n")[0] as MdBlock.Paragraph
        val links = p.text.spans.mapNotNull { (it.style as? MdStyle.Link)?.url }
        assertEquals(listOf("https://example.com/a?b=1&c=2", "https://x.org", "#setup"), links)
        assertEquals("See docs, https://x.org and ref.", p.text.text)
    }

    @Test
    fun tableKeepsColumnsAlignmentAndPadsShortRows() {
        val t = read("| A | B | C |\n|:--|:-:|--:|\n| 1 | **2** | 3 |\n| only |\n")[0] as MdBlock.Table
        assertEquals(listOf("A", "B", "C"), t.header.map { it.text })
        assertEquals(listOf(MdAlign.Start, MdAlign.Center, MdAlign.End), t.align)
        assertEquals(listOf("1", "2", "3"), t.rows[0].map { it.text })
        assertEquals(listOf("only", "", ""), t.rows[1].map { it.text })
    }

    @Test
    fun taskListsNestedListsAndStartNumbers() {
        val b = read("- [x] done\n- [ ] todo\n  - child\n\n3. three\n4. four\n")
        val tasks = b[0] as MdBlock.ListBlock
        assertEquals(listOf(true, false), tasks.items.map { it.checked })
        assertTrue(tasks.items[1].blocks.any { it is MdBlock.ListBlock })
        val ordered = b[1] as MdBlock.ListBlock
        assertTrue(ordered.ordered)
        assertEquals(3, ordered.start)
    }

    @Test
    fun fencedCodeKeepsLanguageAndIndentation() {
        val c = read("```kotlin\nfun a() {\n    b()\n}\n```\n")[0] as MdBlock.Code
        assertEquals("kotlin", c.language)
        assertEquals("fun a() {\n    b()\n}", c.code)
    }

    @Test
    fun frontMatterIsSplitOffTheTop() {
        val b = read("---\ntitle: Hi\ntags: [a]\n---\n# Body\n")
        assertEquals(MdBlock.FrontMatter("title: Hi\ntags: [a]"), b[0])
        assertEquals("Body", (b[1] as MdBlock.Heading).text.text)
    }

    @Test
    fun rawHtmlIsNeverInterpreted() {
        val b = read("<div onclick=\"x()\">hi</div>\n\nText <b>bold</b><br>next\n")
        assertTrue(b[0] is MdBlock.Html)
        val p = b[1] as MdBlock.Paragraph
        assertEquals("Text bold\nnext", p.text.text)
        val html = MarkdownHtml.render(MdDocument(b), "<t>")
        assertFalse(html.contains("<div onclick"))
        // Shown as highlighted source: escaped, split into coloured spans.
        assertTrue(html.contains("onclick") && html.contains("&lt;"))
        assertTrue(html.contains("<title>&lt;t&gt;</title>"))
    }

    @Test
    fun htmlNestsInlineTagsAndWrapsTables() {
        val b = read("**bold *both* x** [l](https://e.com)\n\n| a |\n|---|\n| b |\n")
        val html = MarkdownHtml.render(MdDocument(b), "t")
        assertTrue(html.contains("<strong>bold <em>both</em> x</strong> <a href=\"https://e.com\">l</a>"))
        assertTrue(html.contains("<div class=\"table\"><table><thead><tr><th>a</th></tr></thead><tbody><tr><td>b</td></tr></tbody></table></div>"))
    }

    @Test
    fun imagesAreLabelsOffline() {
        val p = read("![Схема](pic.png)")[0] as MdBlock.Paragraph
        val img = p.text.spans.single().style as MdStyle.Image
        assertEquals("pic.png", img.url)
        assertEquals("Схема", p.text.text)
        assertTrue(MarkdownHtml.render(MdDocument(listOf(p)), "t").contains("<span class=\"img\">Схема</span>"))
    }

    @Test
    fun decodingHandlesBomUtf16AndCp1251() {
        val ru = "Привет, мир"
        assertEquals(ru, TextDecoding.decode(ru.toByteArray(Charsets.UTF_8)))
        assertEquals(ru, TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ru.toByteArray(Charsets.UTF_8)))
        assertEquals(ru, TextDecoding.decode(ru.toByteArray(charset("windows-1251"))))
        assertEquals("# Title text", TextDecoding.decode("# Title text".toByteArray(Charsets.UTF_16LE)))
        assertEquals(ru, TextDecoding.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + ru.toByteArray(Charsets.UTF_16LE)))
    }
}
