package com.example.skylarkwallet.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * バックアップファイルの暗号化コンテナ（形式 v1）。
 *
 * ```
 * 0   "SKWB"                    4
 * 4   形式のバージョン（1）      1
 * 5   鍵導出の方式（1 = PBKDF2-HMAC-SHA256）  1
 * 6   反復回数（int32 BE）       4
 * 10  salt                      16
 * 26  nonce                     12
 * 38  AES-256-GCM の暗号文と tag
 * ```
 * 先頭38バイト（ヘッダー）を associated data にして、ヘッダーの改ざんも検知する。
 * パスワードは [BackupPassword.normalize] 済みの値を渡す。
 */
object BackupFormat {

    const val DEFAULT_ITERATIONS = 200_000
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 5_000_000

    private val MAGIC = "SKWB".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val HEADER_SIZE = 4 + 1 + 1 + 4 + SALT_SIZE + NONCE_SIZE

    fun seal(
        plain: ByteArray,
        password: String,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS)
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC)
            .put(VERSION)
            .put(KDF_PBKDF2_SHA256)
            .putInt(iterations)
            .put(salt)
            .put(nonce)
            .array()
        val cipher = aesGcm(Cipher.ENCRYPT_MODE, password, salt, iterations, nonce, header)
        return header + cipher.doFinal(plain)
    }

    fun open(file: ByteArray, password: String): ByteArray {
        if (file.size < HEADER_SIZE || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupException.NotBackupFile()
        }
        val buffer = ByteBuffer.wrap(file, MAGIC.size, HEADER_SIZE - MAGIC.size)
        if (buffer.get() != VERSION || buffer.get() != KDF_PBKDF2_SHA256) throw BackupException.UnsupportedVersion()
        val iterations = buffer.getInt()
        // 細工したファイルで極端な反復回数を指定され、端末が固まるのを防ぐ
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException.NotBackupFile()
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_SIZE).also { buffer.get(it) }
        val header = file.copyOfRange(0, HEADER_SIZE)
        return try {
            aesGcm(Cipher.DECRYPT_MODE, password, salt, iterations, nonce, header)
                .doFinal(file, HEADER_SIZE, file.size - HEADER_SIZE)
        } catch (e: GeneralSecurityException) {
            throw BackupException.WrongPasswordOrCorrupted(e)
        }
    }

    private fun aesGcm(
        mode: Int,
        password: String,
        salt: ByteArray,
        iterations: Int,
        nonce: ByteArray,
        header: ByteArray,
    ): Cipher {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(header)
            }
        } finally {
            keyBytes.fill(0)
        }
    }
}

/** バックアップファイルを読めなかった理由。 */
sealed class BackupException(cause: Throwable? = null) : Exception(cause) {
    /** このアプリのバックアップファイルではない。 */
    class NotBackupFile : BackupException()

    /** 新しいバージョンのアプリで作られたなど、読めない形式。 */
    class UnsupportedVersion : BackupException()

    /** パスワードが違う、またはファイルが壊れている（AES-GCM では区別できない）。 */
    class WrongPasswordOrCorrupted(cause: Throwable? = null) : BackupException(cause)
}
