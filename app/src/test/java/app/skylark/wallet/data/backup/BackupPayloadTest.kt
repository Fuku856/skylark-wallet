package app.skylark.wallet.data.backup

import app.skylark.wallet.domain.model.VoucherBackup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class BackupPayloadTest {

    private val full = VoucherBackup(
        code9 = "ABC123XYZ",
        barcode13 = "6674577439187",
        resultHtml = "<p>残高</p>",
        balance = 1500,
        expiryAt = 1_800_000_000_000,
        balanceCheckedAt = 1_700_000_000_000,
        lastUpdatedAt = 1_700_000_000_001,
        memo = "家族の分",
        createdAt = 1_690_000_000_000,
    )
    private val minimal = VoucherBackup(
        code9 = "000000001",
        barcode13 = null,
        resultHtml = null,
        balance = null,
        expiryAt = null,
        balanceCheckedAt = null,
        lastUpdatedAt = null,
        memo = "",
        createdAt = 1,
    )

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    @Test
    fun `round trips`() {
        assertEquals(listOf(full, minimal), BackupPayload.decode(BackupPayload.encode(listOf(full, minimal))))
    }

    @Test
    fun `ignores unknown keys`() {
        val json = """{"version":1,"exportedAt":0,"extra":true,"vouchers":[{"code9":"000000001","createdAt":1,"future":"x"}]}"""

        assertEquals(listOf(minimal), BackupPayload.decode(gzip(json)))
    }

    @Test(expected = BackupException.UnsupportedVersion::class)
    fun `rejects newer payload version`() {
        BackupPayload.decode(gzip("""{"version":2,"exportedAt":0,"vouchers":[]}"""))
    }

    @Test(expected = BackupException.WrongPasswordOrCorrupted::class)
    fun `rejects broken json`() {
        BackupPayload.decode(gzip("""{"version":1,"""))
    }

    @Test(expected = BackupException.WrongPasswordOrCorrupted::class)
    fun `rejects non gzip data`() {
        BackupPayload.decode("plain text".toByteArray())
    }

    @Test(expected = BackupException.WrongPasswordOrCorrupted::class)
    fun `rejects payload larger than limit`() {
        BackupPayload.decode(gzip(" ".repeat(10_000)), maxPlainSize = 1_000)
    }
}
