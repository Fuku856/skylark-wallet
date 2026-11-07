package app.skylark.wallet.data.remote

import app.skylark.wallet.diagnostics.DebugLog
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class LookupLogInterceptorTest {

    private lateinit var server: MockWebServer
    private val lines = mutableListOf<String>()
    private val client = OkHttpClient.Builder().addNetworkInterceptor(LookupLogInterceptor(lines::add)).build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun get(pathAndQuery: String) {
        client.newCall(Request.Builder().url(server.url(pathAndQuery)).build()).execute().close()
    }

    @Test
    fun `logs path status and cookie names without secrets`() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "image/png")
                .addHeader("Set-Cookie", "ci_session=secret-value; Max-Age=300; path=/")
                .build(),
        )

        get("/input/barcode?6674577439187")

        assertEquals(listOf("GET /input/barcode -> 200 image/png cookies=[ci_session]"), lines)
        assertFalse(lines.single().contains("6674577439187"))
        assertFalse(lines.single().contains("secret-value"))
    }

    @Test
    fun `logs each redirect without query`() {
        server.enqueue(MockResponse.Builder().code(303).addHeader("Location", "/next?6674577439187").build())
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").build())

        get("/input")

        assertEquals("GET /input -> 303 - cookies=[] location=/next", lines[0])
        assertEquals("GET /next -> 200 text/html cookies=[]", lines[1])
    }

    @Test
    fun `sanitize masks long digit runs only`() {
        assertEquals("code *** ok 12345", DebugLog.sanitize("code 6674577439187 ok 12345"))
    }

    @Test
    fun `sanitize keeps captcha and ocr text intact while masking code`() {
        val log = "code 6674577439187 submit captcha=1234 ocr='I234' attempt=1"
        assertEquals("code *** submit captcha=1234 ocr='I234' attempt=1", DebugLog.sanitize(log))
    }
}
