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

class SkylarkScraperTest {

    private lateinit var server: MockWebServer
    private lateinit var scraper: SkylarkScraper

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scraper = SkylarkScraper(OkHttpClient(), SkylarkEndpoint(server.url("/")))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun html(body: String, vararg headers: Pair<String, String>) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "text/html; charset=UTF-8")
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .body(body)
        .build()

    private fun png(bytes: ByteArray) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "image/png")
        .body(Buffer().write(bytes))
        .build()

    @Test
    fun `uses same cookie for page, captcha and submit`() = runTest {
        server.enqueue(html(Fixtures.read("input.html"), "Set-Cookie" to "ci_session=abc; path=/"))
        server.enqueue(png(byteArrayOf(1, 2, 3)))
        server.enqueue(html(Fixtures.read("result_sample.html")))

        val session = scraper.newSession()
        scraper.open(session)
        val captcha = scraper.fetchCaptcha(session)
        val result = scraper.submit(session, "ABC123xyz", "5640", termsAgreed = true)

        assertEquals(listOf<Byte>(1, 2, 3), captcha.toList())
        assertTrue(result.contains("6674577439187"))

        val get = server.takeRequest()
        assertEquals("/input", get.url.encodedPath)
        assertNull(get.headers["Cookie"])

        val image = server.takeRequest()
        assertEquals("/input/captcha", image.url.encodedPath)
        assertEquals("ci_session=abc", image.headers["Cookie"])

        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/input", post.url.encodedPath)
        assertEquals("ci_session=abc", post.headers["Cookie"])
        assertEquals(
            "code%5B0%5D=ABC&code%5B1%5D=123&code%5B2%5D=xyz&captchakey=5640&agree=1",
            post.body?.utf8(),
        )
    }

    @Test
    fun `omits agree flag when terms not agreed`() = runTest {
        server.enqueue(html(Fixtures.read("input.html")))
        server.enqueue(html(Fixtures.read("error_captcha.html")))

        val session = scraper.newSession()
        scraper.open(session)
        scraper.submit(session, "ABC123XYZ", "1234", termsAgreed = false)

        server.takeRequest()
        assertEquals("code%5B0%5D=ABC&code%5B1%5D=123&code%5B2%5D=XYZ&captchakey=1234", server.takeRequest().body?.utf8())
    }

    @Test
    fun `does not carry cookies to next session`() = runTest {
        server.enqueue(html(Fixtures.read("input.html"), "Set-Cookie" to "ci_session=first; path=/"))
        server.enqueue(html(Fixtures.read("input.html"), "Set-Cookie" to "ci_session=second; path=/"))

        scraper.newSession().use { scraper.open(it) }
        scraper.newSession().use { scraper.open(it) }

        server.takeRequest()
        assertNull(server.takeRequest().headers["Cookie"])
    }

    @Test
    fun `inlines barcode image as data uri`() = runTest {
        server.enqueue(html(Fixtures.read("input.html")))
        server.enqueue(png(byteArrayOf(9, 9)))

        val session = scraper.newSession()
        val form = scraper.open(session)
        val inlined = scraper.inlineBarcodeImages(session, Fixtures.read("result_sample.html"), form.actionUrl)

        assertTrue(inlined.contains("src=\"data:image/png;base64,CQk=\""))
        server.takeRequest()
        assertEquals("/input/barcode", server.takeRequest().url.encodedPath)
    }

    @Test(expected = SiteMaintenanceException::class)
    fun `throws on maintenance page`() = runTest {
        server.enqueue(html(Fixtures.read("maintenance.html")))
        scraper.open(scraper.newSession())
    }

    @Test(expected = SiteMaintenanceException::class)
    fun `throws on actual maintenance page`() = runTest {
        server.enqueue(html(Fixtures.read("maintenance_actual.html")))
        scraper.open(scraper.newSession())
    }
}
