package dev.subham.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The recogniser's output is where tuning against real handwriting happens, so
 * it is worth pinning down. Several of these candidate lists are verbatim from
 * the device.
 */
class ReadingTest {

    // ---------- tidying ----------

    @Test
    fun `spaces are dropped`() {
        assertEquals("2x+5=3x-4", tidyForAlgebra("2x + 5 = 3x - 4"))
    }

    @Test
    fun `unicode minus becomes a hyphen`() {
        assertEquals("x=-4", tidyForAlgebra("x = −4"))
    }

    @Test
    fun `a comma inside a number is a decimal point`() {
        assertEquals("x=3.5", tidyForAlgebra("x = 3,5"))
    }

    @Test
    fun `a mid dot is a decimal point`() {
        assertEquals("x=0.5", tidyForAlgebra("x = 0·5"))
    }

    @Test
    fun `multiplication signs become asterisks`() {
        assertEquals("2*3=6", tidyForAlgebra("2 × 3 = 6"))
    }

    // ---------- confusions ----------

    @Test
    fun `a lone vertical mark is a one`() {
        assertEquals("x=1", unconfuse("x=l"))
    }

    @Test
    fun `a ring is a zero`() {
        assertEquals("3x=0", unconfuse("3x=O"))
    }

    @Test
    fun `a cross is the unknown, not a multiplication sign`() {
        assertEquals("3x=9", unconfuse("3X=9"))
    }

    // ---------- choosing between candidates ----------

    @Test
    fun `a parseable candidate beats a higher ranked unparseable one`() {
        // The model ranks by how it looks as text, not as algebra.
        val candidates = listOf("ll", "Il", "x=1", "I")
        assertEquals("x=1", pickEquation(candidates))
    }

    @Test
    fun `confusions are resolved when nothing parses outright`() {
        assertEquals("x=1", pickEquation(listOf("x=l")))
    }

    @Test
    fun `the best guess survives when nothing can be parsed`() {
        // Verbatim from the device: two vertical strokes drawn through adb.
        val candidates = listOf("ll", "II", "l", "Il", "I", "il")
        assertEquals("11", pickEquation(candidates))
    }

    @Test
    fun `nothing in means nothing out`() {
        assertNull(pickEquation(emptyList()))
        assertNull(pickEquation(listOf("", "   ")))
    }

    @Test
    fun `a real equation is left alone`() {
        assertEquals("2x-3x=-4-5", pickEquation(listOf("2x - 3x = -4 - 5")))
    }

    @Test
    fun `a misread digit does not become a phantom unknown`() {
        // "3x=l" parses happily as an equation in x and l, which would give a
        // confident and wrong verdict. Nobody calls an unknown l.
        assertEquals("3x=1", pickEquation(listOf("3x=l")))
        assertEquals("x=0", pickEquation(listOf("x=O")))
    }

    @Test
    fun `genuine second unknowns are kept`() {
        assertEquals("3x=y", pickEquation(listOf("3x = y")))
        assertEquals("2a+b=0", pickEquation(listOf("2a + b = 0")))
    }
}
