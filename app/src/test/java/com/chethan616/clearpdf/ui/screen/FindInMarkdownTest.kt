package com.chethan616.clearpdf.ui.screen

import com.chethan616.clearpdf.utils.markdown.MarkdownReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindInMarkdownTest {

    @Test
    fun findsInHeadingsListsTablesAndCodeInReadingOrder() {
        val doc = MarkdownReader.read("# Доки\n\n- пункт доки\n\n| a |\n|---|\n| ДОКИ |\n\n```\nдоки()\n```\n\nдоки и доки\n")
        val hits = findInMarkdown(doc.blocks, "доки")
        assertEquals(listOf(0, 1, 2, 3, 4, 4), hits.map { it.item })
        // Two identical texts stay two places: hits are keyed by the text object itself.
        assertTrue(hits[4].key === hits[5].key)
        assertEquals(listOf(0..3, 7..10), hits.drop(4).map { it.range })
    }
}
