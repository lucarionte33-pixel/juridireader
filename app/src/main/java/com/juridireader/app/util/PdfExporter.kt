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

        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val contentWidth = pageWidth - (margin * 2)

        val headerPaint = TextPaint().apply {
            color = Color.rgb(26, 54, 93)
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subHeaderPaint = TextPaint().apply {
            color = Color.rgb(107, 114, 128)
            textSize = 10f
            isAntiAlias = true
        }

        val articleTitlePaint = TextPaint().apply {
            color = Color.rgb(139, 30, 63)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val snippetTextPaint = TextPaint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 10f
            textSkewX = -0.15f // Italic
            isAntiAlias = true
        }

        val noteBodyPaint = TextPaint().apply {
            color = Color.rgb(120, 53, 15)
            textSize = 9.5f
            isAntiAlias = true
        }

        val boxBgPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val boxBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = margin.toFloat()

        // Cabeçalho Principal
        val docTitle = "$lawTitle • Caderno de Grifos & Anotações"
        val titleLayout = StaticLayout.Builder.obtain(
            docTitle, 0, docTitle.length, headerPaint, contentWidth
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        titleLayout.draw(canvas)
        currentY += titleLayout.height + 4f

        val subText = "Relatório consolidado de trechos destacados e notas de estudo • Gerado pelo Juridireader"
        val subLayout = StaticLayout.Builder.obtain(
            subText, 0, subText.length, subHeaderPaint, contentWidth
        ).build()
        canvas.save()
        canvas.translate(margin.toFloat(), currentY)
        subLayout.draw(canvas)
        canvas.restore()
        currentY += subLayout.height + 16f

        fun checkPageBreak(requiredHeight: Float) {
            if (currentY + requiredHeight > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = margin.toFloat()
            }
        }

        for (art in filtered) {
            val artTitleText = "${art.articleNumber} — ${art.lawTitle.ifBlank { art.lawCode }}"
            val artTitleLayout = StaticLayout.Builder.obtain(
                artTitleText, 0, artTitleText.length, articleTitlePaint, contentWidth
            ).build()

            checkPageBreak(artTitleLayout.height + 40f)

            // Título do Artigo
            canvas.save()
            canvas.translate(margin.toFloat(), currentY)
            artTitleLayout.draw(canvas)
            canvas.restore()
            currentY += artTitleLayout.height + 6f

            // 1. Trechos Grifados (apenas as partes selecionadas!)
            if (art.textHighlights.isNotEmpty()) {
                for (th in art.textHighlights) {
                    val quote = "“${th.selectedText.trim()}”"
                    val snippetLayout = StaticLayout.Builder.obtain(
                        quote, 0, quote.length, snippetTextPaint, contentWidth - 24
                    ).build()

                    val blockHeight = snippetLayout.height + 14f
                    checkPageBreak(blockHeight + 10f)

                    val hlEnum = HighlightColor.fromName(th.color)
                    val (fillColor, strokeColor) = when (hlEnum) {
                        HighlightColor.YELLOW -> Pair(Color.rgb(254, 249, 195), Color.rgb(234, 179, 8))
                        HighlightColor.GREEN -> Pair(Color.rgb(220, 252, 231), Color.rgb(34, 197, 94))
                        HighlightColor.BLUE -> Pair(Color.rgb(219, 234, 254), Color.rgb(59, 130, 246))
                        HighlightColor.PINK -> Pair(Color.rgb(252, 231, 243), Color.rgb(236, 72, 153))
                        HighlightColor.NONE -> Pair(Color.rgb(243, 244, 246), Color.rgb(209, 213, 219))
                    }

                    boxBgPaint.color = fillColor
                    boxBorderPaint.color = strokeColor

                    val boxLeft = margin.toFloat()
                    val boxTop = currentY
                    val boxRight = (pageWidth - margin).toFloat()
                    val boxBottom = currentY + blockHeight

                    canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 4f, 4f, boxBgPaint)
                    canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 4f, 4f, boxBorderPaint)

                    canvas.save()
                    canvas.translate(boxLeft + 12f, boxTop + 7f)
                    snippetLayout.draw(canvas)
                    canvas.restore()

                    currentY = boxBottom + 6f
                }
            } else if (!art.highlightColor.isNullOrBlank()) {
                // Grifo legado de artigo inteiro
                val quote = "“${art.content.trim()}”"
                val snippetLayout = StaticLayout.Builder.obtain(
                    quote, 0, quote.length, snippetTextPaint, contentWidth - 24
                ).build()
                val blockHeight = snippetLayout.height + 14f
                checkPageBreak(blockHeight + 10f)

                boxBgPaint.color = Color.rgb(254, 249, 195)
                boxBorderPaint.color = Color.rgb(234, 179, 8)

                val boxLeft = margin.toFloat()
                val boxTop = currentY
                val boxRight = (pageWidth - margin).toFloat()
                val boxBottom = currentY + blockHeight

                canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 4f, 4f, boxBgPaint)
                canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 4f, 4f, boxBorderPaint)

                canvas.save()
                canvas.translate(boxLeft + 12f, boxTop + 7f)
                snippetLayout.draw(canvas)
                canvas.restore()

                currentY = boxBottom + 6f
            }

            // 2. Anotação pessoal do usuário (se houver)
            if (!art.userNote.isNullOrBlank()) {
                val noteText = "📝 Anotação: ${art.userNote.trim()}"
                val noteLayout = StaticLayout.Builder.obtain(
                    noteText, 0, noteText.length, noteBodyPaint, contentWidth - 24
                ).build()

                val blockHeight = noteLayout.height + 12f
                checkPageBreak(blockHeight + 10f)

                boxBgPaint.color = Color.rgb(254, 243, 199)
                boxBorderPaint.color = Color.rgb(217, 119, 6)

                val boxLeft = margin.toFloat()
                val boxTop = currentY
                val boxRight = (pageWidth - margin).toFloat()
                val boxBottom = currentY + blockHeight

                canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 4f, 4f, boxBgPaint)
                canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 4f, 4f, boxBorderPaint)

                canvas.save()
                canvas.translate(boxLeft + 12f, boxTop + 6f)
                noteLayout.draw(canvas)
                canvas.restore()

                currentY = boxBottom + 6f
            }

            currentY += 8f
        }

        document.finishPage(page)

        return try {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeName = lawTitle.replace("[^a-zA-Z0-9]".toRegex(), "_").take(25)
            val outFile = File(docsDir, "${safeName}_grifos_anotacoes.pdf")
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

    fun exportSnippetToPdf(
        context: Context,
        lawTitle: String,
        articleNumber: String,
        selectedSnippet: String
    ): File? {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val contentWidth = pageWidth - (margin * 2)

        val headerPaint = TextPaint().apply {
            color = Color.rgb(26, 54, 93)
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val articleTitlePaint = TextPaint().apply {
            color = Color.rgb(139, 30, 63)
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = TextPaint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 11f
            textSkewX = -0.12f
            isAntiAlias = true
        }

        val footerPaint = TextPaint().apply {
            color = Color.rgb(120, 120, 120)
            textSize = 9f
            isAntiAlias = true
        }

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        var currentY = margin.toFloat()

        // Cabeçalho da Lei
        val titleLayout = StaticLayout.Builder.obtain(
            lawTitle, 0, lawTitle.length, headerPaint, contentWidth
        ).build()
        titleLayout.draw(canvas)
        currentY += titleLayout.height + 10f

        // Artigo de Referência
        val artRef = "Trecho Selecionado • $articleNumber"
        val artRefLayout = StaticLayout.Builder.obtain(
            artRef, 0, artRef.length, articleTitlePaint, contentWidth
        ).build()
        canvas.save()
        canvas.translate(margin.toFloat(), currentY)
        artRefLayout.draw(canvas)
        canvas.restore()
        currentY += artRefLayout.height + 14f

        // Trecho Destacado
        val quote = "“${selectedSnippet.trim()}”"
        val snippetLayout = StaticLayout.Builder.obtain(
            quote, 0, quote.length, bodyPaint, contentWidth - 28
        ).build()

        val boxPaint = Paint().apply {
            color = Color.rgb(254, 249, 195)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(234, 179, 8)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }

        val blockHeight = snippetLayout.height + 24f
        val boxLeft = margin.toFloat()
        val boxTop = currentY
        val boxRight = (pageWidth - margin).toFloat()
        val boxBottom = currentY + blockHeight

        canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 6f, 6f, boxPaint)
        canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 6f, 6f, borderPaint)

        canvas.save()
        canvas.translate(boxLeft + 14f, boxTop + 12f)
        snippetLayout.draw(canvas)
        canvas.restore()

        currentY = boxBottom + 20f

        // Rodapé
        val footerText = "Documento extraído via Juridireader • Vade Mecum Digital Offline"
        val footerLayout = StaticLayout.Builder.obtain(
            footerText, 0, footerText.length, footerPaint, contentWidth
        ).build()
        canvas.save()
        canvas.translate(margin.toFloat(), currentY)
        footerLayout.draw(canvas)
        canvas.restore()

        document.finishPage(page)

        return try {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeName = lawTitle.replace("[^a-zA-Z0-9]".toRegex(), "_").take(25)
            val outFile = File(docsDir, "${safeName}_trecho_${System.currentTimeMillis()}.pdf")
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
}
