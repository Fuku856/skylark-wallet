package com.example.skylarkwallet.data.backup

import com.example.skylarkwallet.domain.model.VoucherBackup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 暗号化する前の中身（gzip した JSON）。 */
object BackupPayload {

    private const val VERSION = 1

    /** 展開後の上限。細工したファイル（圧縮爆弾）でメモリを使い切らないようにする。 */
    const val MAX_PLAIN_SIZE = 64 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Root(
        val version: Int,
        val exportedAt: Long,
        val vouchers: List<Entry>,
    )

    @Serializable
    private data class Entry(
        val code9: String,
        val barcode13: String? = null,
        val resultHtml: String? = null,
        val balance: Int? = null,
        val expiryAt: Long? = null,
        val balanceCheckedAt: Long? = null,
        val lastUpdatedAt: Long? = null,
        val memo: String = "",
        val createdAt: Long,
    )

    fun encode(vouchers: List<VoucherBackup>, exportedAt: Long = System.currentTimeMillis()): ByteArray {
        val text = json.encodeToString(Root.serializer(), Root(VERSION, exportedAt, vouchers.map { it.toEntry() }))
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray, maxPlainSize: Int = MAX_PLAIN_SIZE): List<VoucherBackup> {
        val plain = try {
            gunzip(bytes, maxPlainSize)
        } catch (e: IOException) {
            throw BackupException.WrongPasswordOrCorrupted(e)
        }
        val root = try {
            json.decodeFromString(Root.serializer(), String(plain, Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupException.WrongPasswordOrCorrupted(e)
        } catch (e: IllegalArgumentException) {
            throw BackupException.WrongPasswordOrCorrupted(e)
        }
        if (root.version != VERSION) throw BackupException.UnsupportedVersion()
        return root.vouchers.map { it.toDomain() }
    }

    private fun gunzip(bytes: ByteArray, maxSize: Int): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > maxSize) throw IOException("too large")
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun VoucherBackup.toEntry() = Entry(
        code9 = code9,
        barcode13 = barcode13,
        resultHtml = resultHtml,
        balance = balance,
        expiryAt = expiryAt,
        balanceCheckedAt = balanceCheckedAt,
        lastUpdatedAt = lastUpdatedAt,
        memo = memo,
        createdAt = createdAt,
    )

    private fun Entry.toDomain() = VoucherBackup(
        code9 = code9,
        barcode13 = barcode13,
        resultHtml = resultHtml,
        balance = balance,
        expiryAt = expiryAt,
        balanceCheckedAt = balanceCheckedAt,
        lastUpdatedAt = lastUpdatedAt,
        memo = memo,
        createdAt = createdAt,
    )
}
