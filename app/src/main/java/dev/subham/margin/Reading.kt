package dev.subham.margin

/**
 * Turning what the recogniser saw into something the parser can judge.
 *
 * Deliberately free of Android and ML Kit imports: this is where the tuning
 * against real handwriting happens, so it needs to be testable without a phone
 * in the room.
 */

/** Cleans up a reading into something the parser has a chance with. */
fun tidyForAlgebra(raw: String): String =
    raw.replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace('–', '-')
        .replace('—', '-')
        // A handwritten decimal point is frequently read as a comma, and inside
        // a number a comma is never a thousands separator here.
        .replace(Regex("""(?<=\d),(?=\d)"""), ".")
        .replace('·', '.')
        .replace('•', '.')
        .replace("^", "")
        .replace(Regex("""\s+"""), "")
        .trim()

/**
 * Characters a handwriting model habitually mixes up in an algebra context.
 * A lone vertical mark is a 1 rather than an l; a ring is a zero; a cross is
 * the unknown rather than a multiplication sign.
 */
fun unconfuse(text: String): String =
    text.map { ch ->
        when (ch) {
            'l', 'I', '|' -> '1'
            'O', 'o' -> '0'
            'S' -> '5'
            'Z' -> '2'
            '×', '*', 'X' -> 'x'
            else -> ch
        }
    }.joinToString("")

/**
 * The best reading that is actually parseable as an equation.
 *
 * The top-ranked candidate is frequently not the algebra one — the model is a
 * general text recogniser with no idea it is looking at maths, and will happily
 * rank "ll" above "1=1". Preferring a lower-ranked candidate that parses is a
 * large and very cheap accuracy win.
 *
 * When nothing parses the best guess is returned anyway: a visible wrong
 * reading tells the writer what happened, where silence tells them nothing.
 */
fun pickEquation(candidates: List<String>): String? {
    val cleaned = candidates.map(::tidyForAlgebra).filter { it.isNotBlank() }

    // Both readings of every candidate, in rank order, since the confusable
    // form is often the intended one.
    val variants = cleaned.flatMap { listOf(it, unconfuse(it)) }.distinct()

    // Parsing alone is not enough. "x=l" parses — as an equation in two
    // unknowns, x and l — so a misread 1 becomes a phantom variable and the
    // verdict is confidently wrong. Nobody calls an unknown l.
    variants.firstOrNull { parsesAsEquation(it) && usesRealUnknowns(it) }?.let { return it }
    variants.firstOrNull(::parsesAsEquation)?.let { return it }

    return cleaned.map(::unconfuse).firstOrNull() ?: cleaned.firstOrNull()
}

/** Letters a student actually uses for an unknown. */
private const val UNKNOWNS = "xyzabcmnpqtuvwk"

private fun usesRealUnknowns(text: String): Boolean =
    text.filter { it.isLetter() }.all { it.lowercaseChar() in UNKNOWNS }

private fun parsesAsEquation(text: String): Boolean =
    runCatching { parseEquation(text) }.isSuccess
