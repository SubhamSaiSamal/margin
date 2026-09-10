package dev.subham.margin

/**
 * Turning a spoken line of working into an equation.
 *
 * The third way in, and the one that needs no hands: the phone lies flat on
 * the desk while the student works on paper and says what they are doing.
 * "two x minus three x equals minus four minus five" is a line of working like
 * any other, and is judged by exactly the same engine.
 *
 * Pure text, deliberately: this is the part worth getting right, and it can be
 * tested without a microphone in the room.
 */

/** Multi-word forms first, so "is equal to" is not read as three separate words. */
private val PHRASES = listOf(
    "is equal to" to "=",
    "equal to" to "=",
    "equals to" to "=",
    "equals" to "=",
    "same as" to "=",
    "divided by" to "/",
    "multiplied by" to "*",
    "open bracket" to "(",
    "open parenthesis" to "(",
    "close bracket" to ")",
    "close parenthesis" to ")",
    "square root of" to "sqrt",
)

private val WORDS = mapOf(
    "plus" to "+",
    "add" to "+",
    "and" to "+",
    "minus" to "-",
    "subtract" to "-",
    "negative" to "-",
    "take away" to "-",
    "times" to "*",
    "into" to "*",
    "over" to "/",
    "by" to "/",
    // Recognisers hear letters as the words that sound like them.
    "ex" to "x",
    "axe" to "x",
    "eks" to "x",
    "why" to "y",
    "wye" to "y",
    "zed" to "z",
    "zee" to "z",
)

private val UNITS = mapOf(
    "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "to" to 2, "too" to 2,
    "three" to 3, "four" to 4, "for" to 4, "five" to 5, "six" to 6,
    "seven" to 7, "eight" to 8, "ate" to 8, "nine" to 9, "ten" to 10,
    "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
    "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
    "nineteen" to 19,
)

private val TENS = mapOf(
    "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
    "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
)

/**
 * The equation a student just said, or null if it cannot be made into one.
 *
 * Returns a string in the same shape the ink and camera paths produce, so
 * everything downstream is identical no matter how the line arrived.
 */
fun fromSpeech(spoken: String): String? {
    var text = spoken.lowercase().replace(Regex("""[,.?!]"""), " ")

    for ((phrase, symbol) in PHRASES) {
        text = text.replace(phrase, " $symbol ")
    }

    val tokens = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
    val out = StringBuilder()
    var index = 0

    while (index < tokens.size) {
        val token = tokens[index]

        // A run of number words is one number: "twenty five" is 25, not 20 then 5.
        val (value, consumed) = readNumber(tokens, index)
        if (consumed > 0) {
            out.append(value)
            index += consumed
            continue
        }

        val mapped = WORDS[token] ?: token
        out.append(mapped)
        index++
    }

    val equation = tidyForAlgebra(out.toString())
    if (equation.isBlank()) return null

    // Only hand back something the engine can actually judge.
    return equation.takeIf { runCatching { parseEquation(it) }.isSuccess }
}

/** Reads a compound number starting at [start]; returns the value and how many words it ate. */
private fun readNumber(tokens: List<String>, start: Int): Pair<Int, Int> {
    var total = 0
    var index = start
    var matched = false

    while (index < tokens.size) {
        val token = tokens[index]

        when {
            token.toIntOrNull() != null -> {
                // A recogniser that already gave digits: take them and stop.
                if (matched) break
                return token.toInt() to 1
            }

            TENS.containsKey(token) -> {
                total += TENS.getValue(token)
                matched = true
                index++
            }

            UNITS.containsKey(token) -> {
                total += UNITS.getValue(token)
                matched = true
                index++
                // A unit ends the number, unless it was the multiplier in
                // "one hundred". Without this the run stops early and the
                // leftover "hundred" is emitted as a word.
                if (tokens.getOrNull(index) != "hundred") break
            }

            token == "hundred" && matched -> {
                total *= 100
                index++
            }

            else -> break
        }
    }

    return if (matched) total to (index - start) else 0 to 0
}
