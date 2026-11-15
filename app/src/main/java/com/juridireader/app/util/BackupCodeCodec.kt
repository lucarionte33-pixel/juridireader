package com.juridireader.app.util

import com.juridireader.app.data.local.entity.UserFavoriteEntity
import com.juridireader.app.data.local.entity.UserHighlightEntity
import com.juridireader.app.data.local.entity.UserNoteEntity
import com.juridireader.app.data.local.entity.UserTextHighlightEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class ImportMode {
    MERGE,
    REPLACE
}

data class UserBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val favorites: List<UserFavoriteEntity> = emptyList(),
    val notes: List<UserNoteEntity> = emptyList(),
    val highlights: List<UserHighlightEntity> = emptyList(),
    val textHighlights: List<UserTextHighlightEntity> = emptyList()
) {
    val totalCount: Int
        get() = favorites.size + notes.size + highlights.size + textHighlights.size
}

data class ImportSummary(
    val addedFavorites: Int,
    val addedOrUpdatedNotes: Int,
    val addedHighlights: Int,
    val addedTextHighlights: Int,
    val mode: ImportMode
)

/**
 * Codifica e decodifica notas, favoritos e grifos do usuário em uma string compacta offline,
 * comprimida com GZIP + Base64 e protegida com checksum CRC32.
 */
object BackupCodeCodec {

    private const val PREFIX = "JURIDI1"
    private const val PAYLOAD_VERSION = 1

    fun encode(payload: UserBackupPayload): String {
        val root = JSONObject().apply {
            put("v", PAYLOAD_VERSION)
            put("ts", payload.exportedAt)

            put("fav", JSONArray().apply {
                payload.favorites.forEach { fav ->
                    put(JSONArray().apply {
                        put(fav.articleId)
                        put(fav.createdAt)
                    })
                }
            })

            put("not", JSONArray().apply {
                payload.notes.forEach { note ->
                    put(JSONArray().apply {
                        put(note.articleId)
                        put(note.noteText)
                        put(note.updatedAt)
                    })
                }
            })

            put("hl", JSONArray().apply {
                payload.highlights.forEach { hl ->
                    put(JSONArray().apply {
                        put(hl.articleId)
                        put(hl.color)
                        put(hl.updatedAt)
                    })
                }
            })

            put("thl", JSONArray().apply {
                payload.textHighlights.forEach { th ->
                    put(JSONArray().apply {
                        put(th.articleId)
                        put(th.startOffset)
                        put(th.endOffset)
                        put(th.selectedText)
                        put(th.color)
                        put(th.createdAt)
                    })
                }
            })
        }

        val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
        val compressed = gzip(jsonBytes)
        val crcHex = computeCrc32Hex(compressed)
        val base64 = Base64.getEncoder().encodeToString(compressed)
        return "$PREFIX:$crcHex:$base64"
    }

    fun decode(rawInput: String): Result<UserBackupPayload> {
        return try {
            // Remove espaços, quebras de linha e tabs inseridos ao copiar/colar por mensageiros
            val cleaned = rawInput.replace(Regex("\\s+"), "").trim()
            if (cleaned.isEmpty()) {
                return Result.failure(IllegalArgumentException("Cole um código de backup válido."))
            }

            val parts = cleaned.split(":", limit = 3)
            if (parts.size != 3 || parts[0] != PREFIX) {
                return Result.failure(
                    IllegalArgumentException("Formato de código inválido. Deve começar com \"$PREFIX:\".")
                )
            }

            val expectedCrc = parts[1].lowercase()
            val base64Payload = parts[2]
            if (base64Payload.isEmpty()) {
                return Result.failure(IllegalArgumentException("O código está incompleto."))
            }

            val compressed = Base64.getDecoder().decode(base64Payload)
            val actualCrc = computeCrc32Hex(compressed)
            if (actualCrc != expectedCrc) {
                return Result.failure(
                    IllegalArgumentException("Código incompleto ou corrompido (falha na verificação CRC). Copie o código inteiro novamente.")
                )
            }

            val jsonStr = gunzip(compressed).toString(Charsets.UTF_8)
            val root = JSONObject(jsonStr)
            val version = root.optInt("v", 1)
            val exportedAt = root.optLong("ts", System.currentTimeMillis())

            val favorites = mutableListOf<UserFavoriteEntity>()
            root.optJSONArray("fav")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONArray(i)
                    val articleId = item.getString(0)
                    if (articleId.isNotBlank()) {
                        favorites.add(
                            UserFavoriteEntity(
                                articleId = articleId,
                                createdAt = item.optLong(1, System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            val notes = mutableListOf<UserNoteEntity>()
            root.optJSONArray("not")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONArray(i)
                    val articleId = item.getString(0)
                    val noteText = item.getString(1)
                    if (articleId.isNotBlank() && noteText.isNotBlank()) {
                        notes.add(
                            UserNoteEntity(
                                articleId = articleId,
                                noteText = noteText,
                                updatedAt = item.optLong(2, System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            val highlights = mutableListOf<UserHighlightEntity>()
            root.optJSONArray("hl")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONArray(i)
                    val articleId = item.getString(0)
                    val color = item.optString(1, "YELLOW")
                    if (articleId.isNotBlank()) {
                        highlights.add(
                            UserHighlightEntity(
                                articleId = articleId,
                                color = color,
                                updatedAt = item.optLong(2, System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            val textHighlights = mutableListOf<UserTextHighlightEntity>()
            root.optJSONArray("thl")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONArray(i)
                    val articleId = item.getString(0)
                    val start = item.getInt(1)
                    val end = item.getInt(2)
                    val selectedText = item.getString(3)
                    val color = item.optString(4, "YELLOW")
                    val createdAt = item.optLong(5, System.currentTimeMillis())
                    if (articleId.isNotBlank() && selectedText.isNotEmpty()) {
                        textHighlights.add(
                            UserTextHighlightEntity(
                                articleId = articleId,
                                startOffset = start,
                                endOffset = end,
                                selectedText = selectedText,
                                color = color,
                                createdAt = createdAt
                            )
                        )
                    }
                }
            }

            Result.success(
                UserBackupPayload(
                    version = version,
                    exportedAt = exportedAt,
                    favorites = favorites,
                    notes = notes,
                    highlights = highlights,
                    textHighlights = textHighlights
                )
            )
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("Não foi possível ler o código de backup. Verifique se foi copiado por completo.")
            )
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(compressed: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
    }

    private fun computeCrc32Hex(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        return "%08x".format(crc.value)
    }
}
