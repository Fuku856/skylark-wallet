package app.skylark.wallet.ui.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupScriptTest {

    @Test
    fun `fill splits code into three quoted parts`() {
        val script = LookupScript.fill("ABC123xyz", agree = true)
        assertTrue(script.contains("var parts = [\"ABC\",\"123\",\"xyz\"];"))
        assertTrue(script.contains("f.elements['code[' + i + ']']"))
    }

    @Test
    fun `fill checks agree only when agreed`() {
        assertTrue(LookupScript.fill("ABC123XYZ", agree = true).contains("if (agree && true)"))
        assertTrue(LookupScript.fill("ABC123XYZ", agree = false).contains("if (agree && false)"))
    }

    @Test
    fun `fill scrolls recaptcha widget into view`() {
        assertTrue(LookupScript.fill("ABC123XYZ", agree = true).contains("getElementById('charge-recaptcha')"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fill rejects code that is not nine alphanumerics`() {
        LookupScript.fill("ABC'123XY", agree = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fill rejects short code`() {
        LookupScript.fill("12345678", agree = true)
    }

    @Test
    fun `submit clicks site button instead of form submit`() {
        assertTrue(LookupScript.SUBMIT.contains(".click()"))
        assertFalse(LookupScript.SUBMIT.contains("f.submit()"))
        assertTrue(LookupScript.SUBMIT.contains("getElementById('g-recaptcha-response-input')"))
    }

    @Test
    fun `token check returns only a boolean`() {
        assertTrue(LookupScript.TOKEN_READY.contains("return !!(t && t.value);"))
    }

    @Test
    fun `decodes evaluateJavascript string results`() {
        assertEquals("ok", LookupScript.decodeString("\"ok\""))
        assertEquals("<html>\n\"x\"</html>", LookupScript.decodeString("\"<html>\n\\\"x\\\"</html>\""))
        assertEquals("\u3042", LookupScript.decodeString("\"\u3042\""))
    }

    @Test
    fun `decode returns null for non string results`() {
        assertNull(LookupScript.decodeString(null))
        assertNull(LookupScript.decodeString(""))
        assertNull(LookupScript.decodeString("null"))
        assertNull(LookupScript.decodeString("true"))
        assertNull(LookupScript.decodeString("{\"a\":1}"))
        assertNull(LookupScript.decodeString("not json"))
    }
}
