package com.example.skylarkwallet.data.remote

import com.example.skylarkwallet.testutil.Fixtures
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputFormTest {

    private val pageUrl = "https://www.skylarkyutaiken.com/input".toHttpUrl()

    @Test
    fun `parses form from real input page`() {
        val form = InputForm.parse(Fixtures.read("input.html"), pageUrl)!!

        assertEquals("https://www.skylarkyutaiken.com/input", form.actionUrl.toString())
        assertEquals(listOf("code[0]", "code[1]", "code[2]"), form.codeFieldNames)
        assertEquals("captchakey", form.captchaFieldName)
        assertEquals("agree", form.agreeFieldName)
        assertEquals("1", form.agreeValue)
        assertTrue(form.hiddenFields.isEmpty())
        assertEquals("/input/captcha", form.captchaUrl.encodedPath)
    }

    @Test
    fun `includes hidden fields`() {
        val html = Fixtures.read("input.html")
            .replace("<div class=\"rwb\">", "<input type=\"hidden\" name=\"token\" value=\"abc\"><div class=\"rwb\">")

        val form = InputForm.parse(html, pageUrl)!!

        assertEquals(listOf("token" to "abc"), form.hiddenFields)
    }

    @Test
    fun `returns null without form`() {
        assertNull(InputForm.parse(Fixtures.read("result_sample.html"), pageUrl))
    }
}
