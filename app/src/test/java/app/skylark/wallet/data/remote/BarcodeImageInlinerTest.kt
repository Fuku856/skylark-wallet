package app.skylark.wallet.data.remote

import app.skylark.wallet.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarcodeImageInlinerTest {

    private lateinit var server: MockWebServer
    private lateinit var inliner: BarcodeImageInliner

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        inliner = BarcodeImageInliner(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun png(bytes: ByteArray) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "image/png")
        .body(Buffer().write(bytes))
        .build()

    @Test
    fun `inlines barcode image as data uri with webview cookie`() = runTest {
        server.enqueue(png(byteArrayOf(9, 9)))

        val inlined = inliner.inline(Fixtures.read("result_sample.html"), server.url("/input"), "ci_session=abc; csrf_cookie_name=t")

        assertTrue(inlined.contains("src=\"data:image/png;base64,CQk=\""))
        val request = server.takeRequest()
        assertEquals("/input/barcode", request.url.encodedPath)
        assertEquals("ci_session=abc; csrf_cookie_name=t", request.headers["Cookie"])
        assertEquals(server.url("/input").toString(), request.headers["Referer"])
    }

    @Test
    fun `sends no cookie header when webview has none`() = runTest {
        server.enqueue(png(byteArrayOf(1)))

        inliner.inline(Fixtures.read("result_sample.html"), server.url("/input"), null)

        assertNull(server.takeRequest().headers["Cookie"])
    }

    @Test
    fun `keeps original src when image cannot be fetched`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        val inlined = inliner.inline(Fixtures.read("result_sample.html"), server.url("/input"), null)

        assertTrue(inlined.contains("src=\"/input/barcode?6674577439187\""))
    }

    @Test
    fun `does not send cookie to other hosts`() = runTest {
        val html = """<div class="bcode"><img src="https://example.invalid/barcode.png"></div>"""

        val inlined = inliner.inline(html, server.url("/input"), "ci_session=abc")

        assertTrue(inlined.contains("https://example.invalid/barcode.png"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `returns html unchanged without barcode image`() = runTest {
        val html = Fixtures.read("input.html")
        assertEquals(html, inliner.inline(html, server.url("/input"), null))
    }
}
