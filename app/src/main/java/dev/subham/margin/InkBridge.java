package dev.subham.margin;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.digitalink.Ink;
import com.google.mlkit.vision.digitalink.RecognitionContext;
import com.google.mlkit.vision.digitalink.WritingArea;

/**
 * Every call into ML Kit's static factories lives here, in Java.
 *
 * Kotlin will not resolve some of these static methods on this library
 * (Ink.builder() and friends are present in the jar — verified by decompiling
 * it — but the Kotlin frontend refuses them). Calling them from Java sidesteps
 * the problem entirely rather than working around it one method at a time.
 */
public final class InkBridge {

    private InkBridge() {
    }

    public static Ink.Builder inkBuilder() {
        return Ink.builder();
    }

    public static Ink.Stroke.Builder strokeBuilder() {
        return Ink.Stroke.builder();
    }

    public static Ink.Point point(float x, float y, long timestamp) {
        return Ink.Point.create(x, y, timestamp);
    }

    /**
     * Telling the recogniser how big the writing surface is materially improves
     * accuracy: without it the model has to guess the scale of the handwriting.
     */
    /**
     * preContext is not optional despite being nullable in the signature: the
     * builder throws "Missing required properties: preContext" if it is never
     * set. An empty string means "nothing written before this line".
     */
    public static RecognitionContext context(float width, float height, @Nullable String preContext) {
        return RecognitionContext.builder()
                .setWritingArea(new WritingArea(width, height))
                .setPreContext(preContext == null ? "" : preContext)
                .build();
    }
}
