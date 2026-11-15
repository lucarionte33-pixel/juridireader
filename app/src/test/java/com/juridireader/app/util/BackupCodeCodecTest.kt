package com.juridireader.app.util

import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity
import com.juridireader.app.data.local.entity.UserTextHighlightEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodeCodecTest {

    @Test
    fun `encode and decode preserves favorites notes highlights and textHighlights`() {
        val original = UserBackupPayload(
            version = 1,
            exportedAt = 1760000000000L,
            favorites = listOf(
                UserFavoriteEntity(articleId = "CF88_ART_5", createdAt = 1001L),
                UserFavoriteEntity(articleId = "CC_ART_186", createdAt = 1002L)
            ),
            notes = listOf(
                UserNoteEntity(
                    articleId = "CF88_ART_5",
                    noteText = "Cláusula pétrea — direitos e garantias fundamentais (§ 4º, art. 60).",
                    updatedAt = 2001L
                )
            ),
            highlights = listOf(
                UserHighlightEntity(articleId = "CP_ART_121", color = "AMARELO", updatedAt = 3001L)
            ),
            textHighlights = listOf(
                UserTextHighlightEntity(
                    id = 42L,
                    articleId = "CF88_ART_5",
                    startOffset = 10,
                    endOffset = 55,
                    selectedText = "todos são iguais perante a lei, sem distinção",
                    color = "VERDE",
                    createdAt = 4001L
                )
            )
        )

        val code = BackupCodeCodec.encode(original)
        assertTrue(code.startsWith("JURIDI1:"))

        val decodedResult = BackupCodeCodec.decode(code)
        assertTrue("Expected decode success, got: ${decodedResult.exceptionOrNull()}", decodedResult.isSuccess)

        val decoded = decodedResult.getOrThrow()
        assertEquals(original.exportedAt, decoded.exportedAt)
        assertEquals(original.favorites, decoded.favorites)
        assertEquals(original.notes, decoded.notes)
        assertEquals(original.highlights, decoded.highlights)
        assertEquals(1, decoded.textHighlights.size)
        assertEquals("CF88_ART_5", decoded.textHighlights[0].articleId)
        assertEquals(10, decoded.textHighlights[0].startOffset)
        assertEquals(55, decoded.textHighlights[0].endOffset)
        assertEquals(original.textHighlights[0].selectedText, decoded.textHighlights[0].selectedText)
        assertEquals("VERDE", decoded.textHighlights[0].color)
        assertEquals(4001L, decoded.textHighlights[0].createdAt)
    }

    @Test
    fun `decode tolerates line breaks and spaces added by messaging apps`() {
        val payload = UserBackupPayload(
            exportedAt = 1760000001234L,
            favorites = listOf(UserFavoriteEntity("CPC_ART_300", 500L)),
            notes = listOf(UserNoteEntity("CPC_ART_300", "Tutela de urgência", 600L))
        )

        val cleanCode = BackupCodeCodec.encode(payload)
        val wrappedCode = "  \n" + cleanCode.chunked(24).joinToString("\n ") + " \t\n"

        val result = BackupCodeCodec.decode(wrappedCode)
        assertTrue(result.isSuccess)
        val decoded = result.getOrThrow()
        assertEquals(payload.favorites, decoded.favorites)
        assertEquals(payload.notes, decoded.notes)
    }

    @Test
    fun `decode rejects tampered or truncated backup code via CRC32`() {
        val payload = UserBackupPayload(
            exportedAt = 1760000005555L,
            favorites = listOf(UserFavoriteEntity("CDC_ART_6", 700L))
        )
        val code = BackupCodeCodec.encode(payload)

        // Flip a character inside the payload segment
        val parts = code.split(":")
        val body = parts[2]
        val alteredChar = if (body.first() == 'A') 'B' else 'A'
        val tampered = "${parts[0]}:${parts[1]}:$alteredChar${body.drop(1)}"

        assertTrue(BackupCodeCodec.decode(tampered).isFailure)
        assertTrue(BackupCodeCodec.decode(code.dropLast(5)).isFailure)
        assertTrue(BackupCodeCodec.decode("INVALID:12345678:abc").isFailure)
    }

    @Test
    fun `encode and decode empty payload works cleanly`() {
        val empty = UserBackupPayload(exportedAt = 1760000009999L)
        val code = BackupCodeCodec.encode(empty)
        val decoded = BackupCodeCodec.decode(code).getOrThrow()
        assertEquals(0, decoded.totalCount)
        assertEquals(1760000009999L, decoded.exportedAt)
    }
}
