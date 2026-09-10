package dev.subham.margin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.digitalink.Ink

/** One finger-drawn stroke: where it went, and when. */
class PenStroke {
    val points = mutableListOf<Triple<Float, Float, Long>>()

    /** Vertical middle of the stroke, used to decide which ruled line it sits on. */
    fun middleY(): Float =
        if (points.isEmpty()) 0f else points.sumOf { it.second.toDouble() }.toFloat() / points.size

    fun asPath(): Path = Path().apply {
        points.firstOrNull()?.let { moveTo(it.first, it.second) }
        points.drop(1).forEach { lineTo(it.first, it.second) }
    }
}

/** Everything written on one ruled line. */
class WrittenLine {
    val strokes = mutableListOf<PenStroke>()

    /** What the recogniser last made of it. */
    var reading: String? = null

    /** How it fared against the line above and the premise. Null until judged. */
    var verdict: Verdict? = null

    /** Set when the line does not follow, and spoken once. */
    var hint: String? = null

    /** What the engine established, kept so the hint can be re-worded later. */
    var finding: Finding? = null

    /** Strokes have changed since the last reading. */
    var pending: Boolean = false
}

/**
 * Strokes become ML Kit ink in the surface's own coordinates, which is what the
 * recogniser expects when it is also told the size of that surface.
 */
fun List<PenStroke>.toInk(offsetY: Float = 0f): Ink {
    val ink = InkBridge.inkBuilder()

    forEach { stroke ->
        val builder = InkBridge.strokeBuilder()
        stroke.points.forEach { (x, y, t) -> builder.addPoint(InkBridge.point(x, y - offsetY, t)) }
        ink.addStroke(builder.build())
    }

    return ink.build()
}

/**
 * The page itself: faint rules, the red margin, and every stroke written so far.
 *
 * The whole screen is the writing surface. There is nothing to press — a line
 * is judged when the pen rests, and the verdict appears in the margin beside
 * the handwriting that caused it.
 */
@Composable
fun NotebookPage(
    lines: Map<Int, WrittenLine>,
    revision: Int,
    rowHeight: Dp,
    marginX: Dp,
    modifier: Modifier = Modifier,
    ruleColor: Color = Color(0x14191D24),
    marginColor: Color = Color(0x40C22F38),
    inkColor: Color = Color(0xFF191D24),
    onStrokeComplete: (row: Int, stroke: PenStroke) -> Unit,
) {
    // The stroke currently under the finger. Held here, so one pen movement is
    // always exactly one stroke — an earlier version rebuilt this from shared
    // state on every touch event and shredded each movement into fragments,
    // which left the recogniser with ink it could not read at all.
    val live = remember { mutableStateOf<PenStroke?>(null) }
    val liveRevision = remember { mutableIntStateOf(0) }
    val complete by rememberUpdatedState(onStrokeComplete)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val rowPx = rowHeight.toPx()

                detectDragGestures(
                    onDragStart = { start ->
                        live.value = PenStroke().apply {
                            points += Triple(start.x, start.y, System.currentTimeMillis())
                        }
                        liveRevision.intValue++
                    },
                    onDrag = { change, _ ->
                        live.value?.points?.plusAssign(
                            Triple(change.position.x, change.position.y, System.currentTimeMillis())
                        )
                        liveRevision.intValue++
                        change.consume()
                    },
                    onDragEnd = {
                        live.value?.let { stroke ->
                            val row = (stroke.middleY() / rowPx).toInt().coerceAtLeast(0)
                            complete(row, stroke)
                        }
                        live.value = null
                        liveRevision.intValue++
                    },
                    onDragCancel = {
                        live.value?.let { stroke ->
                            val row = (stroke.middleY() / rowPx).toInt().coerceAtLeast(0)
                            complete(row, stroke)
                        }
                        live.value = null
                        liveRevision.intValue++
                    },
                )
            }
    ) {
        revision // read both so Compose repaints as ink is mutated in place
        liveRevision.intValue

        val rowPx = rowHeight.toPx()
        val marginPx = marginX.toPx()

        // Ruled lines, kept faint enough to sit under handwriting.
        var y = rowPx
        while (y < size.height) {
            drawLine(
                color = ruleColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += rowPx
        }

        drawLine(
            color = marginColor,
            start = Offset(marginPx, 0f),
            end = Offset(marginPx, size.height),
            strokeWidth = 1.5f,
        )

        val pen = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        lines.values.forEach { line ->
            line.strokes.forEach { stroke ->
                drawPath(path = stroke.asPath(), color = inkColor, style = pen)
            }
        }

        live.value?.let { drawPath(path = it.asPath(), color = inkColor, style = pen) }
    }
}
