package app.skylark.wallet.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPasswordTest {

    @Test
    fun `generates five groups of four`() {
        val password = BackupPassword.generate()

        assertTrue(password, Regex("^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){4}$").matches(password))
        assertTrue(BackupPassword.isWellFormed(BackupPassword.normalize(password)))
    }

    @Test
    fun `generates different passwords`() {
        assertNotEquals(BackupPassword.generate(), BackupPassword.generate())
    }

    @Test
    fun `normalizes generated password to itself without separators`() {
        assertEquals("ABCD0123EFGH4567JKMN", BackupPassword.normalize("ABCD-0123-EFGH-4567-JKMN"))
    }

    @Test
    fun `accepts lowercase fullwidth and spaces`() {
        assertEquals("ABCD0123EFGH4567JKMN", BackupPassword.normalize("abcd 0123 ｅｆｇｈ－４５６７ jkmn"))
    }

    @Test
    fun `reads ambiguous letters as digits`() {
        assertEquals("0111", BackupPassword.normalize("OIlL"))
    }

    @Test
    fun `rejects wrong length`() {
        assertFalse(BackupPassword.isWellFormed(BackupPassword.normalize("ABCD-0123-EFGH-4567")))
    }

    @Test
    fun `rejects letters outside alphabet`() {
        assertFalse(BackupPassword.isWellFormed("ABCD0123EFGH4567JKMU"))
    }
}
