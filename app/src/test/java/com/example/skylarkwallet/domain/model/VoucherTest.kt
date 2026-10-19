package com.example.skylarkwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherTest {

    private val now = 1_800_000_000_000L
    private val day = Voucher.DAY_MILLIS

    private fun voucher(balance: Int? = 1000, expiryAt: Long? = null, lastUpdatedAt: Long? = now, createdAt: Long = 0) =
        Voucher(
            id = "id$createdAt", code9 = "ABC123XYZ", barcode13 = null, balance = balance, expiryAt = expiryAt,
            balanceCheckedAt = null, lastUpdatedAt = lastUpdatedAt, memo = "", createdAt = createdAt,
            hasResultHtml = false, lastError = null,
        )

    @Test
    fun `used is derived from zero balance`() {
        assertTrue(voucher(balance = 0).isUsed)
        assertFalse(voucher(balance = 1).isUsed)
        assertFalse(voucher(balance = null).isUsed)
    }

    @Test
    fun `detects expiry within thirty days`() {
        assertTrue(voucher(expiryAt = now + 30 * day).isExpiringSoon(now))
        assertFalse(voucher(expiryAt = now + 31 * day).isExpiringSoon(now))
        assertFalse(voucher(expiryAt = now - 1).isExpiringSoon(now))
        assertTrue(voucher(expiryAt = now - 1).isExpired(now))
    }

    @Test
    fun `needs refresh after seven days`() {
        assertTrue(voucher(lastUpdatedAt = now - 7 * day).needsRefresh(now))
        assertFalse(voucher(lastUpdatedAt = now - 6 * day).needsRefresh(now))
        assertFalse(voucher(balance = 0, lastUpdatedAt = now - 30 * day).needsRefresh(now))
    }

    @Test
    fun `orders by expiry with unknown last`() {
        val a = voucher(expiryAt = now + 10 * day, createdAt = 1)
        val b = voucher(expiryAt = null, createdAt = 2)
        val c = voucher(expiryAt = now + 2 * day, createdAt = 3)

        assertEquals(listOf(c, a, b), listOf(a, b, c).sortedWith(Voucher.ExpiryOrder))
    }
}
