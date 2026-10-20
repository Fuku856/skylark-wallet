package app.skylark.wallet.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `formats code in groups of three`() {
        assertEquals("ABC-123-xyz", Format.code("ABC123xyz"))
    }

    @Test
    fun `leaves malformed code as is`() {
        assertEquals("ABC123XY", Format.code("ABC123XY"))
    }
}
