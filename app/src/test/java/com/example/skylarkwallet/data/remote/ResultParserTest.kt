package com.example.skylarkwallet.data.remote

import com.example.skylarkwallet.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class ResultParserTest {

    private fun jst(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0, nanos: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, s, nanos, ResultParser.TOKYO).toInstant().toEpochMilli()

    @Test
    fun `parses balance, barcode, expiry and checked time from result page`() {
        val outcome = ResultParser.parse(Fixtures.read("result_sample.html"))

        assertTrue(outcome is ResultParser.Outcome.Success)
        val result = (outcome as ResultParser.Outcome.Success).result
        assertEquals(0, result.balance)
        assertEquals("6674577439187", result.barcode13)
        assertEquals(jst(2027, 3, 10, 23, 59, 59, 999_000_000), result.expiryAt)
        assertEquals(jst(2026, 10, 19, 14, 29), result.balanceCheckedAt)
    }

    @Test
    fun `falls back to text regex when selectors do not match`() {
        val outcome = ResultParser.parse(Fixtures.read("result_fallback.html"))

        val result = (outcome as ResultParser.Outcome.Success).result
        assertEquals(1500, result.balance)
        assertEquals("4901234567894", result.barcode13)
        assertEquals(jst(2027, 3, 10, 23, 59, 59, 999_000_000), result.expiryAt)
        assertEquals(jst(2026, 10, 19, 11, 5), result.balanceCheckedAt)
    }

    @Test
    fun `rejects barcode with wrong check digit`() {
        val html = Fixtures.read("result_sample.html").replace("6674577439187", "6674577439188")

        assertEquals(ResultParser.Outcome.Unknown, ResultParser.parse(html))
    }

    @Test
    fun `treats redisplayed form without error as captcha mismatch`() {
        assertEquals(ResultParser.Outcome.CaptchaMismatch(null), ResultParser.parse(Fixtures.read("input.html")))
    }

    @Test
    fun `captcha error message is captcha mismatch`() {
        val outcome = ResultParser.parse(Fixtures.read("error_captcha.html"))

        assertEquals(ResultParser.Outcome.CaptchaMismatch("画像の番号が正しくありません。"), outcome)
    }

    @Test
    fun `code error message is invalid code`() {
        val outcome = ResultParser.parse(Fixtures.read("error_code.html"))

        assertEquals(ResultParser.Outcome.InvalidCode("ご優待番号が正しくありません。"), outcome)
    }

    @Test
    fun `code error wins when shown together with captcha error`() {
        val html = Fixtures.read("error_code.html").replace(
            "<p class=\"err\">ご優待番号が正しくありません。</p>",
            "<p class=\"err\">ご優待番号が正しくありません。</p><p class=\"err\">画像の番号が正しくありません。</p>",
        )

        assertEquals(ResultParser.Outcome.InvalidCode("ご優待番号が正しくありません。"), ResultParser.parse(html))
    }

    @Test
    fun `unrecognized error message is captcha mismatch`() {
        val html = Fixtures.read("error_code.html").replace("ご優待番号が正しくありません。", "入力された番号が違います。")

        assertEquals(ResultParser.Outcome.CaptchaMismatch("入力された番号が違います。"), ResultParser.parse(html))
    }

    @Test
    fun `detects maintenance page`() {
        assertEquals(ResultParser.Outcome.Maintenance, ResultParser.parse(Fixtures.read("maintenance.html")))
    }

    @Test
    fun `unexpected page is unknown`() {
        assertEquals(ResultParser.Outcome.Unknown, ResultParser.parse("<html><body><p>Not Found</p></body></html>"))
    }

    @Test
    fun `expiry tolerates spacing and full-width digits`() {
        val expected = jst(2027, 3, 10, 23, 59, 59, 999_000_000)
        assertEquals(expected, ResultParser.parseExpiry("2027 年 03月 10日 23時59分迄"))
        assertEquals(expected, ResultParser.parseExpiry("2027年3月10日23時59分迄"))
        assertEquals(expected, ResultParser.parseExpiry("２０２７年 ０３月 １０日 ２３時５９分迄"))
        assertNull(ResultParser.parseExpiry("2027年3月10日"))
    }

    @Test
    fun `checked time handles am pm and twelve o clock`() {
        assertEquals(jst(2026, 10, 19, 14, 29), ResultParser.parseCheckedAt("2026年 10月 19日 午後2:29時点の残高"))
        assertEquals(jst(2026, 10, 19, 0, 5), ResultParser.parseCheckedAt("2026年 10月 19日 午前12:05時点の残高"))
        assertEquals(jst(2026, 10, 19, 12, 5), ResultParser.parseCheckedAt("2026年 10月 19日 午後12:05時点の残高"))
        assertEquals(jst(2026, 10, 19, 9, 0), ResultParser.parseCheckedAt("2026年10月19日 9：00 時点の残高"))
    }
}
