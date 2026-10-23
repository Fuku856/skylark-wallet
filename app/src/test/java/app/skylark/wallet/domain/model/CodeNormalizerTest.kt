package app.skylark.wallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeNormalizerTest {

    @Test
    fun `removes separators and converts full-width`() {
        assertEquals("ABC123xyz", CodeNormalizer.normalize("ABC-123-xyz"))
        assertEquals("ABC123XYZ", CodeNormalizer.normalize(" ABC 123　XYZ\n"))
        assertEquals("ABC123XYZ", CodeNormalizer.normalize("ＡＢＣ－１２３－ＸＹＺ"))
        assertEquals("ABC123XYZ", CodeNormalizer.normalize("ABC‐123ーXYZ"))
    }

    @Test
    fun `accepts only nine ascii alphanumerics`() {
        assertTrue(CodeNormalizer.isValid("ABC123xyz"))
        assertFalse(CodeNormalizer.isValid("ABC123XY"))
        assertFalse(CodeNormalizer.isValid("ABC123XYZ0"))
        assertFalse(CodeNormalizer.isValid("ABC-23XYZ"))
    }

    @Test
    fun `splits into three parts`() {
        assertEquals(listOf("ABC", "123", "XYZ"), CodeNormalizer.split("ABC123XYZ"))
    }
}
