package com.example.skylarkwallet.data.repository

import android.util.Log
import com.example.skylarkwallet.data.local.crypto.StringCipher
import com.example.skylarkwallet.data.local.db.VoucherDao
import com.example.skylarkwallet.data.local.db.VoucherEntity
import com.example.skylarkwallet.domain.model.CodeNormalizer
import com.example.skylarkwallet.domain.model.Ean13
import com.example.skylarkwallet.domain.model.ImportSummary
import com.example.skylarkwallet.domain.model.MemoNormalizer
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.domain.model.VoucherBackup
import com.example.skylarkwallet.domain.model.VoucherResult
import com.example.skylarkwallet.domain.repository.VoucherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val dao: VoucherDao,
    private val cipher: StringCipher,
) : VoucherRepository {

    override fun observeVouchers(): Flow<List<Voucher>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }.flowOn(Dispatchers.Default)

    override suspend fun getVouchers(): List<Voucher> = withContext(Dispatchers.Default) {
        dao.getAll().mapNotNull { it.toDomain() }
    }

    override suspend fun getVoucher(id: String): Voucher? = withContext(Dispatchers.Default) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun findByCode(code9: String): Voucher? = getVouchers().firstOrNull { it.code9 == code9 }

    override suspend fun add(code9: String, memo: String): Voucher = withContext(Dispatchers.Default) {
        val id = UUID.randomUUID().toString()
        val entity = VoucherEntity(
            id = id,
            encryptedCode9 = cipher.encrypt(code9, id),
            memo = memo,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
        entity.toDomain() ?: error("failed to decrypt new voucher")
    }

    override suspend fun saveResult(id: String, result: VoucherResult, html: String) = withContext(Dispatchers.Default) {
        val current = dao.getById(id) ?: return@withContext
        dao.update(
            current.copy(
                encryptedBarcode13 = cipher.encrypt(result.barcode13, id),
                encryptedResultHtml = cipher.encrypt(html, id),
                balance = result.balance,
                expiryAt = result.expiryAt ?: current.expiryAt,
                balanceCheckedAt = result.balanceCheckedAt,
                lastUpdatedAt = System.currentTimeMillis(),
                lastError = null,
            ),
        )
    }

    override suspend fun markError(id: String, message: String) = dao.updateError(id, message)

    override suspend fun updateMemo(id: String, memo: String) = dao.updateMemo(id, MemoNormalizer.normalize(memo))

    override suspend fun getResultHtml(id: String): String? = withContext(Dispatchers.Default) {
        val entity = dao.getById(id) ?: return@withContext null
        entity.encryptedResultHtml?.let { decryptOrNull(it, id) }
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override fun observeUnreadableCount(): Flow<Int> =
        dao.observeAll().map { list -> list.count { !it.isReadable() } }.flowOn(Dispatchers.Default)

    override suspend fun deleteUnreadable() = withContext(Dispatchers.Default) {
        dao.getAll().filterNot { it.isReadable() }.forEach { dao.delete(it.id) }
    }

    override suspend fun exportBackup(): List<VoucherBackup> = withContext(Dispatchers.Default) {
        dao.getAll().mapNotNull { entity ->
            val code9 = decryptOrNull(entity.encryptedCode9, entity.id) ?: return@mapNotNull null
            VoucherBackup(
                code9 = code9,
                barcode13 = entity.encryptedBarcode13?.let { decryptOrNull(it, entity.id) },
                resultHtml = entity.encryptedResultHtml?.let { decryptOrNull(it, entity.id) },
                balance = entity.balance,
                expiryAt = entity.expiryAt,
                balanceCheckedAt = entity.balanceCheckedAt,
                lastUpdatedAt = entity.lastUpdatedAt,
                memo = entity.memo,
                createdAt = entity.createdAt,
            )
        }
    }

    override suspend fun importBackup(entries: List<VoucherBackup>): ImportSummary = withContext(Dispatchers.Default) {
        val known = getVouchers().mapTo(mutableSetOf()) { it.code9 }
        var duplicate = 0
        var invalid = 0
        val added = entries.mapNotNull { entry ->
            when {
                !CodeNormalizer.isValid(entry.code9) -> {
                    invalid++
                    null
                }
                !known.add(entry.code9) -> {
                    duplicate++
                    null
                }
                else -> entry.toEntity(UUID.randomUUID().toString())
            }
        }
        // 1トランザクションで入れる（途中で失敗しても一部だけ入った状態にしない）
        dao.insertAll(added)
        ImportSummary(added = added.size, skippedDuplicate = duplicate, skippedInvalid = invalid)
    }

    /** 移行先の ID で暗号化し直す（associated data が ID なので、元の暗号文は使えない）。 */
    private fun VoucherBackup.toEntity(id: String) = VoucherEntity(
        id = id,
        encryptedCode9 = cipher.encrypt(code9, id),
        encryptedBarcode13 = barcode13?.takeIf(Ean13::isValid)?.let { cipher.encrypt(it, id) },
        encryptedResultHtml = resultHtml?.let { cipher.encrypt(it, id) },
        balance = balance,
        expiryAt = expiryAt,
        balanceCheckedAt = balanceCheckedAt,
        lastUpdatedAt = lastUpdatedAt,
        memo = MemoNormalizer.normalize(memo),
        createdAt = createdAt,
    )

    private fun VoucherEntity.isReadable(): Boolean = decryptOrNull(encryptedCode9, id) != null

    private fun VoucherEntity.toDomain(): Voucher? {
        val code9 = decryptOrNull(encryptedCode9, id) ?: return null
        return Voucher(
            id = id,
            code9 = code9,
            barcode13 = encryptedBarcode13?.let { decryptOrNull(it, id) },
            balance = balance,
            expiryAt = expiryAt,
            balanceCheckedAt = balanceCheckedAt,
            lastUpdatedAt = lastUpdatedAt,
            memo = memo,
            createdAt = createdAt,
            hasResultHtml = encryptedResultHtml != null,
            lastError = lastError,
        )
    }

    private fun decryptOrNull(cipherText: String, id: String): String? = try {
        cipher.decrypt(cipherText, id)
    } catch (e: GeneralSecurityException) {
        // 平文や暗号文はログに出さない
        Log.w(TAG, "decrypt failed: ${e.javaClass.simpleName}")
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "decode failed: ${e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val TAG = "VoucherRepository"
    }
}
