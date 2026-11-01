package dev.pepotech.pepomote.ui.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pepotech.pepomote.control.TrackpadEngine
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlin.math.roundToInt

/**
 * El gesto de trackpad ("Mouse-Joystick"), para cualquier superficie: la
 * velocidad del dedo es la deflexión del stick. La velocidad se suaviza un
 * poco (media exponencial) para que no tiemble y, si el dedo se queda quieto
 * sin levantarse, se apaga en unas decenas de milisegundos: la cámara para,
 * no sigue girando sola.
 *
 * [onTouch]: dónde está el dedo (null al soltar), para pintarlo.
 */
@Composable
fun Modifier.trackpadInput(
    sensitivity: Float,
    antiDeadzone: Float,
    invertY: Boolean = false,
    accept: (Offset) -> Boolean = { true },
    onTouch: (Offset?) -> Unit = {},
    onStick: (x: Int, y: Int) -> Unit
): Modifier {
    val acceptNow by rememberUpdatedState(accept)
    var active by remember { mutableStateOf(false) }
    val emit by rememberUpdatedState(onStick)
    val touch by rememberUpdatedState(onTouch)
    val sens by rememberUpdatedState(sensitivity)
    val adz by rememberUpdatedState(antiDeadzone)
    val inv by rememberUpdatedState(invertY)
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
            val (x, y) = TrackpadEngine.fromVelocity(motion.vx, motion.vy, sens, adz)
            val out = x to (if (inv) -y else y)
            if (out != lastSent) {
                lastSent = out
                emit(out.first, out.second)
            }
        }
    }

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            // Un dedo que no es nuestro (cae en un botón de «Pulsar
            // deslizando») se deja pasar entero, sin consumirlo
            if (!acceptNow(down.position)) {
                do {
                    val ev = awaitPointerEvent()
                } while (ev.changes.any { it.id == down.id && it.pressed })
                return@awaitEachGesture
            }
            down.consume()
            motion.reset()
            touch(down.position)
            active = true
            try {
                drag(down.id) { change ->
                    // El movimiento se lee ANTES de consumir: una vez
                    // consumido, positionChange() devuelve cero
                    val d = change.positionChangeIgnoreConsumed()
                    change.consume()
                    val dt = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                    val vx = d.x / density / dt
                    val vy = d.y / density / dt
                    motion.vx += (vx - motion.vx) * SMOOTH
                    motion.vy += (vy - motion.vy) * SMOOTH
                    motion.lastMoveMs = SystemClock.uptimeMillis()
                    touch(change.position)
                }
            } finally {
                active = false
                motion.reset()
                touch(null)
                emit(0, 0)
            }
        }
    }
}

/** El rectángulo del trackpad, con su rótulo y el punto donde está el dedo. */
@Composable
fun RightStickTrackpad(
    sizeDp: Dp,
    sensitivity: Float = 1.0f,
    antiDeadzone: Float = 0.15f,
    invertY: Boolean = false,
    onStick: (x: Int, y: Int) -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val active = touchPos != null

    Box(
        modifier = Modifier
            .size(sizeDp)
            .background(PepoColors.Card, shape)
            .border(2.dp, if (active) PepoColors.Glow else PepoColors.CardBorder, shape)
            .trackpadInput(sensitivity, antiDeadzone, invertY, onTouch = { touchPos = it }, onStick = onStick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "TRACKPAD",
            color = if (active) PepoColors.Glow else PepoColors.TextDim,
            fontSize = (sizeDp.value * 0.085f).coerceIn(10f, 15f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        val p = touchPos
        if (p != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (p.x - 14.dp.toPx()).roundToInt(),
                            (p.y - 14.dp.toPx()).roundToInt()
                        )
                    }
                    .size(28.dp)
                    .background(PepoColors.Glow.copy(alpha = 0.55f), CircleShape)
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
