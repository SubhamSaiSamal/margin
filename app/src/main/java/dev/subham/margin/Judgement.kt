package dev.subham.margin

/**
 * Judging a whole page of working.
 *
 * One place, shared by every way a line can arrive — written on glass, read off
 * paper, or spoken aloud — so all three are held to identical standards.
 *
 * Two questions are asked of each line, not one:
 *
 *   1. Does it follow from the line directly above it?
 *   2. Is it still the problem you started with?
 *
 * The second matters more than it looks. A student who slips at line three and
 * then manipulates the wrong equation perfectly gets a tick on every line after
 * it, because each step really does follow from the last. They are also no
 * longer solving the question that was set, and nothing in any tool I have seen
 * tells them so.
 */

enum class Verdict {
    /** The first readable line. There is nothing above it to disagree with. */
    PREMISE,

    /** Follows from the line above, and still the same problem. */
    FOLLOWS,

    /** Does not follow from the line above. */
    BROKEN,

    /** Follows from the line above, but the problem changed further up. */
    DRIFTED,
}

data class Judged(
    val line: String,
    val verdict: Verdict,
    val finding: Finding? = null,
)

private fun readable(line: String): Boolean =
    runCatching { parseEquation(line) }.isSuccess

private fun agrees(a: String, b: String): Boolean =
    runCatching { equivalent(a, b) }.getOrDefault(false)

/**
 * Every line judged in order. Lines that cannot be parsed are dropped rather
 * than guessed at — a line nobody can read is not a line anyone can mark.
 */
fun judgeLines(lines: List<String>): List<Judged> {
    val usable = lines.filter(::readable)
    if (usable.isEmpty()) return emptyList()

    val premise = usable.first()
    var previous: String? = null

    return usable.map { line ->
        val above = previous
        previous = line

        when {
            above == null -> Judged(line, Verdict.PREMISE)

            !agrees(above, line) -> Judged(
                line,
                Verdict.BROKEN,
                runCatching { diagnose(above, line) }.getOrDefault(Finding.Unclear),
            )

            // It follows from the line above. Does it still answer the question?
            !agrees(premise, line) -> Judged(line, Verdict.DRIFTED)

            else -> Judged(line, Verdict.FOLLOWS)
        }
    }
}

/** What to say about a line, without ever handing over the answer. */
fun wordFor(judged: Judged): String? = when (judged.verdict) {
    Verdict.PREMISE, Verdict.FOLLOWS -> null

    Verdict.DRIFTED ->
        "this follows, but it is no longer the equation you started with"

    Verdict.BROKEN -> when (val finding = judged.finding) {
        is Finding.Sign -> "check the sign on the ${trimNumber(finding.value)}"
        Finding.LostVariable -> "an unknown went missing"
        Finding.VariableTerm -> "check what happened to the letters"
        Finding.Constant -> "check the numbers you carried across"
        Finding.Unclear, null -> "check this against the line above"
    }
}

internal fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
