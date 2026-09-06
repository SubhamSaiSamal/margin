package dev.subham.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgebraTest {

    // ---------- single unknown ----------

    @Test
    fun `valid transposition is accepted`() {
        assertTrue(equivalent("2x + 5 = 3x - 4", "2x - 3x = -4 - 5"))
    }

    @Test
    fun `sign flip on a moved term is rejected`() {
        assertFalse(equivalent("2x - 3x = -4 - 5", "-x = -4 + 5"))
    }

    @Test
    fun `multiplying both sides by minus one is accepted`() {
        assertTrue(equivalent("-x = -9", "x = 9"))
    }

    @Test
    fun `sign on the seven is rejected`() {
        assertFalse(equivalent("3x - 7 = 2x + 1", "3x - 2x = 1 - 7"))
    }

    @Test
    fun `the correct version of that step is accepted`() {
        assertTrue(equivalent("3x - 7 = 2x + 1", "3x - 2x = 1 + 7"))
    }

    @Test
    fun `parentheses expand correctly`() {
        assertTrue(equivalent("2(x+3) = 10", "2x + 6 = 10"))
    }

    @Test
    fun `dividing through is accepted`() {
        assertTrue(equivalent("4x = 8", "x = 2"))
    }

    @Test
    fun `scaling the whole equation is accepted`() {
        assertTrue(equivalent("2x + 6 = 0", "x + 3 = 0"))
    }

    @Test
    fun `an arithmetic slip is rejected`() {
        assertFalse(equivalent("x + 2 = 5", "x = 4"))
    }

    @Test
    fun `unicode minus is read the same as a hyphen`() {
        assertTrue(equivalent("2x + 5 = 3x − 4", "2x - 3x = -4 - 5"))
    }

    // ---------- more than one unknown ----------
    // These are the cases the single-variable engine got wrong on a real phone:
    // it collapsed every letter into one unknown and approved a broken step.

    @Test
    fun `dropping a sign on the second unknown is rejected`() {
        assertFalse(equivalent("3x + y = 0", "3x = y"))
    }

    @Test
    fun `moving the second unknown across correctly is accepted`() {
        assertTrue(equivalent("3x + y = 0", "3x = -y"))
    }

    @Test
    fun `two unknowns are not the same unknown`() {
        assertFalse(equivalent("x = 4", "y = 4"))
    }

    @Test
    fun `scaling a two unknown equation is accepted`() {
        assertTrue(equivalent("2x + 4y = 6", "x + 2y = 3"))
    }

    @Test
    fun `case does not change which unknown is meant`() {
        assertTrue(equivalent("3x = Y", "3x = y"))
    }

    @Test
    fun `losing an unknown entirely is rejected`() {
        assertFalse(equivalent("2x + 6 = 10", "6 = 10"))
    }

    // ---------- diagnosis ----------

    @Test
    fun `names the five`() {
        assertEquals(Finding.Sign(5.0), diagnose("2x - 3x = -4 - 5", "-x = -4 + 5"))
    }

    @Test
    fun `names the seven`() {
        assertEquals(Finding.Sign(7.0), diagnose("3x - 7 = 2x + 1", "3x - 2x = 1 - 7"))
    }

    @Test
    fun `notices a dropped unknown`() {
        assertEquals(Finding.LostVariable, diagnose("2x + 6 = 10", "6 = 10"))
    }

    @Test
    fun `notices a changed variable term`() {
        assertEquals(Finding.VariableTerm, diagnose("4x = 8", "5x = 8"))
    }

    @Test
    fun `falls back to the constants`() {
        assertEquals(Finding.Constant, diagnose("x + 2 = 5", "x = 4"))
    }

    // ---------- roots ----------

    @Test
    fun `root is reported for a solvable line`() {
        assertEquals(9.0, root("2x + 5 = 3x - 4")!!, 1e-9)
    }

    @Test
    fun `no single root when two unknowns remain`() {
        assertNull(root("3x + y = 0"))
    }
}
