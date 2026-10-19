package app.skylark.wallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ean13Test {

    @Test
    fun `validates check digit`() {
        assertTrue(Ean13.isValid("6674577439187"))
        assertTrue(Ean13.isValid("4901234567894"))
        assertFalse(Ean13.isValid("6674577439180"))
        assertFalse(Ean13.isValid("667457743918"))
        assertFalse(Ean13.isValid("66745774391A7"))
    }

    @Test
    fun `computes check digit`() {
        assertEquals(7, Ean13.checkDigit("667457743918"))
    }
}
