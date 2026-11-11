package com.gamso.wakely.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import com.gamso.wakely.domain.usecase.PatternLock
import kotlin.math.hypot

private const val GRID = 3
private const val HIT_RADIUS = 0.3f
private const val DOT_RADIUS = 0.07f
private const val LINE_WIDTH = 0.12f
private const val ACTIVE_DOT_SCALE = 1.6f

/**
 * A 3x3 grid of dots on which a pattern is drawn with one finger. [onPattern] gets the dots touched (0 to 8, row by
 * row) when the finger lifts. The pattern stays drawn, in the error colour when [error], until the next touch.
 */
@Composable
fun PatternPad(onPattern: (List<Int>) -> Unit, modifier: Modifier = Modifier, error: Boolean = false) {
    var dots by remember { mutableStateOf(emptyList<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline
    Canvas(
        modifier.aspectRatio(1f).pointerInput(Unit) {
            awaitEachGesture {
                val cell = size.width / GRID.toFloat()
                val down = awaitFirstDown()
                dots = listOfNotNull(dotAt(down.position, cell))
                finger = down.position
                drag(down.id) { change ->
                    finger = change.position
                    dotAt(change.position, cell)?.let { if (it !in dots) dots = dots + it }
                    change.consume()
                }
                finger = null
                if (dots.isNotEmpty()) onPattern(dots)
            }
        },
    ) {
        val cell = size.width / GRID
        fun center(dot: Int) = Offset((dot % GRID + 0.5f) * cell, (dot / GRID + 0.5f) * cell)
        val path = dots.map(::center) + listOfNotNull(finger.takeIf { dots.isNotEmpty() })
        path.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, cell * LINE_WIDTH, StrokeCap.Round) }
        repeat(PatternLock.DOTS) { dot ->
            val on = dot in dots
            drawCircle(if (on) color else idle, cell * DOT_RADIUS * (if (on) ACTIVE_DOT_SCALE else 1f), center(dot))
        }
    }
}

/** The dot under [at], if the finger is close enough to its centre. */
private fun dotAt(at: Offset, cell: Float): Int? {
    val col = (at.x / cell).toInt()
    val row = (at.y / cell).toInt()
    if (col !in 0 until GRID || row !in 0 until GRID) return null
    val centre = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
    return if (hypot(at.x - centre.x, at.y - centre.y) <= cell * HIT_RADIUS) row * GRID + col else null
}
