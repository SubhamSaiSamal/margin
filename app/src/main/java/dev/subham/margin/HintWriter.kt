package dev.subham.margin

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File

/**
 * Puts the finding into words, using Gemma running on the phone.
 *
 * The division of labour matters and is the whole argument: the *verdict* is
 * computed symbolically in [Algebra], exactly, and can never be hallucinated.
 * This class only turns a finding that has already been established into a
 * sentence. A language model deciding whether a student's algebra is correct
 * would be the wrong tool; a language model choosing how to ask them about it
 * is the right one.
 *
 * Entirely optional. If the model is not on the device, [phrase] returns null
 * and the caller falls back to the deterministic wording — a 529MB file must
 * never be the reason a demo fails.
 */
class HintWriter(private val context: Context) {

    private var engine: LlmInference? = null

    /** Where `adb push` puts the model. */
    val modelPath: String get() = File(MODEL_DIR, MODEL_FILE).path

    val isLoaded: Boolean get() = engine != null

    /** True if the model file is present, whether or not it has been loaded yet. */
    fun isModelPresent(): Boolean = File(modelPath).exists()

    /**
     * Loads the model. Slow — tens of seconds on first call — so it belongs off
     * the main thread and well before anyone is watching.
     */
    fun load(): Boolean {
        if (engine != null) return true

        if (!isModelPresent()) {
            Log.w(LOG, "no model at $modelPath; hints stay deterministic")
            return false
        }

        return runCatching {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(TOP_K)
                .build()

            engine = LlmInference.createFromOptions(context, options)
            Log.i(LOG, "gemma loaded from $modelPath")
            true
        }.getOrElse {
            Log.e(LOG, "gemma failed to load", it)
            false
        }
    }

    /**
     * One short question about [finding], in the student's direction.
     *
     * Returns null on any failure, which the caller reads as "use the plain
     * wording instead".
     */
    fun phrase(previous: String, current: String, finding: Finding): String? {
        val llm = engine ?: return null

        val prompt = buildPrompt(previous, current, finding)

        return runCatching {
            val raw = llm.generateResponse(prompt)
            Log.i(LOG, "gemma phrased: $raw")
            tidyHint(raw)
        }.getOrElse {
            Log.e(LOG, "gemma failed to phrase a hint", it)
            null
        }
    }

    /**
     * The finding is stated as fact. The model is told what is wrong — it is
     * never asked to work it out, and never given room to reveal the answer.
     */
    private fun buildPrompt(previous: String, current: String, finding: Finding): String {
        val fault = when (finding) {
            is Finding.Sign -> "the sign on the ${finding.value.toInt()} was not flipped when the term moved across"
            Finding.LostVariable -> "an unknown disappeared between the two lines"
            Finding.VariableTerm -> "the terms containing letters changed when they should not have"
            Finding.Constant -> "the numbers were carried across incorrectly"
            Finding.Unclear -> "this line does not follow from the one above"
        }

        return """
            A student is solving an equation on paper.
            They wrote: $previous
            Then they wrote: $current
            The mistake is: $fault

            Ask them one short question that points at the mistake, under twelve
            words. Do not solve it. Do not state the correct line. Do not give
            the answer. Reply with the question only.
        """.trimIndent()
    }

    /** Models like to add quotes, preambles and second thoughts. */
    private fun tidyHint(raw: String): String? {
        val line = raw.trim()
            .lineSequence()
            .map { it.trim().removeSurrounding("\"").removePrefix("- ").trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return null

        // Anything long has almost certainly started explaining, which is the
        // one thing this app promises not to do.
        return line.takeIf { it.length in 4..90 }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    companion object {
        const val MODEL_DIR = "/data/local/tmp/llm"
        const val MODEL_FILE = "gemma3-1b-it-int4.task"

        private const val MAX_TOKENS = 512
        private const val TOP_K = 40
    }
}
