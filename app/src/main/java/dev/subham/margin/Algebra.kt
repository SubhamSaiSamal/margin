package dev.subham.margin

import kotlin.math.abs

/**
 * Linear equation equivalence, computed rather than guessed.
 *
 * Every expression in scope reduces to a set of variable terms plus a constant,
 * so a whole equation reduces to  c1*v1 + c2*v2 + ... + k = 0.  Two lines of
 * working are equivalent when they describe the same solution set, which for
 * linear equations means one is a non-zero scalar multiple of the other.
 *
 * Handling several variables matters: an earlier single-variable version
 * collapsed every letter into one unknown and cheerfully accepted 3x + y = 0
 * becoming 3x = y. Silently approving a wrong step is the worst thing this
 * app could do, so variables are tracked separately.
 */

class AlgebraError(message: String) : Exception(message)

/** c1*v1 + c2*v2 + ... + constant = 0 */
data class Equation(val coefficients: Map<Char, Double>, val constant: Double)

/** A parsed value: variable terms plus a constant. */
private data class Linear(val terms: Map<Char, Double>, val constant: Double)

private const val NEAR = 1e-9

private fun Map<Char, Double>.pruned(): Map<Char, Double> =
    filterValues { abs(it) > NEAR }

// ---------- tokens ----------

private sealed interface Token {
    data class Num(val value: Double) : Token
    data class Var(val name: Char) : Token
    data class Symbol(val ch: Char) : Token
}

private fun tokenize(source: String): List<Token> {
    val clean = source
        .replace('−', '-')
        .replace('–', '-')
        .replace('—', '-')
        .replace('×', '*')
        .replace('⋅', '*')
        .filterNot { it.isWhitespace() }

    val tokens = mutableListOf<Token>()
    var i = 0

    while (i < clean.length) {
        val ch = clean[i]

        when {
            ch.isDigit() || ch == '.' -> {
                val start = i
                while (i < clean.length && (clean[i].isDigit() || clean[i] == '.')) i++
                val text = clean.substring(start, i)
                tokens += Token.Num(text.toDoubleOrNull() ?: throw AlgebraError("can't read \"$text\""))
            }

            ch.isLetter() -> {
                // Handwriting recognition is inconsistent about case, and a
                // student means the same unknown by Y as by y.
                tokens += Token.Var(ch.lowercaseChar())
                i++
            }

            ch in "+-*/()" -> {
                tokens += Token.Symbol(ch)
                i++
            }

            else -> throw AlgebraError("can't read \"$ch\"")
        }
    }

    return tokens
}

// ---------- arithmetic on linear values ----------

private fun add(p: Linear, q: Linear): Linear {
    val terms = p.terms.toMutableMap()
    q.terms.forEach { (name, coefficient) ->
        terms[name] = (terms[name] ?: 0.0) + coefficient
    }
    return Linear(terms.pruned(), p.constant + q.constant)
}

private fun negate(p: Linear) = Linear(p.terms.mapValues { -it.value }, -p.constant)

private fun sub(p: Linear, q: Linear) = add(p, negate(q))

private fun mul(p: Linear, q: Linear): Linear {
    if (p.terms.isNotEmpty() && q.terms.isNotEmpty()) throw AlgebraError("not linear")

    val (scalar, other) = if (p.terms.isEmpty()) p.constant to q else q.constant to p
    return Linear(other.terms.mapValues { it.value * scalar }.pruned(), other.constant * scalar)
}

private fun div(p: Linear, q: Linear): Linear {
    if (q.terms.isNotEmpty()) throw AlgebraError("can't divide by a term with a letter in it")
    if (abs(q.constant) < NEAR) throw AlgebraError("divide by zero")
    return Linear(p.terms.mapValues { it.value / q.constant }.pruned(), p.constant / q.constant)
}

// ---------- recursive descent ----------

private class Parser(private val tokens: List<Token>) {
    private var pos = 0

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun symbolAt(ch: Char): Boolean {
        val t = peek()
        return t is Token.Symbol && t.ch == ch
    }

    fun expression(): Linear {
        var value = term()

        while (symbolAt('+') || symbolAt('-')) {
            val op = (peek() as Token.Symbol).ch
            pos++
            val rhs = term()
            value = if (op == '+') add(value, rhs) else sub(value, rhs)
        }

        return value
    }

    private fun term(): Linear {
        var value = factor()

        loop@ while (true) {
            when (val t = peek()) {
                is Token.Symbol -> when (t.ch) {
                    '*' -> { pos++; value = mul(value, factor()) }
                    '/' -> { pos++; value = div(value, factor()) }
                    '(' -> value = mul(value, factor()) // 2(x+1)
                    else -> break@loop
                }
                is Token.Num, is Token.Var -> value = mul(value, factor()) // 3x
                null -> break@loop
            }
        }

        return value
    }

