package com.juridireader.app.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.juridireader.app.data.local.entity.UserTextHighlightEntity
import com.juridireader.app.ui.model.HighlightColor

object LegalTextFormatter {

    // Cor bordô / vinho clássica para Títulos, Capítulos e Seções jurídicas
    val HeadingColor = Color(0xFF8B1E3F)
    val HeadingColorDark = Color(0xFFE5989B)
    val SubtleRefColor = Color(0xFF5B6B7C)

    // Cores de destaque estilo navegador para navegação na pesquisa
    val SearchMatchBg = Color(0xFFFDE047).copy(alpha = 0.60f)
    val ActiveSearchMatchBg = Color(0xFF4ADE80)

    private val headingPrefixRegex = Regex("""^###\s*([\s\S]+?)\s*###\n*""")
    private val sectionHeaderLineRegex = Regex(
        """^(LIVRO|T[ÍI]TULO|CAP[ÍI]TULO|SE[ÇC][ÃA]O|SUBSE[ÇC][ÃA]O|PARTE|DISPOSI[ÇC][ÃA]O\s+PRELIMINAR)\b.*""",
        RegexOption.IGNORE_CASE
    )

    fun extractHeadingAndBody(content: String): Pair<String?, String> {
        val m = headingPrefixRegex.find(content)
        return if (m != null) {
            val heading = m.groups[1]?.value?.trim()
            val body = content.substring(m.range.last + 1).trimStart()
            heading to body
        } else {
            null to content
        }
    }

    fun stripHeadingMarker(content: String): String {
        return extractHeadingAndBody(content).second
    }

    /**
     * Formata cabeçalhos estruturais em múltiplas linhas:
     * linha estrutural ("TÍTULO I", "CAPÍTULO IV") em negrito e subtítulo em peso normal.
     */
    fun formatHeadingText(heading: String, headingColor: Color): AnnotatedString {
        return buildAnnotatedString {
            append(heading)
            addStyle(SpanStyle(color = headingColor), 0, heading.length)
            var cursor = 0
            for (line in heading.split('\n')) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && sectionHeaderLineRegex.matches(trimmed)) {
                    addStyle(
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                        start = cursor,
                        end = (cursor + line.length).coerceAtMost(heading.length)
                    )
                }
                cursor += line.length + 1
            }
        }
    }

    fun formatArticleText(
        content: String,
        textHighlights: List<UserTextHighlightEntity> = emptyList(),
        primaryColor: Color,
        refColor: Color = SubtleRefColor,
        searchMatches: List<IntRange> = emptyList(),
        activeSearchMatch: IntRange? = null
    ): AnnotatedString {
        return buildAnnotatedString {
            append(content)

            // 1. Destaque em Negrito para início de Artigo (ex: "Art. 1º.", "Artigo 5º")
            val artRegex = Regex("""(?:^|\n\n|\n)\s*(Art(?:\.|igo)\s*[0-9]+[ºoªa\.\-A-Za-z0-9]*\.?)""")
            for (match in artRegex.findAll(content)) {
                val g = match.groups[1] ?: continue
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    ),
                    start = g.range.first,
                    end = g.range.last + 1
                )
            }

            // 2. Destaque em Negrito para Parágrafos no início do parágrafo ("Parágrafo único.", "§ 1º")
            val parRegex = Regex("""(?:^|\n\n|\n)\s*(Par[áa]grafo\s+[úu]nico\.?|§\s*[0-9]+[ºoªa\.\-A-Za-z0-9]*\.?)""", RegexOption.IGNORE_CASE)
            for (match in parRegex.findAll(content)) {
                val g = match.groups[1] ?: continue
                addStyle(
                    style = SpanStyle(fontWeight = FontWeight.Bold),
                    start = g.range.first,
                    end = g.range.last + 1
                )
            }

            // 3. Destaque em SemiBold para Incisos e Alíneas no início de linha
            val incisoRegex = Regex("""(?:^|\n\n|\n)\s*([IVXLCDM]+\s*[-–—]|[a-z]\))""")
            for (match in incisoRegex.findAll(content)) {
                val group = match.groups[1]
                if (group != null) {
                    addStyle(
                        style = SpanStyle(fontWeight = FontWeight.SemiBold),
                        start = group.range.first,
                        end = group.range.last + 1
                    )
                }
            }

            // 4. Estilo discreto para notas legislativas ("(Vide ...)", "(Redação dada ...)", etc.)
            val refRegex = Regex(
                """\((Vide|Reda[çc][ãa]o|Inclu[íi]d[oa]|Revogad[oa]|Vig[êe]ncia|Acrescid[oa]|Acrescentad[oa]|Alterad[oa]|Promulga[çc][ãa]o|Regulamento|Produ[çc][ãa]o|VETAD[OA])[^\)]*\)""",
                RegexOption.IGNORE_CASE
            )
            for (match in refRegex.findAll(content)) {
                addStyle(
                    style = SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = refColor
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            // 5. Aplicar Grifos selecionados pelo usuário (UserTextHighlightEntity)
            for (h in textHighlights) {
                var start = h.startOffset.coerceIn(0, content.length)
                var end = h.endOffset.coerceIn(start, content.length)
                if (h.selectedText.isNotBlank()) {
                    val snippet = h.selectedText
                    val matchesExact = (start < end && content.substring(start, end) == snippet)
                    if (!matchesExact) {
                        val idx = content.indexOf(snippet)
                        if (idx >= 0) {
                            start = idx
                            end = idx + snippet.length
                        }
                    }
                }
                if (start < end) {
                    val colorEnum = HighlightColor.fromName(h.color)
                    val bgColor = if (colorEnum != HighlightColor.NONE) {
                        colorEnum.composeColor.copy(alpha = 0.55f)
                    } else {
                        Color(0xFFFEF08A)
                    }
                    addStyle(
                        style = SpanStyle(background = bgColor, color = Color.Black),
                        start = start,
                        end = end
                    )
                }
            }

            // 6. Destaque estilo navegador para palavras pesquisadas (todas em amarelo, ativa em verde)
            for (range in searchMatches) {
                val start = range.first.coerceIn(0, content.length)
                val end = (range.last + 1).coerceIn(start, content.length)
                if (start < end) {
                    val isActive = (activeSearchMatch == range)
                    addStyle(
                        style = SpanStyle(
                            background = if (isActive) ActiveSearchMatchBg else SearchMatchBg,
                            color = Color.Black,
                            fontWeight = if (isActive) FontWeight.Bold else null
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
}
