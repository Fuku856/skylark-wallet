package app.skylark.wallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoucherSectionsTest {

    private val now = 1_800_000_000_000L
    private val day = Voucher.DAY_MILLIS

    private fun voucher(id: String, balance: Int?, expiryAt: Long?, createdAt: Long, barcode: Boolean = true) = Voucher(
        id = id, code9 = "ABC123XYZ", barcode13 = if (barcode) "4900000000000" else null, balance = balance,
        expiryAt = expiryAt, balanceCheckedAt = null, lastUpdatedAt = now, memo = "", createdAt = createdAt,
        hasResultHtml = false, lastError = null,
    )

    private val soon = voucher("soon", 1000, now + 3 * day, createdAt = 3)
    private val later = voucher("later", 500, now + 30 * day, createdAt = 1)
    private val expired = voucher("expired", 500, now - day, createdAt = 2)
    private val usedOld = voucher("usedOld", 0, now + 1 * day, createdAt = 4)
    private val usedNew = voucher("usedNew", 0, now + 50 * day, createdAt = 5)
    private val usedNoBarcode = voucher("usedNoBarcode", 0, now + 2 * day, createdAt = 6, barcode = false)

    private val sections = VoucherSections.of(listOf(usedNew, later, usedNoBarcode, expired, soon, usedOld))

    @Test
    fun `active is ordered by expiry and used by newest registration first`() {
        assertEquals(listOf("expired", "soon", "later"), sections.active.map { it.id })
        assertEquals(listOf("usedNoBarcode", "usedNew", "usedOld"), sections.used.map { it.id })
    }

    @Test
    fun `barcode button shows usable vouchers only`() {
        assertEquals(listOf("soon", "later"), sections.barcodeList(null, now).map { it.id })
    }

    @Test
    fun `barcode from used voucher shows used vouchers only`() {
        assertEquals(listOf("usedNew", "usedOld"), sections.barcodeList("usedNew", now).map { it.id })
    }

    @Test
    fun `barcode from active voucher shows active vouchers in home order`() {
        assertEquals(listOf("expired", "soon", "later"), sections.barcodeList("expired", now).map { it.id })
    }

    @Test
    fun `barcode button shows nothing when only expired vouchers remain`() {
        val onlyExpired = VoucherSections.of(listOf(expired, usedOld))
        assertEquals(emptyList<String>(), onlyExpired.barcodeList(null, now).map { it.id })
    }
}
