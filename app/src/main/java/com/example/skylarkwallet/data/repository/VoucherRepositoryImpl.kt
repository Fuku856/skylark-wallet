package com.example.skylarkwallet.data.repository

import android.util.Log
import com.example.skylarkwallet.data.local.crypto.StringCipher
import com.example.skylarkwallet.data.local.db.VoucherDao
import com.example.skylarkwallet.data.local.db.VoucherEntity
import com.example.skylarkwallet.domain.model.Voucher
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

    override suspend fun updateMemo(id: String, memo: String) = dao.updateMemo(id, memo.trim())

    override suspend fun getResultHtml(id: String): String? = withContext(Dispatchers.Default) {
        val entity = dao.getById(id) ?: return@withContext null
        entity.encryptedResultHtml?.let { decryptOrNull(it, id) }
    }

    override suspend fun delete(id: String) = dao.delete(id)

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
