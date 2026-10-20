package app.skylark.wallet.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.ByteBuffer

/** テストを速くするため、反復回数は許可する下限で暗号化する。 */
class BackupFormatTest {

    private val plain = "ABC123XYZ 6674577439187 残高".toByteArray()
    private val password = "ABCD0123EFGH4567JKMN"

    private fun seal(data: ByteArray = plain) = BackupFormat.seal(data, password, BackupFormat.MIN_ITERATIONS)

    @Test
    fun `round trips`() {
        assertArrayEquals(plain, BackupFormat.open(seal(), password))
    }

    @Test
    fun `does not contain plaintext`() {
        val file = String(seal(), Charsets.ISO_8859_1)

        assertFalse(file.contains("ABC123XYZ"))
        assertFalse(file.contains("6674577439187"))
    }

    @Test
    fun `same input gives different output`() {
        assertNotEquals(seal().toList(), seal().toList())
    }

    @Test
    fun `round trips empty payload`() {
        assertArrayEquals(ByteArray(0), BackupFormat.open(seal(ByteArray(0)), password))
    }

    @Test(expected = BackupException.WrongPasswordOrCorrupted::class)
    fun `fails with wrong password`() {
        BackupFormat.open(seal(), "ABCD0123EFGH4567JKMP")
    }

    @Test(expected = BackupException.WrongPasswordOrCorrupted::class)
    fun `detects tampered salt`() {
        val file = seal()
        file[12] = (file[12].toInt() xor 1).toByte()
        BackupFormat.open(file, password)
    }

    @Test(expected = BackupException.WrongPasswordOrCorrupted::class)
    fun `detects tampered ciphertext`() {
        val file = seal()
        file[file.size - 20] = (file[file.size - 20].toInt() xor 1).toByte()
        BackupFormat.open(file, password)
    }

    @Test(expected = BackupException.WrongPasswordOrCorrupted::class)
    fun `detects truncated file`() {
        val file = seal()
        BackupFormat.open(file.copyOf(file.size - 1), password)
    }

    @Test(expected = BackupException.NotBackupFile::class)
    fun `rejects other files`() {
        BackupFormat.open("PK\u0003\u0004 not a backup file at all, just some bytes".toByteArray(), password)
    }

    @Test(expected = BackupException.NotBackupFile::class)
    fun `rejects too short file`() {
        BackupFormat.open("SKWB".toByteArray(), password)
    }

    @Test(expected = BackupException.UnsupportedVersion::class)
    fun `rejects newer version`() {
        val file = seal()
        file[4] = 2
        BackupFormat.open(file, password)
    }

    @Test(expected = BackupException.NotBackupFile::class)
    fun `rejects excessive iterations`() {
        val file = seal()
        ByteBuffer.wrap(file).putInt(6, BackupFormat.MAX_ITERATIONS + 1)
        BackupFormat.open(file, password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `refuses to seal with weak iterations`() {
        BackupFormat.seal(plain, password, BackupFormat.MIN_ITERATIONS - 1)
    }
}
