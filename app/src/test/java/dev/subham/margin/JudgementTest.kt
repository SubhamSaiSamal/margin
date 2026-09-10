package dev.subham.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgementTest {

    private fun verdicts(vararg lines: String) = judgeLines(lines.toList()).map { it.verdict }

    @Test
    fun `a clean solution is premise then follows`() {
        assertEquals(
            listOf(Verdict.PREMISE, Verdict.FOLLOWS, Verdict.FOLLOWS, Verdict.FOLLOWS),
            verdicts("2x + 5 = 3x - 4", "2x - 3x = -4 - 5", "-x = -9", "x = 9"),
        )
    }

    @Test
    fun `the broken line is the one marked`() {
        assertEquals(
            listOf(Verdict.PREMISE, Verdict.FOLLOWS, Verdict.BROKEN),
            verdicts("2x + 5 = 3x - 4", "2x - 3x = -4 - 5", "-x = -4 + 5"),
        )
    }

    // ---------- the interesting one ----------

    @Test
    fun `working on correctly after a slip is drift, not success`() {
        // Line 3 flips a sign. Lines 4 and 5 are then perfect algebra -- on the
        // wrong equation. Every tool I know of ticks them.
        val page = judgeLines(
            listOf(
                "2x + 5 = 3x - 4",   // premise, x = 9
                "2x - 3x = -4 - 5",  // follows
                "-x = -4 + 5",       // slip: should be -9
                "-x = 1",            // follows from the slip
                "x = -1",            // follows again
            )
        )

        assertEquals(
            listOf(
                Verdict.PREMISE,
                Verdict.FOLLOWS,
                Verdict.BROKEN,
                Verdict.DRIFTED,
                Verdict.DRIFTED,
            ),
            page.map { it.verdict },
        )
    }

    @Test
    fun `drift says so plainly`() {
        val page = judgeLines(listOf("x + 1 = 4", "x = 2", "2x = 4"))
        val drifted = page.last()

        assertEquals(Verdict.DRIFTED, drifted.verdict)
        assertTrue(wordFor(drifted)!!.contains("no longer the equation"))
    }

    @Test
    fun `a correct chain never drifts`() {
        // Every legitimate step preserves the solution set, so a clean page
        // cannot drift by definition.
        val page = judgeLines(listOf("4x = 8", "x = 2", "2x = 4", "8x = 16"))
        assertTrue(page.none { it.verdict == Verdict.DRIFTED })
    }

    // ---------- wording ----------

    @Test
    fun `a good line says nothing`() {
        val page = judgeLines(listOf("x = 9", "2x = 18"))
        assertNull(wordFor(page[0]))
        assertNull(wordFor(page[1]))
    }

    @Test
    fun `a broken line names the term`() {
        val page = judgeLines(listOf("3x - 7 = 2x + 1", "3x - 2x = 1 - 7"))
        assertEquals("check the sign on the 7", wordFor(page.last()))
    }

    // ---------- robustness ----------

    @Test
    fun `unreadable lines are dropped rather than guessed at`() {
        val page = judgeLines(listOf("2x + 5 = 3x - 4", "ll II l", "2x - 3x = -4 - 5"))
        assertEquals(2, page.size)
        assertEquals(listOf(Verdict.PREMISE, Verdict.FOLLOWS), page.map { it.verdict })
    }

    @Test
    fun `nothing readable means nothing judged`() {
        assertTrue(judgeLines(listOf("ll", "???")).isEmpty())
        assertTrue(judgeLines(emptyList()).isEmpty())
    }

    @Test
    fun `a single line is just the premise`() {
        val page = judgeLines(listOf("7x = 21"))
        assertEquals(listOf(Verdict.PREMISE), page.map { it.verdict })
        assertNull(wordFor(page.single()))
    }

    @Test
    fun `findings survive onto the judged line`() {
        val page = judgeLines(listOf("2x - 3x = -4 - 5", "-x = -4 + 5"))
        assertNotNull(page.last().finding)
        assertEquals(Finding.Sign(5.0), page.last().finding)
    }
}
