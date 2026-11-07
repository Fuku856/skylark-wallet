package app.skylark.wallet.data.captcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptchaSolverTest {

    @Test
    fun successHoldsDigitsAndRawText() {
        val success = CaptchaSolver.SolveResult.Success(digits = "1234", rawText = "I234")
        assertEquals("1234", success.digits)
        assertEquals("I234", success.rawText)
    }

    @Test
    fun successDefaultsRawTextToDigits() {
        val success = CaptchaSolver.SolveResult.Success(digits = "1234")
        assertEquals("1234", success.rawText)
    }

    @Test
    fun failureHoldsKindDetailAndRawText() {
        val failure = CaptchaSolver.SolveResult.Failure(
            kind = CaptchaSolver.FailureKind.NotDigits,
            rawText = "ABCDE",
        )
        assertEquals(CaptchaSolver.FailureKind.NotDigits, failure.kind)
        assertEquals("ABCDE", failure.rawText)
        assertNull(failure.detail)
    }
}
