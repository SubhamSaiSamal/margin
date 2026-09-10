package dev.subham.margin

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * The other way in: point the camera at a page of paper.
 *
 * The engine underneath is identical — a line of working read off paper is
 * judged by exactly the same symbolic equivalence as a line written on glass.
 * Only the way the characters arrive changes.
 *
 * This is a different ML Kit model from the ink recogniser: text recognition
 * works on pixels and needs no strokes, so it reads printing and handwriting
 * that already exists rather than watching it being made. It also runs entirely
 * on the device, so the offline claim holds either way.
 */
class PaperReader {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Every line of working the camera can see, top to bottom.
     *
     * Reading order matters: the engine judges each line against the one above
     * it, so lines are sorted by vertical position rather than trusting the
     * order the recogniser happens to return them in.
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun read(image: ImageProxy, onLines: (List<String>) -> Unit) {
        val source = image.image
        if (source == null) {
            image.close()
            return
        }

        val input = InputImage.fromMediaImage(source, image.imageInfo.rotationDegrees)

        recognizer.process(input)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks
                    .flatMap { it.lines }
                    .sortedBy { it.boundingBox?.top ?: 0 }
                    .mapNotNull { line -> pickEquation(listOf(line.text)) }
                    .filter { runCatching { parseEquation(it) }.isSuccess }

                if (lines.isNotEmpty()) Log.i(LOG, "paper: $lines")
                onLines(lines)
            }
            .addOnFailureListener {
                Log.e(LOG, "paper read failed", it)
                onLines(emptyList())
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() = recognizer.close()

    companion object {
        /** Analyse the newest frame and drop the rest; reading every frame is wasted work. */
        fun analyser(): ImageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
}

/**
 * Judge a whole page at once, the way the notebook judges it line by line.
 *
 * Returns each line with whether it follows from the line above, so a page
 * photographed from a real notebook gets the same ticks and crosses.
 */
fun judgePage(lines: List<String>): List<Pair<String, Boolean>> {
    var previous: String? = null

    return lines.map { line ->
        val above = previous
        val holds = when {
            above == null -> true
            else -> runCatching { equivalent(above, line) }.getOrDefault(false)
        }
        previous = line
        line to holds
    }
}
