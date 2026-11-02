package dev.pepotech.pepomote.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalView
import dev.pepotech.pepomote.control.ButtonState
import dev.pepotech.pepomote.control.Haptics
import dev.pepotech.pepomote.control.Kbm
import dev.pepotech.pepomote.control.PointerTrackpad
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** Adónde va lo que hace el dedo en un touchpad de ratón. */
interface MouseOut {
    /** Velocidad del dedo en dp/ms (+y abajo), ya con su sensibilidad. */
    fun move(vx: Float, vy: Float)
    fun button(index: Int, down: Boolean)
    fun wheel(units: Int)

    /** Gesto de tres dedos (atajo de teclado). false = este camino no tiene teclas. */
    fun gesture(g: SwipeGesture): Boolean = false
    fun zoom(units: Int) {}
}

enum class SwipeGesture { UP, DOWN, LEFT, RIGHT }

/** Por [Kbm] (receptor de RevolutionMote+, Bluetooth o el puntero del receptor original). */
class KbmOut(private val src: Int) : MouseOut {
    override fun move(vx: Float, vy: Float) = Kbm.setTouch(src, vx, vy)
    override fun button(index: Int, down: Boolean) = Kbm.button(index, down)
    override fun wheel(units: Int) = Kbm.wheel(units)

    // Los atajos de Windows de los touchpads de precisión
    override fun gesture(g: SwipeGesture): Boolean = when (g) {
        SwipeGesture.UP -> Kbm.combo("MetaLeft", "Tab")          // vista de tareas
        SwipeGesture.DOWN -> Kbm.combo("MetaLeft", "KeyD")       // escritorio
        SwipeGesture.RIGHT -> Kbm.combo("AltLeft", "Tab")        // siguiente ventana
        SwipeGesture.LEFT -> Kbm.combo("AltLeft", "ShiftLeft", "Tab")
    }

    override fun zoom(units: Int) = Kbm.zoom(units)
}

/** Modo puntero del mando de Wii: giro sintético, A = clic izquierdo, B = derecho. */
object PointerOut : MouseOut {
    override fun move(vx: Float, vy: Float) = PointerTrackpad.setVelocity(vx, vy)
    override fun button(index: Int, down: Boolean) {
        when (index) {
            0 -> ButtonState.set(ButtonState.A, down)
            1 -> ButtonState.set(ButtonState.B, down)
        }
    }
    override fun wheel(units: Int) = ButtonState.addScroll(units / 4)
}

/** Tiempos y distancias de los gestos (los de un touchpad de portátil). */
object MouseGestureTiming {
    /** Un toque más corto que esto y sin moverse es un clic. */
    const val TAP_MS = 240L

    /** Tras un toque, si el dedo vuelve antes de esto: doble toque (doble clic o agarrar). */
    const val SECOND_TAP_MS = 230L

    /** Mantener el dedo quieto esto agarra (botón izquierdo pulsado). */
    const val HOLD_MS = 380L

    /** Lo que se puede mover un dedo (dp) y seguir contando como quieto. */
    const val SLOP_DP = 6f

    /** Recorrido (dp) de tres dedos para que cuente como gesto. */
    const val SWIPE_DP = 45f
}

/**
 * Un touchpad de ratón completo, como el de un portátil:
 *
 * - un dedo mueve el cursor (con inercia si se quiere);
 * - toque = clic; dos toques = doble clic;
 * - toque y, sin levantar el segundo, mover (o mantener) = agarrar y arrastrar;
 * - mantener el dedo quieto un momento = agarrar también;
 * - dos dedos: toque = clic derecho; deslizar = rueda; pellizcar = zoom;
 * - tres dedos: toque = clic central; deslizar arriba = vista de tareas,
 *   abajo = escritorio, a los lados = cambiar de ventana.
 *
 * El primer clic espera [MouseGestureTiming.SECOND_TAP_MS] por si viene un
 * segundo toque (así el doble toque puede ser «agarrar» sin un clic suelto
 * delante), igual que en Windows con «tocar dos veces y arrastrar».
 */
