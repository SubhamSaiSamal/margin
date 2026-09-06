package dev.subham.margin

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val LOG = "margin"

/**
 * Turns strokes into text on the device.
 *
 * The model is downloaded once and then lives on the phone, so every
 * recognition after that runs with the radio off.
 */
class InkRecognizer {

    private val model = DigitalInkRecognitionModel
        .builder(DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!)
        .build()

    private val recognizer = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build()
    )

    private val models = RemoteModelManager.getInstance()

    /** True once the model is on the device and recognition can run offline. */
    suspend fun isReady(): Boolean = suspendCoroutine { continuation ->
        models.isModelDownloaded(model)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(false) }
    }

    /** Fetches the model. Needs a network once; never again. */
    suspend fun prepare(): Unit = suspendCoroutine { continuation ->
        models.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    /**
     * Every reading the model offered, best first.
     *
     * [areaWidth] and [areaHeight] describe the surface the ink was written on.
     * The recogniser uses them to work out the scale of the handwriting, and
     * accuracy is noticeably worse without them.
     */
    /**
     * [preContext] is the line written above this one. The recogniser uses it to
     * bias its predictions, which matters here because algebra lines resemble
     * the line before them far more than they resemble ordinary English.
     */
    suspend fun readAll(
        ink: Ink,
        areaWidth: Float,
        areaHeight: Float,
        preContext: String? = null,
    ): List<String> {
        val withArea = recognise(ink, InkBridge.context(areaWidth, areaHeight, preContext))
        if (withArea.isNotEmpty()) return withArea

        // A writing area that does not match the handwriting can stop the model
        // returning anything at all, so a plain read is worth trying before
        // giving up on the line.
        Log.w(LOG, "no candidates with ${areaWidth}x${areaHeight}; retrying without a writing area")
        return recognise(ink, null)
    }

    private suspend fun recognise(ink: Ink, context: RecognitionContext?): List<String> =
        suspendCoroutine { continuation ->
            val task = if (context == null) {
                recognizer.recognize(ink)
            } else {
                recognizer.recognize(ink, context)
            }

            task
                .addOnSuccessListener { result ->
                    val candidates = result.candidates.mapNotNull { it.text }
                    Log.i(LOG, "recognise(area=${context != null}) ${ink.strokes.size} strokes -> $candidates")
                    continuation.resume(candidates)
                }
                .addOnFailureListener {
                    Log.e(LOG, "recognition failed", it)
                    continuation.resume(emptyList())
                }
        }

    fun close() = recognizer.close()
}

/** Cleans up a reading into something the parser has a chance with. */
fun tidyForAlgebra(raw: String): String =
    raw.replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace('–', '-')
        .replace('—', '-')
        // A handwritten decimal point is frequently read as a comma, and in a
        // number a comma is never a separator here.
        .replace(Regex("""(?<=\d),(?=\d)"""), ".")
        .replace('·', '.')
        .replace('•', '.')
        .replace("^", "")
        .replace(Regex("""\s+"""), "")
        .trim()

/** Characters a handwriting model habitually mixes up in an algebra context. */
private fun unconfuse(text: String): String =
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
 * general text recogniser and has no idea it is looking at maths. Preferring a
 * lower-ranked candidate that parses is a large, cheap accuracy win.
 */
fun pickEquation(candidates: List<String>): String? {
    val cleaned = candidates.map { tidyForAlgebra(it) }.filter { it.isNotBlank() }

    cleaned.firstOrNull { parses(it) }?.let { return it }
    cleaned.map(::unconfuse).firstOrNull { parses(it) }?.let { return it }

    // Nothing parsed, so show the best guess anyway — a visible wrong reading is
    // far more useful to the writer than silence.
    return cleaned.firstOrNull()
}

private fun parses(text: String): Boolean =
    runCatching { parseEquation(text) }.isSuccess
