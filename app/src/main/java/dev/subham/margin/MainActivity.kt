package dev.subham.margin

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

// The deck's palette, so the app and the pitch are visibly one product.
val Paper = Color(0xFFF7F5EF)
val Ink = Color(0xFF191D24)
val Graphite = Color(0xFF5C6672)
val RedPen = Color(0xFFC22F38)
val InkBlue = Color(0xFF22447D)

private val ROW_HEIGHT = 92.dp
private val MARGIN_X = 44.dp
private const val SETTLE_MILLIS = 900L

class MainActivity : ComponentActivity() {

    private var speaker: TextToSpeech? = null
    private val recognizer = InkRecognizer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speaker = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) speaker?.language = Locale.UK
        }

        setContent {
            MarginApp(
                recognizer = recognizer,
                say = { line -> speaker?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "margin") },
            )
        }
    }

    override fun onDestroy() {
        speaker?.shutdown()
        recognizer.close()
        super.onDestroy()
    }
}

@Composable
fun MarginApp(recognizer: InkRecognizer, say: (String) -> Unit) {
    // Keyed by ruled-line index, so what you write lands on the line you wrote it on.
    val lines = remember { mutableMapOf<Int, WrittenLine>() }

    var revision by remember { mutableIntStateOf(0) }
    var settleTick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("preparing handwriting model") }
    var spoken by remember { mutableStateOf<Int?>(null) }
    var surface by remember { mutableStateOf(IntSize.Zero) }
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    LaunchedEffect(Unit) {
        status = try {
            if (!recognizer.isReady()) recognizer.prepare()
            "ready · offline"
        } catch (error: Exception) {
            "model unavailable"
        }
    }

    // Resting the pen is the signal. Every new stroke restarts this.
    LaunchedEffect(settleTick) {
        if (settleTick == 0) return@LaunchedEffect
        delay(SETTLE_MILLIS)

        val pending = lines.filterValues { it.pending && it.strokes.isNotEmpty() }
        if (pending.isEmpty()) return@LaunchedEffect
        if (surface.width == 0 || surface.height == 0) return@LaunchedEffect

        for ((row, line) in pending) {
            // A ruled row is the writing area, not the whole page: telling the
            // recogniser the area is 2400px tall when the writing is 270px tall
            // breaks the scale it normalises against.
            val top = row * rowPx

            // The nearest readable line above, so the recogniser knows roughly
            // what this line is likely to look like.
            val above = lines.entries
                .filter { it.key < row && it.value.reading != null }
                .maxByOrNull { it.key }
                ?.value?.reading

            val candidates = runCatching {
                recognizer.readAll(
                    ink = line.strokes.toInk(offsetY = top),
                    areaWidth = surface.width.toFloat(),
                    areaHeight = rowPx,
                    preContext = above,
                )
            }.onFailure { Log.e(LOG, "readAll threw for row $row", it) }
                .getOrDefault(emptyList())

            val reading = pickEquation(candidates)
            val shape = line.strokes.joinToString(",") { it.points.size.toString() }
            Log.i(LOG, "row $row: ${line.strokes.size} strokes (points: $shape) -> $candidates -> picked=$reading")

            line.reading = reading
            line.pending = false
        }

        judge(lines)
        revision++

        // Speak only the newest complaint, and only once.
        val newest = lines.entries
            .filter { it.value.holds == false && it.value.hint != null }
            .maxByOrNull { it.key }

        if (newest != null && newest.key != spoken) {
            spoken = newest.key
            newest.value.hint?.let(say)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .onSizeChanged { surface = it }
    ) {

        NotebookPage(
            lines = lines,
            revision = revision,
            rowHeight = ROW_HEIGHT,
            marginX = MARGIN_X,
            onStrokeComplete = { row, stroke ->
                val line = lines.getOrPut(row) { WrittenLine() }
                line.strokes += stroke
                line.pending = true
                revision++
                settleTick++
            },
        )

        // Verdicts sit in the margin, beside the handwriting that caused them.
        lines.forEach { (row, line) ->
            val holds = line.holds
            if (holds != null) {
                Text(
                    text = if (holds) "✓" else "✗",
                    modifier = Modifier
                        .offset(y = ROW_HEIGHT * row + ROW_HEIGHT * 0.34f)
                        .width(MARGIN_X),
                    style = TextStyle(
                        color = if (holds) InkBlue else RedPen,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }

            // What it read, small and out of the way, so a misread is visible.
            // Tapping it throws that one line away rather than the whole page.
            line.reading?.let { reading ->
                Text(
                    text = if (line.holds == false && line.hint != null) line.hint!! else reading,
                    modifier = Modifier
                        .offset(y = ROW_HEIGHT * row + ROW_HEIGHT * 0.04f)
                        .fillMaxWidth()
                        .clickable {
                            lines.remove(row)
                            judge(lines)
                            spoken = null
                            revision++
                        }
                        .padding(end = 14.dp, top = 4.dp, bottom = 4.dp),
                    style = TextStyle(
                        color = if (line.holds == false) RedPen else Graphite,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 58.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "margin.",
                style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = status,
                modifier = Modifier.fillMaxWidth().padding(end = 60.dp),
                style = TextStyle(color = Graphite, fontSize = 11.sp, textAlign = TextAlign.End),
            )
        }

        Text(
            text = "clear",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 14.dp)
                .clickable {
                    lines.clear()
                    spoken = null
                    revision++
                }
                .padding(6.dp),
            style = TextStyle(color = Graphite, fontSize = 12.sp),
        )
    }
}

/**
 * Judge every readable line against the readable line above it. Re-run from
 * scratch each time, so correcting an earlier line fixes everything below it.
 */
fun judge(lines: Map<Int, WrittenLine>) {
    var previous: String? = null

    lines.entries.sortedBy { it.key }.forEach { (_, line) ->
        val reading = line.reading

        if (reading == null) {
            line.holds = null
            line.hint = null
            return@forEach
        }

        val parses = runCatching { parseEquation(reading) }.isSuccess
        if (!parses) {
            line.holds = null
            line.hint = null
            return@forEach
        }

        val above = previous
        if (above == null) {
            // The first readable line is the premise; there is nothing to disagree with.
            line.holds = true
            line.hint = null
        } else if (runCatching { equivalent(above, reading) }.getOrDefault(false)) {
            line.holds = true
            line.hint = null
        } else {
            line.holds = false
            line.hint = hintFor(above, reading)
        }

        previous = reading
    }
}

/** What to ask about, without ever handing over the answer. */
fun hintFor(previous: String, current: String): String =
    when (val finding = runCatching { diagnose(previous, current) }.getOrDefault(Finding.Unclear)) {
        is Finding.Sign -> "check the sign on the ${trimNumber(finding.value)}"
        Finding.LostVariable -> "an unknown went missing"
        Finding.VariableTerm -> "check what happened to the letters"
        Finding.Constant -> "check the numbers you carried across"
        Finding.Unclear -> "check this against the line above"
    }

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
