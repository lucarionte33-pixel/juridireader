package com.juridireader.app.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.juridireader.app.data.local.entity.UserTextHighlightEntity
import com.juridireader.app.ui.model.HighlightColor

object LegalTextFormatter {

    // Cor bordô / vinho clássica para Títulos, Capítulos e Seções jurídicas
    val HeadingColor = Color(0xFF8B1E3F)
    val SubtleRefColor = Color(0xFF6B7280)

    fun formatArticleText(
        content: String,
        textHighlights: List<UserTextHighlightEntity> = emptyList(),
        primaryColor: Color
    ): AnnotatedString {
        return buildAnnotatedString {
            append(content)

            // 1. Destaque em Negrito para início de Artigo (ex: "Art. 1º", "Artigo 5º")
            val artRegex = Regex("""\b(Art(?:\.|\b|igo)\s*[0-9]+[ºoªa\.\-]*)""", RegexOption.IGNORE_CASE)
            for (match in artRegex.findAll(content)) {
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            // 2. Destaque em Negrito para Parágrafos ("Parágrafo único", "§ 1º")
            val parRegex = Regex("""\b(Par[áa]grafo\s+[úu]nico\.?|§\s*[0-9]+[ºoªa\.\-]*)""", RegexOption.IGNORE_CASE)
            for (match in parRegex.findAll(content)) {
                addStyle(
                    style = SpanStyle(fontWeight = FontWeight.Bold),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            // 3. Destaque em Negrito para Incisos no início de linha ("I - ", "II - ", etc.)
            val incisoRegex = Regex("""(?:^|\n\n|\n)\s*([IVXLCDM]+\s*[-–—])""")
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

            // 4. Estilo discreto e itálico para remissões "(Vide ...)" ou "(Redação dada pela ...)"
            val refRegex = Regex("""\((Vide[^\)]+|Reda[çc][ãa]o dada[^\)]+|Inclu[íi]do pel[^\)]+)\)""", RegexOption.IGNORE_CASE)
            for (match in refRegex.findAll(content)) {
                addStyle(
                    style = SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = SubtleRefColor
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
                        style = SpanStyle(background = bgColor),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
}
