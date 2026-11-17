package com.juridireader.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextHighlighterTest {

    @Test
    fun `finds case and accent insensitive matches with exact original ranges`() {
        val text = "Art. 2º O sistema tributário nacional é regido pela Constituição e legislação tributária."
        val ranges = SearchTextHighlighter.findMatchRanges(text, "tributario")

        assertEquals(1, ranges.size)
        assertEquals("tributário", text.substring(ranges[0]))

        val rangesStem = SearchTextHighlighter.findMatchRanges(text, "tribut")
        assertEquals(2, rangesStem.size)
        assertEquals("tribut", text.substring(rangesStem[0]))
        assertEquals("tribut", text.substring(rangesStem[1]))
    }

    @Test
    fun `finds exact phrase first and falls back to individual terms when not contiguous`() {
        val text = "Todos são iguais perante a lei, garantindo aos brasileiros inviolabilidade do direito."

        val exact = SearchTextHighlighter.findMatchRanges(text, "iguais perante")
        assertEquals(1, exact.size)
        assertEquals("iguais perante", text.substring(exact[0]))

        val multiWord = SearchTextHighlighter.findMatchRanges(text, "iguais brasileiros")
        assertEquals(2, multiWord.size)
        assertEquals("iguais", text.substring(multiWord[0]))
        assertEquals("brasileiros", text.substring(multiWord[1]))
    }

    @Test
    fun `returns empty list for blank query or missing term`() {
        val text = "Art. 1º Toda pessoa é capaz de direitos e deveres na ordem civil."
        assertTrue(SearchTextHighlighter.findMatchRanges(text, "   ").isEmpty())
        assertTrue(SearchTextHighlighter.findMatchRanges(text, "inexistente").isEmpty())
    }
}
