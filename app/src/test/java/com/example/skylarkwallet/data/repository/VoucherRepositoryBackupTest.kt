package com.example.skylarkwallet.data.repository

import com.example.skylarkwallet.data.local.crypto.AeadStringCipher
import com.example.skylarkwallet.data.local.db.VoucherDao
import com.example.skylarkwallet.data.local.db.VoucherEntity
import com.example.skylarkwallet.domain.model.ImportSummary
import com.example.skylarkwallet.domain.model.VoucherBackup
import com.example.skylarkwallet.domain.model.VoucherResult
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoucherRepositoryBackupTest {

    private class FakeDao : VoucherDao {
        val rows = MutableStateFlow<List<VoucherEntity>>(emptyList())

        override fun observeAll(): Flow<List<VoucherEntity>> = rows
        override suspend fun getAll() = rows.value.sortedBy { it.createdAt }
        override suspend fun getById(id: String) = rows.value.firstOrNull { it.id == id }
        override suspend fun insert(entity: VoucherEntity) = insertAll(listOf(entity))
        override suspend fun insertAll(entities: List<VoucherEntity>) {
            rows.value += entities
        }
        override suspend fun update(entity: VoucherEntity) {
            rows.value = rows.value.map { if (it.id == entity.id) entity else it }
        }
        override suspend fun updateMemo(id: String, memo: String) {
            rows.value = rows.value.map { if (it.id == id) it.copy(memo = memo) else it }
        }
        override suspend fun updateError(id: String, error: String?) {
            rows.value = rows.value.map { if (it.id == id) it.copy(lastError = error) else it }
        }
        override suspend fun delete(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    /** 端末ごとに別の鍵になることを再現するため、呼ぶたびに新しい鍵を作る。 */
    private fun newCipher() = AeadStringCipher {
        AeadConfig.register()
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun backup(code9: String, memo: String = "", barcode13: String? = null) = VoucherBackup(
        code9 = code9,
        barcode13 = barcode13,
        resultHtml = null,
        balance = null,
        expiryAt = null,
        balanceCheckedAt = null,
        lastUpdatedAt = null,
        memo = memo,
        createdAt = 1,
    )

    @Test
    fun `moves vouchers to another device key`() = runTest {
        val oldDao = FakeDao()
        val old = VoucherRepositoryImpl(oldDao, newCipher())
        val first = old.add("ABC123XYZ", "家族の分")
        old.saveResult(
            first.id,
            VoucherResult(
                barcode13 = "6674577439187",
                balance = 1500,
                expiryAt = 1_800_000_000_000,
                balanceCheckedAt = 1_700_000_000_000,
            ),
            "<p>残高</p>",
        )
        old.add("000000001", "")

        val newDao = FakeDao()
        val new = VoucherRepositoryImpl(newDao, newCipher())
        val summary = new.importBackup(old.exportBackup())

        assertEquals(ImportSummary(added = 2, skippedDuplicate = 0, skippedInvalid = 0), summary)
        val moved = new.getVouchers().first { it.code9 == "ABC123XYZ" }
        assertNotEquals(first.id, moved.id)
        assertEquals("6674577439187", moved.barcode13)
        assertEquals(1500, moved.balance)
        assertEquals(1_800_000_000_000, moved.expiryAt)
        assertEquals("家族の分", moved.memo)
        assertEquals("<p>残高</p>", new.getResultHtml(moved.id))
    }

    @Test
    fun `skips registered and repeated codes`() = runTest {
        val repository = VoucherRepositoryImpl(FakeDao(), newCipher())
        repository.add("ABC123XYZ", "既存")

        val summary = repository.importBackup(
            listOf(backup("ABC123XYZ", "バックアップ"), backup("000000001"), backup("000000001")),
        )

        assertEquals(ImportSummary(added = 1, skippedDuplicate = 2, skippedInvalid = 0), summary)
        assertEquals("既存", repository.findByCode("ABC123XYZ")?.memo)
    }

    @Test
    fun `skips invalid codes and drops invalid barcodes`() = runTest {
        val repository = VoucherRepositoryImpl(FakeDao(), newCipher())

        val summary = repository.importBackup(
            listOf(backup("ABC-123"), backup("000000001", memo = " a\nb ", barcode13 = "1234567890123")),
        )

        assertEquals(ImportSummary(added = 1, skippedDuplicate = 0, skippedInvalid = 1), summary)
        val imported = repository.getVouchers().single()
        assertNull(imported.barcode13)
        assertEquals("a b", imported.memo)
    }

    @Test
    fun `exports nothing for unreadable vouchers`() = runTest {
        val dao = FakeDao()
        VoucherRepositoryImpl(dao, newCipher()).add("ABC123XYZ", "")

        // 鍵が変わった（端末の暗号鍵が無効になった）状態
        assertEquals(emptyList<VoucherBackup>(), VoucherRepositoryImpl(dao, newCipher()).exportBackup())
    }
}