    private fun factor(): Linear {
        val t = peek() ?: throw AlgebraError("line ends early")

        if (t is Token.Symbol) {
            when (t.ch) {
                '-' -> { pos++; return negate(factor()) }
                '+' -> { pos++; return factor() }
                '(' -> {
                    pos++
                    val inner = expression()
                    if (!symbolAt(')')) throw AlgebraError("missing )")
                    pos++
                    return inner
                }
                else -> throw AlgebraError("unexpected symbol")
            }
        }

        if (t is Token.Num) { pos++; return Linear(emptyMap(), t.value) }

        pos++
        return Linear(mapOf((t as Token.Var).name to 1.0), 0.0)
    }

    fun atEnd(): Boolean = pos == tokens.size
}

private fun parseSide(source: String): Linear {
    val parser = Parser(tokenize(source))
    val value = parser.expression()
    if (!parser.atEnd()) throw AlgebraError("trailing symbols")
    return value
}

/** "3x + y = 0" becomes 3x + 1y + 0 = 0 */
fun parseEquation(source: String): Equation {
    val halves = source.split("=")
    if (halves.size != 2) throw AlgebraError("needs exactly one =")

    val difference = sub(parseSide(halves[0]), parseSide(halves[1]))
    return Equation(difference.terms.pruned(), difference.constant)
}

/**
 * Do these two lines describe the same solution set? For linear equations that
 * holds exactly when one is a non-zero multiple of the other, so 2x = 8 and
 * x = 4 agree, while 3x + y = 0 and 3x = y do not.
 */
fun equivalent(previous: String, current: String): Boolean {
    val p = parseEquation(previous)
    val q = parseEquation(current)

    val names = (p.coefficients.keys + q.coefficients.keys).toList()

    // No letters left on either side: both are statements about numbers alone.
    if (names.isEmpty()) return (abs(p.constant) < NEAR) == (abs(q.constant) < NEAR)

    // One side still has an unknown and the other does not.
    if (p.coefficients.isEmpty() != q.coefficients.isEmpty()) return false

    val left = names.map { p.coefficients[it] ?: 0.0 } + p.constant
    val right = names.map { q.coefficients[it] ?: 0.0 } + q.constant

    // Scale is set by the first coefficient that is actually present.
    val pivot = left.indexOfFirst { abs(it) > NEAR }
    if (pivot == -1) return right.all { abs(it) < NEAR }

    val scale = right[pivot] / left[pivot]
    if (abs(scale) < NEAR) return false

    return left.indices.all { abs(right[it] - scale * left[it]) < NEAR }
}

/** The root, when exactly one unknown is left. Used for explaining, never for answering. */
fun root(source: String): Double? {
    val (coefficients, constant) = parseEquation(source)
    if (coefficients.size != 1) return null
    val coefficient = coefficients.values.first()
    return if (abs(coefficient) > NEAR) -constant / coefficient else null
}

/** What went wrong, reported from what actually changed between the two lines. */
sealed interface Finding {
    /** A term flipped sign; [value] is the number it happened to. */
    data class Sign(val value: Double) : Finding
    data object LostVariable : Finding
    data object VariableTerm : Finding
    data object Constant : Finding
    data object Unclear : Finding
}

private val NUMBER = Regex("""\d+(?:\.\d+)?""")

/**
 * Only ever called once a step is already known wrong. A term that flipped sign
 * moves the constant by exactly twice its value, so the number can be solved
 * for and then checked against what the student actually wrote.
 */
fun diagnose(previous: String, current: String): Finding {
    val before = parseEquation(previous)
    val after = parseEquation(current)

    if (before.coefficients.isNotEmpty() && after.coefficients.isEmpty()) return Finding.LostVariable

    val sameTerms = before.coefficients.keys == after.coefficients.keys &&
        before.coefficients.all { (name, value) -> abs((after.coefficients[name] ?: 0.0) - value) < NEAR }
    val sameConstant = abs(before.constant - after.constant) < NEAR

    if (sameTerms && !sameConstant) {
        val shift = abs(after.constant - before.constant) / 2
        val written = (NUMBER.findAll(previous) + NUMBER.findAll(current))
            .mapNotNull { it.value.toDoubleOrNull() }
            .toSet()

        written.firstOrNull { abs(it - shift) < NEAR }?.let { return Finding.Sign(it) }
        return Finding.Constant
    }

    if (!sameTerms && sameConstant) return Finding.VariableTerm
    return Finding.Unclear
}
