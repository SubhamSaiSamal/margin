package dev.subham.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A page read off paper is judged by the same engine as a page written on
 * glass. These pin that down: the input changed, the reasoning did not.
 */
class PaperTest {

    @Test
    fun `a correct page is all ticks`() {
        val page = judgePage(
            listOf(
                "2x + 5 = 3x - 4",
                "2x - 3x = -4 - 5",
                "-x = -9",
                "x = 9",
            )
        )

        assertEquals(4, page.size)
        assertTrue(page.all { it.second })
    }

    @Test
    fun `the first broken line is the one marked`() {
        val page = judgePage(
            listOf(
                "2x + 5 = 3x - 4",
                "2x - 3x = -4 - 5",
                "-x = -4 + 5",   // sign not flipped
                "x = -1",
            )
        )

        assertEquals(listOf(true, true, false, true), page.map { it.second })
    }

    @Test
    fun `the first line is always the premise`() {
        val page = judgePage(listOf("7x = 21"))
        assertEquals(listOf(true), page.map { it.second })
    }

    @Test
    fun `an empty page judges to nothing`() {
        assertTrue(judgePage(emptyList()).isEmpty())
    }

    @Test
    fun `two unknowns survive the trip through the camera`() {
        val page = judgePage(listOf("3x + y = 0", "3x = y"))
        assertEquals(listOf(true, false), page.map { it.second })
    }
}
