package com.example.skylarkwallet.data.backup

import android.content.Context
import android.net.Uri
import com.example.skylarkwallet.domain.model.ImportSummary
import com.example.skylarkwallet.domain.repository.VoucherRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 端末移行用のバックアップファイルを書き出す・読み込む。
 * 保存先・読み込み元は利用者がファイル選択画面（SAF）で選ぶので、ストレージの権限は要らない。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VoucherRepository,
) {
    data class ExportResult(val password: String, val count: Int)

    /** 券を新しいパスワードで暗号化して [uri] に書き出す。パスワードは呼び出し側で利用者に表示する。 */
    suspend fun export(uri: Uri): ExportResult {
        val vouchers = repository.exportBackup()
        val password = BackupPassword.generate()
        val file = withContext(Dispatchers.Default) {
            BackupFormat.seal(BackupPayload.encode(vouchers), BackupPassword.normalize(password))
        }
        withContext(Dispatchers.IO) {
            // "wt": 既存のファイルを選んだときに古い中身が後ろに残らないよう切り詰める
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException()
            out.use { it.write(file) }
        }
        return ExportResult(password, vouchers.size)
    }

    /** [uri] のバックアップを [password] で復号し、登録済みでない券を追加する。 */
    suspend fun import(uri: Uri, password: String): ImportSummary {
        val file = withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
            input.use { it.readLimited(MAX_FILE_SIZE) }
        }
        val vouchers = withContext(Dispatchers.Default) {
            BackupPayload.decode(BackupFormat.open(file, BackupPassword.normalize(password)))
        }
        return repository.importBackup(vouchers)
    }

    private fun InputStream.readLimited(maxSize: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            // 大きすぎるファイルはこのアプリのバックアップではない（メモリを使い切らないよう読むのをやめる）
            if (out.size() + read > maxSize) throw BackupException.NotBackupFile()
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        const val MIME_TYPE = "application/octet-stream"
        const val FILE_EXTENSION = "skwb"

        /** 結果 HTML を含めても券1枚あたり数十 KB（圧縮後）なので、十分な余裕がある。 */
        private const val MAX_FILE_SIZE = 32 * 1024 * 1024
    }
}
