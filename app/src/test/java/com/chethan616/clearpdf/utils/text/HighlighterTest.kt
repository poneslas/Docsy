package com.chethan616.clearpdf.utils.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlighterTest {

    private fun scopes(g: Grammar, line: String, state: LineState = LineState.Initial): Map<String, String> =
        Highlighter.tokenize(g, line, state).tokens.associate { line.substring(it.start, it.end) to it.scope }

    @Test
    fun logcatLineGetsDateLevelAndNumbers() {
        val s = scopes(Grammars.Log, "10-23 10:11:04.245  4109  4109 E chromium: [ERROR:aw_component.cc(285)] No directory")
        assertEquals("comment log.date", s["10-23"])
        assertEquals("comment log.date", s["10:11:04.245"])
        assertEquals("constant.language log.constant", s["4109"])
        assertEquals("strong log.error", s["E"])
    }

    @Test
    fun logLevelsWordsAndBrackets() {
        assertEquals("markup.deleted log.warning", scopes(Grammars.Log, "2026-10-23 WARN cache is full")["WARN"])
        assertEquals("markup.inserted log.info", scopes(Grammars.Log, "[info] started")["[info]"])
        assertEquals("comment log.date", scopes(Grammars.Log, "2026-10-23 WARN cache is full")["2026-10-23"])
        assertEquals("strong log.error", scopes(Grammars.Log, "E/AndroidRuntime: FATAL")["E"])
    }

    @Test
    fun stackTraceAndExceptions() {
        assertEquals("log.exception", scopes(Grammars.Log, "    at com.foo.Bar.baz(Bar.kt:12)").values.single())
        assertEquals("log.exceptiontype", scopes(Grammars.Log, "Caused by: java.lang.IllegalStateException: nope")["java.lang.IllegalStateException"])
    }

    @Test
    fun jsonKeysStringsNumbers() {
        val s = scopes(Grammars.Json, """{"name": "Доки", "n": -1.5e3, "ok": true, "x": null}""")
        assertEquals("support.type.property-name.json", s["\"name\""])
        assertEquals("string.quoted.double.json", s["\"Доки\""])
        assertEquals("constant.numeric.json", s["-1.5e3"])
        assertEquals("constant.language.json", s["true"])
    }

    @Test
    fun xmlTagsAttributesAndCommentsAcrossLines() {
        val first = Highlighter.tokenize(Grammars.Xml, """<item id="7" android:name='x'> text <!-- note""")
        val s = first.tokens.associate { """<item id="7" android:name='x'> text <!-- note""".substring(it.start, it.end) to it.scope }
        assertEquals("entity.name.tag.xml", s["item"])
        assertEquals("entity.other.attribute-name.xml", s["id"])
        assertEquals("string.quoted.double.xml", s["\"7\""])
        assertEquals("entity.other.attribute-name.namespace.xml", s["android"])
        // The comment is still open: the next line starts inside it and leaves it at "-->".
        val second = Highlighter.tokenize(Grammars.Xml, "still comment --> <b/>", first.endState)
        assertEquals("comment.block.xml", second.tokens.first().scope)
        assertEquals(0, second.tokens.first().start)
        assertTrue(second.tokens.any { it.scope == "entity.name.tag.xml" })
        assertTrue(second.endState.stack.isEmpty())
    }

    @Test
    fun multiLineTagKeepsAttributesHighlighted() {
        val states = Highlighter.lineStates(Grammars.Xml, listOf("<manifest", "    package=\"ru.poneslas.docsy\">", "<b/>"))!!
        val s = scopes(Grammars.Xml, "    package=\"ru.poneslas.docsy\">", states[1])
        assertEquals("entity.other.attribute-name.xml", s["package"])
        assertTrue(states[2].stack.isEmpty())
    }

    @Test
    fun yamlIniAndCode() {
        val y = scopes(Grammars.Yaml, "  - name: Docsy # comment")
        assertEquals("entity.name.tag.yaml", y["name"])
        assertEquals("comment.line.number-sign.yaml", y["# comment"])
        assertEquals("constant.numeric.yaml", scopes(Grammars.Yaml, "port: 8080")["8080"])
        val i = scopes(Grammars.Ini, "[server]")
        assertEquals("entity.name.section.group-title.ini", i["server"])
        assertEquals("keyword.other.definition.ini", scopes(Grammars.Ini, "timeout = 30")["timeout"])
        val c = scopes(Grammars.Code, "val x = foo(\"s\") // done")
        assertEquals("keyword.control", c["val"])
        assertEquals("entity.name.function", c["foo"])
        assertEquals("comment.line.double-slash", c["// done"])
        assertEquals("variable.other", scopes(Grammars.Script, "echo \$HOME")["\$HOME"])
    }

    @Test
    fun everyRuleCompilesAndNoLineHangs() {
        val samples = listOf("", " ", "\"", "<", "<!--", "/*", "'''", "a".repeat(5000), "{\"k\":[1,2,{\"z\":\"\\\"\"}]}")
        for (g in Grammars.all) for (line in samples) Highlighter.tokenize(g, line)
    }

    @Test
    fun detection() {
        assertEquals(Grammars.Log, Grammars.forFileName("app.log"))
        assertEquals(Grammars.Json, Grammars.forFileName("package.JSON"))
        assertEquals(Grammars.Xml, Grammars.forFileName("AndroidManifest.xml"))
        assertEquals(Grammars.Ini, Grammars.forFileName(".env.local"))
        assertNull(Grammars.forFileName("notes.txt"))
        assertEquals(Grammars.Log, Grammars.sniff(listOf("10-23 10:11:04.245  1  2 I Tag: a", "10-23 10:11:04.246  1  2 W Tag: b")))
        assertEquals(Grammars.Json, Grammars.sniff(listOf("{", "  \"a\": 1", "}")))
        assertNull(Grammars.sniff(listOf("Дорогой дневник,", "сегодня всё хорошо.")))
    }

    @Test
    fun formatJsonKeepsContentAndIndents() {
        assertEquals("{\n  \"a\": [\n    1,\n    {}\n  ],\n  \"s\": \"x, {y}\"\n}", TextFormat.json("""{"a":[1,{}],"s":"x, {y}"}"""))
    }

    @Test
    fun formatXmlIndentsAndKeepsShortText() {
        assertEquals(
            "<?xml version=\"1.0\"?>\n<a>\n  <b>text</b>\n  <c/>\n  <!-- n -->\n</a>",
            TextFormat.xml("<?xml version=\"1.0\"?><a><b>text</b><c/><!-- n --></a>")
        )
    }

    @Test
    fun htmlEscapesAndColours() {
        val html = TextHtml.highlighted("<a href=\"x\">&</a>", Grammars.Xml)
        assertTrue(html.contains("&lt;"))
        assertTrue(!html.contains("<a href"))
        assertTrue(html.contains("<span style=\"color:#800000\">a</span>"))
    }
}