@Composable
fun Modifier.mouseGestures(
    out: MouseOut,
    sensitivity: Float,
    inertia: Float,
    gestures: Boolean = true,
    onTouching: (Boolean) -> Unit = {},
    accept: (Offset) -> Boolean = { true }
): Modifier {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val o by rememberUpdatedState(out)
    val sens by rememberUpdatedState(sensitivity)
    val inert by rememberUpdatedState(inertia)
    val multi by rememberUpdatedState(gestures)
    val touching by rememberUpdatedState(onTouching)
    val acceptNow by rememberUpdatedState(accept)
    val m = remember { FloatArray(2) }
    val lastMove = remember { LongArray(1) }
    var isDown by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    // El clic del primer toque, a la espera de un posible segundo
    val pending = remember { arrayOfNulls<Job>(1) }
    val lastTapUp = remember { LongArray(1) }

    fun send(vx: Float, vy: Float) = o.move(vx * sens, vy * sens)
    fun stop() {
        m[0] = 0f; m[1] = 0f
        o.move(0f, 0f)
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            if (isDown) {
                if (SystemClock.uptimeMillis() - lastMove[0] > 24L) {
                    m[0] *= 0.45f; m[1] *= 0.45f
                }
            } else {
                val k = 0.80f + 0.185f * inert.coerceIn(0f, 1f)
                m[0] *= k; m[1] *= k
                if (hypot(m[0], m[1]) < 0.004f) {
                    stop()
                    running = false
                    break
                }
            }
            send(m[0], m[1])
        }
    }

    fun click(index: Int, times: Int = 1) {
        Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP)
        scope.launch {
            repeat(times) {
                o.button(index, true)
                delay(40)
                o.button(index, false)
                if (it < times - 1) delay(60)
            }
        }
    }

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown()
            if (!acceptNow(first.position)) {
                do {
                    val ev = awaitPointerEvent()
                } while (ev.changes.any { it.id == first.id && it.pressed })
                return@awaitEachGesture
            }
            first.consume()
            val t0 = SystemClock.uptimeMillis()
            // ¿Es el segundo toque de un doble toque?
            val second = pending[0]?.isActive == true && t0 - lastTapUp[0] < MouseGestureTiming.SECOND_TAP_MS
            if (second) pending[0]?.cancel()
            pending[0] = null
            m[0] = 0f; m[1] = 0f; lastMove[0] = t0
            isDown = true
            running = true
            touching(true)
            var travel = 0f
            var fingers = 1
            var dragging = false
            var scrollAcc = 0f
            var zoomAcc = 0f
            var swipeX = 0f
            var swipeY = 0f
            var lastSpan = -1f
            var swiped = false

            fun startDrag() {
                if (dragging) return
                dragging = true
                Haptics.tap(view, HapticFeedbackConstants.LONG_PRESS)
                o.button(0, true)
            }

            while (true) {
                // Esperando a mantener: con el dedo quieto, el tiempo también cuenta
                val holdLimit = if (second) MouseGestureTiming.TAP_MS else MouseGestureTiming.HOLD_MS
                val canHold = !dragging && fingers == 1 && travel < MouseGestureTiming.SLOP_DP
                val left = holdLimit - (SystemClock.uptimeMillis() - t0)
                val ev = if (canHold) {
                    if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (ev == null) {
                    startDrag()
                    continue
                }
                val pressed: List<PointerInputChange> = ev.changes.filter { it.pressed }
                fingers = maxOf(fingers, pressed.size)
                when {
                    pressed.size >= 3 -> {
                        m[0] = 0f; m[1] = 0f
                        val dx = pressed.map { it.positionChangeIgnoreConsumed().x }.average().toFloat() / density
                        val dy = pressed.map { it.positionChangeIgnoreConsumed().y }.average().toFloat() / density
                        swipeX += dx; swipeY += dy
                        travel += hypot(dx, dy)
                        if (multi && !swiped && hypot(swipeX, swipeY) > MouseGestureTiming.SWIPE_DP) {
                            val g = if (abs(swipeX) > abs(swipeY)) {
                                if (swipeX > 0) SwipeGesture.RIGHT else SwipeGesture.LEFT
                            } else {
                                if (swipeY > 0) SwipeGesture.DOWN else SwipeGesture.UP
                            }
                            if (o.gesture(g)) Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP)
                            swiped = true
                        }
                    }
                    pressed.size == 2 -> {
                        m[0] = 0f; m[1] = 0f
                        val a = pressed[0].position
                        val b = pressed[1].position
                        val span = (a - b).getDistance() / density
                        val dy = pressed.map { it.positionChangeIgnoreConsumed().y }.average().toFloat()
                        travel += abs(dy) / density
                        val dSpan = if (lastSpan < 0f) 0f else span - lastSpan
                        lastSpan = span
                        // Pellizcar (la distancia cambia más que lo que se desplaza) = zoom
                        if (multi && abs(dSpan) > abs(dy / density) * 1.5f && abs(dSpan) > 0.5f) {
                            zoomAcc += dSpan
                            val step = (zoomAcc / 12f).toInt()
                            if (step != 0) {
                                o.zoom(step * 120)
                                zoomAcc -= step * 12f
                            }
                        } else {
                            scrollAcc += dy
                            val step = scrollAcc.toInt()
                            if (step != 0) {
                                o.wheel(-step * 4)
                                scrollAcc -= step
                            }
                        }
                    }
                    pressed.size == 1 -> {
                        lastSpan = -1f
                        val c = pressed[0]
                        val d = c.positionChangeIgnoreConsumed()
                        val dt = (c.uptimeMillis - c.previousUptimeMillis).coerceAtLeast(1L)
                        travel += d.getDistance() / density
                        // Segundo toque que se mueve: agarrar y arrastrar
                        if (second && fingers == 1 && travel >= MouseGestureTiming.SLOP_DP) startDrag()
                        m[0] += (d.x / density / dt - m[0]) * 0.55f
                        m[1] += (d.y / density / dt - m[1]) * 0.55f
                        lastMove[0] = SystemClock.uptimeMillis()
                    }
                }
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
            isDown = false
            touching(false)
            val now = SystemClock.uptimeMillis()
            val dur = now - t0
            val tap = dur < MouseGestureTiming.TAP_MS && travel < 10f && !swiped
            when {
                dragging -> {
                    o.button(0, false)
                    Haptics.release(view)
                }
                tap && fingers >= 3 -> click(2)
                tap && fingers == 2 -> click(1)
                tap && second -> click(0, times = 2) // doble toque = doble clic
                tap -> {
                    // Un toque: el clic sale si no llega un segundo a tiempo
                    lastTapUp[0] = now
                    pending[0] = scope.launch {
                        delay(MouseGestureTiming.SECOND_TAP_MS)
                        click(0)
                    }
                }
            }
            val moving = now - lastMove[0] < 60 && hypot(m[0], m[1]) > 0.02f
            if (!(inert > 0f && moving) || dragging) {
                stop()
                running = false
            }
        }
    }
}

/**
 * Modo ratón: el dedo que pulsa A o B y se desliza mueve el cursor con el
 * botón mantenido (como pulsar el botón de un touchpad y arrastrar). Mira
 * los dedos ANTES que los botones (no consume nada): mientras haya un clic
 * mantenido, el último dedo que se apoyó es el del botón.
 */
@Composable
fun Modifier.clickButtonDrag(enabled: Boolean, sensitivity: Float): Modifier {
    val sens by rememberUpdatedState(sensitivity)
    if (!enabled) return this
    return this.pointerInput(Unit) {
        val downAt = HashMap<Long, Long>()
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                for (c in ev.changes) {
                    if (c.pressed && !c.previousPressed) downAt[c.id.value] = c.uptimeMillis
                    if (!c.pressed) downAt.remove(c.id.value)
                }
                if (!Kbm.clickHeld) continue
                val latest = ev.changes.filter { it.pressed }.maxByOrNull { downAt[it.id.value] ?: 0L } ?: continue
                val d = latest.positionChangeIgnoreConsumed()
                Kbm.nudge(d.x / density * sens, d.y / density * sens)
            }
        }
    }
}
