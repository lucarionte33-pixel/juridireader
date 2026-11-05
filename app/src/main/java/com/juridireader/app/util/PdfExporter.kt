package com.juridireader.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.juridireader.app.data.local.entity.ArticleWithUserData
import com.juridireader.app.ui.model.HighlightColor
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun exportLawToPdf(
        context: Context,
        lawTitle: String,
        articles: List<ArticleWithUserData>
    ): File? {
        val document = PdfDocument()

        val pageWidth = 595 // A4 standard width (pt)
        val pageHeight = 842 // A4 standard height (pt)
        val margin = 40
        val contentWidth = pageWidth - (margin * 2)

        val headerPaint = TextPaint().apply {
            color = Color.rgb(26, 54, 93)
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val articleTitlePaint = TextPaint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = TextPaint().apply {
            color = Color.rgb(50, 50, 50)
            textSize = 10f
            isAntiAlias = true
        }

        val noteTitlePaint = TextPaint().apply {
            color = Color.rgb(180, 83, 9)
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val noteBodyPaint = TextPaint().apply {
            color = Color.rgb(80, 60, 40)
            textSize = 9f
            isAntiAlias = true
        }

        val highlightPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = margin.toFloat()

        // Cabeçalho da Lei
        val titleLayout = StaticLayout.Builder.obtain(
            lawTitle, 0, lawTitle.length, headerPaint, contentWidth
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        titleLayout.draw(canvas)
        currentY += titleLayout.height + 20f

        for (art in articles) {
            // Título do Artigo
            val artTitleText = "${art.articleNumber} - ${art.lawCode}"
            val artTitleLayout = StaticLayout.Builder.obtain(
                artTitleText, 0, artTitleText.length, articleTitlePaint, contentWidth
            ).build()

            // Texto do Artigo
            val bodyLayout = StaticLayout.Builder.obtain(
                art.content, 0, art.content.length, bodyPaint, contentWidth
            ).build()

            // Nota (se houver)
            val noteLayout = art.userNote?.let { note ->
                val fullNote = "Anotação: $note"
                StaticLayout.Builder.obtain(
                    fullNote, 0, fullNote.length, noteBodyPaint, contentWidth - 20
                ).build()
            }

            val articleTotalHeight = artTitleLayout.height + 6f + bodyLayout.height +
                    (if (noteLayout != null) noteLayout.height + 20f else 0f) + 16f

            // Nova página se não couber
            if (currentY + articleTotalHeight > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = margin.toFloat()
            }

            // Fundo de grifo (se houver)
            if (!art.highlightColor.isNullOrBlank()) {
                val hl = HighlightColor.fromName(art.highlightColor)
                if (hl != HighlightColor.NONE) {
                    highlightPaint.color = android.graphics.Color.parseColor(hl.hexString)
                    canvas.drawRoundRect(
                        margin.toFloat() - 4f,
                        currentY - 2f,
                        (pageWidth - margin).toFloat() + 4f,
                        currentY + artTitleLayout.height + 6f + bodyLayout.height + 6f,
                        6f, 6f, highlightPaint
                    )
                }
            }

            // Desenhar título do artigo
            canvas.save()
            canvas.translate(margin.toFloat(), currentY)
            artTitleLayout.draw(canvas)
            canvas.restore()
            currentY += artTitleLayout.height + 4f

            // Desenhar corpo do artigo
            canvas.save()
            canvas.translate(margin.toFloat(), currentY)
            bodyLayout.draw(canvas)
            canvas.restore()
            currentY += bodyLayout.height + 8f

            // Desenhar bloco de nota
            if (noteLayout != null) {
                val noteBgPaint = Paint().apply {
                    color = Color.rgb(254, 243, 199)
                    style = Paint.Style.FILL
                }
                val noteBorderPaint = Paint().apply {
                    color = Color.rgb(245, 158, 11)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }

                val noteTop = currentY
                val noteBottom = currentY + noteLayout.height + 12f

                canvas.drawRoundRect(
                    margin.toFloat() + 4f, noteTop,
                    (pageWidth - margin).toFloat() - 4f, noteBottom,
                    4f, 4f, noteBgPaint
                )
                canvas.drawRoundRect(
                    margin.toFloat() + 4f, noteTop,
                    (pageWidth - margin).toFloat() - 4f, noteBottom,
                    4f, 4f, noteBorderPaint
                )

                canvas.save()
                canvas.translate(margin.toFloat() + 12f, noteTop + 6f)
                noteLayout.draw(canvas)
                canvas.restore()

                currentY = noteBottom + 10f
            }

            currentY += 12f
        }

        document.finishPage(page)

        return try {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeName = lawTitle.replace("[^a-zA-Z0-9]".toRegex(), "_").take(30)
            val outFile = File(docsDir, "${safeName}_export.pdf")
            val outputStream = FileOutputStream(outFile)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            document.close()
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    fun exportCustomizedToPdf(
        context: Context,
        lawTitle: String,
        articles: List<ArticleWithUserData>
    ): File? {
        val filtered = articles.filter { art ->
            art.textHighlights.isNotEmpty() || !art.userNote.isNullOrBlank() || !art.highlightColor.isNullOrBlank()
        }
        if (filtered.isEmpty()) return null
        return exportLawToPdf(context, "$lawTitle (Partes Grifadas & Anotações)", filtered)
    }

    fun exportSnippetToPdf(
        context: Context,
        lawTitle: String,
        articleNumber: String,
        selectedSnippet: String
    ): File? {
        val snippetArticle = ArticleWithUserData(
            id = "SNIPPET",
            lawCode = "",
            lawTitle = lawTitle,
            category = "",
            articleNumber = articleNumber,
            content = selectedSnippet,
            orderIndex = 1
        )
        return exportLawToPdf(context, "$lawTitle - Trecho Selecionado", listOf(snippetArticle))
    }
}
