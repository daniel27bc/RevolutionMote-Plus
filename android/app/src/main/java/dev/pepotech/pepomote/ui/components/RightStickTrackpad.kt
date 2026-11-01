package dev.pepotech.pepomote.ui.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pepotech.pepomote.control.TrackpadEngine
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlin.math.roundToInt

/**
 * Superficie táctil que emula un stick como si fuera un trackpad / ratón
 * ("Mouse-Joystick"): la velocidad del dedo es la deflexión del stick. La
 * velocidad se suaviza un poco (media exponencial) para que no tiemble y,
 * si el dedo se queda quieto sin levantarse, se apaga en unas decenas de
 * milisegundos: la cámara para, no sigue girando sola.
 */
@Composable
fun RightStickTrackpad(
    sizeDp: Dp,
    sensitivity: Float = 1.0f,
    antiDeadzone: Float = 0.15f,
    onStick: (x: Int, y: Int) -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    var active by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }
    val emit by rememberUpdatedState(onStick)
    val sens by rememberUpdatedState(sensitivity)
    val adz by rememberUpdatedState(antiDeadzone)
    // Velocidad suavizada (dp/ms) y la hora del último movimiento
    val motion = remember { TrackpadMotion() }

    // Mientras hay dedo: cada fotograma se apaga la velocidad si el dedo no
    // se mueve, y se manda lo que toque (también el 0 al quedarse quieto)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var lastSent = 0 to 0
        while (true) {
            withFrameMillis { }
            val idle = SystemClock.uptimeMillis() - motion.lastMoveMs
            if (idle > IDLE_MS) {
                motion.vx *= DECAY
                motion.vy *= DECAY
            }
            val out = TrackpadEngine.fromVelocity(motion.vx, motion.vy, sens, adz)
            if (out != lastSent) {
                lastSent = out
                emit(out.first, out.second)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp)
            .background(PepoColors.Card, shape)
            .border(1.5.dp, if (active) PepoColors.Glow else PepoColors.CardBorder, shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    motion.reset()
                    touchPos = down.position
                    active = true
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            val d = change.positionChange()
                            val dt = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                            val vx = d.x / density / dt
                            val vy = d.y / density / dt
                            motion.vx += (vx - motion.vx) * SMOOTH
                            motion.vy += (vy - motion.vy) * SMOOTH
                            motion.lastMoveMs = SystemClock.uptimeMillis()
                            touchPos = change.position
                        }
                    } finally {
                        active = false
                        motion.reset()
                        emit(0, 0)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(if (active) 0.8f else 0.4f)
        ) {
            Text(
                "TRACKPAD",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) PepoColors.Glow else PepoColors.Blue,
                letterSpacing = 2.sp
            )
        }

        // Indicador luminoso de toque
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (touchPos.x - 12.dp.toPx()).roundToInt(),
                            (touchPos.y - 12.dp.toPx()).roundToInt()
                        )
                    }
                    .size(24.dp)
                    .background(PepoColors.Glow.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

private class TrackpadMotion {
    var vx = 0f
    var vy = 0f
    var lastMoveMs = 0L
    fun reset() {
        vx = 0f
        vy = 0f
        lastMoveMs = SystemClock.uptimeMillis()
    }
}

/** Peso de cada muestra nueva en la media (más = más directo, menos = más suave). */
private const val SMOOTH = 0.55f

/** Sin movimiento durante esto (ms), la velocidad empieza a apagarse. */
private const val IDLE_MS = 24L

/** Por fotograma quieto: a los ~80 ms la deflexión ya es casi 0. */
private const val DECAY = 0.45f
