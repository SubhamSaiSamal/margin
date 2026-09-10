package dev.subham.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spoken lines are judged by the same engine as written ones, so what matters
 * is that a sentence becomes the right equation. No microphone required.
 */
class SpokenMathsTest {

    @Test
    fun `a plain equation is heard`() {
        assertEquals("2x+5=3x-4", fromSpeech("two x plus five equals three x minus four"))
    }

    @Test
    fun `digits from the recogniser work as well as words`() {
        assertEquals("2x+5=3x-4", fromSpeech("2 x plus 5 equals 3 x minus 4"))
    }

    @Test
    fun `is equal to is not read as three words`() {
        assertEquals("x=9", fromSpeech("x is equal to nine"))
    }

    @Test
    fun `letters heard as the words that sound like them`() {
        // Recognisers routinely return "ex" for x and "why" for y.
        assertEquals("3x=y", fromSpeech("three ex equals why"))
    }

    @Test
    fun `compound numbers are one number`() {
        assertEquals("x=25", fromSpeech("x equals twenty five"))
        assertEquals("x=100", fromSpeech("x equals one hundred"))
    }

    @Test
    fun `negative is a minus`() {
        assertEquals("x=-9", fromSpeech("x equals negative nine"))
    }

    @Test
    fun `division is heard both ways`() {
        assertEquals("x/2=4", fromSpeech("x divided by two equals four"))
        assertEquals("x/2=4", fromSpeech("x over two equals four"))
    }

    @Test
    fun `brackets are heard`() {
        assertEquals("2(x+3)=10", fromSpeech("two open bracket x plus three close bracket equals ten"))
    }

    @Test
    fun `a sentence that is not an equation is refused`() {
        assertNull(fromSpeech("what is the weather like today"))
        assertNull(fromSpeech(""))
    }

    @Test
    fun `a line without an equals sign is refused`() {
        // The engine judges equations; half a thought is not one.
        assertNull(fromSpeech("two x plus five"))
    }

    // ---------- the point of all this ----------

    @Test
    fun `spoken working is judged exactly like written working`() {
        val premise = fromSpeech("two x plus five equals three x minus four")!!
        val good = fromSpeech("two x minus three x equals minus four minus five")!!
        val bad = fromSpeech("minus x equals minus four plus five")!!

        assertTrue(equivalent(premise, good))
        assertTrue(!equivalent(good, bad))
        assertEquals(Finding.Sign(5.0), diagnose(good, bad))
    }
}
