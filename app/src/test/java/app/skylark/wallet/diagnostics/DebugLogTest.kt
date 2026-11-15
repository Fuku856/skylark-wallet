package app.skylark.wallet.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class DebugLogTest {

    private val lines = mutableListOf<Pair<Int, String>>()
    private val gate = DebugLogGate(capacity = 2) { priority, line -> lines += priority to line }

    @Test
    fun `gate writes lines kept before setting is known when enabled`() {
        gate.emit(1) { "early" }

        gate.resolve(true)
        gate.emit(2) { "late" }

        assertEquals(listOf(1 to "early", 2 to "late"), lines)
    }

    @Test
    fun `gate drops kept lines when disabled`() {
        gate.emit(1) { "early" }

        gate.resolve(false)
        gate.emit(2) { "late" }
        gate.resolve(true)

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `gate keeps only newest lines up to capacity`() {
        gate.emit(1) { "first" }
        gate.emit(2) { "second" }
        gate.emit(3) { "third" }

        gate.resolve(true)

        assertEquals(listOf(2 to "second", 3 to "third"), lines)
    }

    @Test
    fun `cause chain has class names without messages`() {
        val cause = IllegalStateException("JSON input: {\"code9\":\"ABC123XYZ\"}")
        val error = RuntimeException("wrapped", cause)

        val chain = DebugLog.causeChain(error)

        assertEquals("java.lang.RuntimeException <- java.lang.IllegalStateException", chain)
        assertFalse(chain.contains("ABC123XYZ"))
    }

    @Test
    fun `stack trace text keeps unknown host exception`() {
        val text = RuntimeException("wrapped", UnknownHostException("example.test")).stackTraceToString()

        assertTrue(text.contains("java.net.UnknownHostException: example.test"))
    }
}
