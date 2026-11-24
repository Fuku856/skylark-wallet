package app.skylark.wallet.ui.lookup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupHostTest {

    @Test
    fun `allows site pages over https`() {
        assertTrue(LookupHost.isAllowed(LookupHost.INPUT_URL))
        assertTrue(LookupHost.isAllowed("https://www.skylarkyutaiken.com/input?x=1"))
        assertTrue(LookupHost.isAllowed("HTTPS://WWW.SKYLARKYUTAIKEN.COM/"))
    }

    @Test
    fun `rejects other hosts and schemes`() {
        assertFalse(LookupHost.isAllowed(null))
        assertFalse(LookupHost.isAllowed(""))
        assertFalse(LookupHost.isAllowed("http://www.skylarkyutaiken.com/input"))
        assertFalse(LookupHost.isAllowed("https://skylarkyutaiken.com/input"))
        assertFalse(LookupHost.isAllowed("https://www.skylarkyutaiken.com.example.com/input"))
        assertFalse(LookupHost.isAllowed("https://www.google.com/recaptcha/enterprise.js"))
        assertFalse(LookupHost.isAllowed("about:blank"))
        assertFalse(LookupHost.isAllowed("javascript:alert(1)"))
    }

    @Test
    fun `rejects user info and explicit ports`() {
        assertFalse(LookupHost.isAllowed("https://user@www.skylarkyutaiken.com/input"))
        assertFalse(LookupHost.isAllowed("https://www.skylarkyutaiken.com:8443/input"))
        assertFalse(LookupHost.isAllowed("https://evil.example@www.skylarkyutaiken.com/"))
    }
}
