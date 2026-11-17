package com.juridireader.app.ui.util

import java.text.Normalizer

/**
 * Localiza ocorrências de pesquisa de forma insensível a maiúsculas e acentos,
 * preservando os intervalos exatos (IntRange) no texto original para destaque visual.
 */
object SearchTextHighlighter {

    private data class NormalizedTextWithMap(
        val normalized: String,
        val originalIndices: IntArray
    )

    private fun normalizeWithIndexMap(input: String): NormalizedTextWithMap {
        val sb = StringBuilder(input.length)
        val map = ArrayList<Int>(input.length)

        for (i in input.indices) {
            val ch = input[i]
            val decomposed = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
            for (d in decomposed) {
                val type = Character.getType(d)
                if (type != Character.NON_SPACING_MARK.toInt() &&
                    type != Character.COMBINING_SPACING_MARK.toInt() &&
                    type != Character.ENCLOSING_MARK.toInt()
                ) {
                    sb.append(d.lowercaseChar())
                    map.add(i)
                }
            }
        }
        return NormalizedTextWithMap(sb.toString(), map.toIntArray())
    }

    private fun normalizeQuery(query: String): String {
        val decomposed = Normalizer.normalize(query.trim(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            val type = Character.getType(c)
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt() &&
                type != Character.ENCLOSING_MARK.toInt()
            ) {
                sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * Retorna os intervalos (inclusivo start..endInclusive) no texto original.
     * Tenta primeiro a expressão completa; se tiver múltiplas palavras e não houver
     * ocorrência contígua da frase exata, destaca cada palavra individual (>= 2 caracteres).
     */
    fun findMatchRanges(text: String, query: String): List<IntRange> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || text.isEmpty()) return emptyList()

        val normTarget = normalizeWithIndexMap(text)
        val normQuery = normalizeQuery(trimmed)
        if (normQuery.isEmpty()) return emptyList()

        val exactMatches = collectOccurrences(normTarget, normQuery)
        if (exactMatches.isNotEmpty()) {
            return exactMatches
        }

        // Fallback por termos individuais quando vindo de busca com múltiplas palavras
        val terms = normQuery.split(Regex("\\s+")).filter { it.length >= 2 }.distinct()
        if (terms.size <= 1) return emptyList()

        val combined = mutableListOf<IntRange>()
        for (term in terms) {
            combined.addAll(collectOccurrences(normTarget, term))
        }
        return mergeOverlappingRanges(combined)
    }

    private fun collectOccurrences(target: NormalizedTextWithMap, normTerm: String): List<IntRange> {
        if (normTerm.isEmpty() || target.normalized.length < normTerm.length) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var searchFrom = 0
        while (searchFrom <= target.normalized.length - normTerm.length) {
            val found = target.normalized.indexOf(normTerm, startIndex = searchFrom)
            if (found < 0) break
            val origStart = target.originalIndices[found]
            val origEnd = target.originalIndices[found + normTerm.length - 1]
            ranges.add(origStart..origEnd)
            searchFrom = found + normTerm.length
        }
        return ranges
    }

    private fun mergeOverlappingRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val result = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val curr = sorted[i]
            val prev = result.last()
            if (curr.first <= prev.last + 1) {
                result[result.lastIndex] = prev.first..maxOf(prev.last, curr.last)
            } else {
                result.add(curr)
            }
        }
        return result
    }
}
