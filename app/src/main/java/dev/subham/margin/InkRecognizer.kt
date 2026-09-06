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
     * gets noticeably worse without them.
     *
     * [preContext] is the line written above this one, which biases predictions
     * — algebra lines resemble the line before them far more than they resemble
     * ordinary English.
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
