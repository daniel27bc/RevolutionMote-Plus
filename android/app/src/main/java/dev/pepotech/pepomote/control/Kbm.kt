package dev.pepotech.pepomote.control

import dev.pepotech.pepomote.service.LinkState
import org.json.JSONArray
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Teclado y ratón del móvil, vayan por donde vayan:
 *
 * - [Backend.APP]: al receptor de RevolutionMote+ por el canal de control
 *   (mensaje `kbm`), a la vez que el mando si lo hay.
 * - [Backend.BT]: como ratón y teclado Bluetooth de verdad ([BtHid]).
 * - [Backend.POINTER]: el receptor original de PepoTech en modo puntero
 *   (giro sintético, A = clic izquierdo, B = derecho; sin teclas sueltas).
 *
 * El movimiento llega como VELOCIDADES (los touchpads en dp/ms, el giro en
 * rad/s) y aquí se integra cada pocos milisegundos en píxeles enteros: el
 * cursor se mueve en relativo, como un ratón, y nunca se descentra.
 */
object Kbm {
    /**
     * Presentar: los botones de volumen del móvil pasan diapositivas
     * (MainActivity los desvía mientras esté puesto).
     */
    @Volatile
    var slideKeys = false

    const val SLIDE_NEXT = "PageDown"
    const val SLIDE_PREV = "PageUp"

    enum class Backend { NONE, APP, POINTER, BT }

    /** Píxeles de cursor por dp de dedo (con sensibilidad 1). */
    const val PX_PER_DP = 2.2f

    /** Píxeles de cursor por radián de giro (con sensibilidad 1). */
    const val PX_PER_RAD = 900f

    /** Las dos superficies que pueden mover el ratón a la vez. */
    const val SRC_PAD = 0
    const val SRC_FREE = 1

    /** El dedo que mantiene A o B en modo ratón (arrastrar pulsando). */
    const val SRC_BTN = 2

    private const val TICK_MS = 8L

    @Volatile
    var backend: Backend = Backend.NONE
        set(value) {
            if (field == value) return
            releaseAll()
            field = value
            if (value == Backend.APP || value == Backend.BT) wake() else stopTicker()
        }

    private val touch = FloatArray(6)

    @Volatile
    private var yaw = 0f

    @Volatile
    private var pitch = 0f

    private var accX = 0f
    private var accY = 0f
    private var lastTickNs = 0L
    private val lock = Any()
    private var pending = JSONArray()
    private val heldKeys = HashSet<String>()
    private val heldButtons = BooleanArray(3)

    private var exec: ScheduledExecutorService? = null
    private var tick: ScheduledFuture<*>? = null

    /** Velocidad de un dedo (dp/ms, +y abajo), ya con su sensibilidad. */
    fun setTouch(src: Int, vx: Float, vy: Float) {
        if (backend == Backend.POINTER) {
            touch[src * 2] = vx
            touch[src * 2 + 1] = vy
            PointerTrackpad.setVelocity(touch[0] + touch[2] + touch[4], touch[1] + touch[3] + touch[5])
            return
        }
        touch[src * 2] = vx
        touch[src * 2 + 1] = vy
        if (vx != 0f || vy != 0f) wake()
    }

    /** Giro del móvil como ratón: [yawRate] (+ = a la izquierda), [pitchRate] (+ = arriba), ya con su sensibilidad. */
    fun setGyro(yawRate: Float, pitchRate: Float) {
        if (backend == Backend.POINTER) {
            PointerTrackpad.setGyro(yawRate, pitchRate)
            return
        }
        yaw = yawRate
        pitch = pitchRate
        if (yawRate != 0f || pitchRate != 0f) wake()
    }

    private var nudgeX = 0f
    private var nudgeY = 0f

    /** Movimiento directo en dp de dedo (ya con su sensibilidad): arrastrar con A o B pulsados. */
    fun nudge(dxDp: Float, dyDp: Float) {
        if (dxDp == 0f && dyDp == 0f) return
        if (backend == Backend.APP || backend == Backend.BT) {
            synchronized(lock) {
                nudgeX += dxDp
                nudgeY += dyDp
            }
            wake()
        }
    }

    /** ¿Hay un clic mantenido ahora mismo (izquierdo o derecho)? */
    val clickHeld: Boolean get() = synchronized(lock) { heldButtons[0] || heldButtons[1] }

    /** Atajo de teclado: pulsa [keys] en orden y las suelta al revés (Alt+Tab, Win+D…). */
    fun combo(vararg keys: String): Boolean {
        if (!keysAvailable) return false
        for (k in keys) key(k, true)
        for (k in keys.reversed()) key(k, false)
        return true
    }

    /** Rueda con Ctrl pulsado: el zoom de casi cualquier programa. */
    fun zoom(units: Int) {
        if (units == 0 || !keysAvailable) return
        key("ControlLeft", true)
        wheel(units)
        key("ControlLeft", false)
    }

    /** Botón del ratón: 0 izquierdo, 1 derecho, 2 central. */
    fun button(index: Int, down: Boolean) {
        if (index !in 0..2) return
        when (backend) {
            Backend.NONE -> return
            Backend.POINTER -> when (index) {
                0 -> ButtonState.set(ButtonState.A, down)
                1 -> ButtonState.set(ButtonState.B, down)
            }
            Backend.APP -> queue(JSONArray().put("btn").put(index).put(down))
            Backend.BT -> BtHid.mouseButton(index, down).also { wake() }
        }
        Diag.kbmEvents.incrementAndGet()
        synchronized(lock) { heldButtons[index] = down }
    }

    /** Rueda en 1/120 de muesca (+ = hacia delante, scroll arriba). */
    fun wheel(units: Int) {
        if (units == 0) return
        when (backend) {
            Backend.NONE -> Unit
            Backend.POINTER -> ButtonState.addScroll(units / 4)
            Backend.APP -> queue(JSONArray().put("wh").put(units))
            Backend.BT -> BtHid.wheel(units).also { wake() }
        }
    }

    /** Tecla por nombre (`KeyboardEvent.code`: «KeyW», «Escape», «F5»…). */
    fun key(name: String, down: Boolean) {
        when (backend) {
            Backend.NONE, Backend.POINTER -> return
            Backend.APP -> queue(JSONArray().put("key").put(name).put(down))
            Backend.BT -> BtHid.key(name, down).also { wake() }
        }
        Diag.kbmEvents.incrementAndGet()
        synchronized(lock) { if (down) heldKeys += name else heldKeys -= name }
    }

    /** Pulsar y soltar una tecla. */
    fun tap(name: String) {
        key(name, true)
        key(name, false)
    }

    /** Un texto entero (el receptor o el teclado Bluetooth lo teclean). */
    fun text(s: String) {
        if (s.isEmpty()) return
        when (backend) {
            Backend.NONE -> Unit
            Backend.APP, Backend.POINTER -> LinkState.sendText?.invoke(s)
            Backend.BT -> BtHid.type(s).also { wake() }
        }
    }

    /**
     * Recentrar: el cursor al centro de la pantalla (receptor de
     * RevolutionMote+) o el recentrado del puntero (receptor original).
     */
    fun center() {
        when (backend) {
            Backend.APP -> queue(JSONArray().put("ctr"))
            Backend.POINTER -> ButtonState.bumpRecenter()
            else -> Unit
        }
    }

    /** ¿Hay teclas sueltas (WASD, Esc…)? En modo puntero, no. */
    val keysAvailable: Boolean get() = backend == Backend.APP || backend == Backend.BT

    /** Nada se queda pulsado ni moviéndose. */
    fun releaseAll() {
        touch.fill(0f)
        yaw = 0f
        pitch = 0f
        val buttons = synchronized(lock) {
            val b = heldButtons.copyOf()
            heldKeys.clear()
            heldButtons.fill(false)
            b
        }
        when (backend) {
            Backend.APP -> {
                queue(JSONArray().put("rel"))
                flush()
            }
            Backend.BT -> BtHid.releaseAll()
            Backend.POINTER -> {
                if (buttons[0]) ButtonState.set(ButtonState.A, false)
                if (buttons[1]) ButtonState.set(ButtonState.B, false)
                PointerTrackpad.setVelocity(0f, 0f)
                PointerTrackpad.setGyro(0f, 0f)
            }
            Backend.NONE -> Unit
        }
    }

    private fun queue(ev: JSONArray) {
        synchronized(lock) { pending.put(ev) }
        wake()
    }

    private fun flush() {
        val batch = synchronized(lock) {
            if (pending.length() == 0) return
            val b = pending
            pending = JSONArray()
            b
        }
        Diag.kbmBatches.incrementAndGet()
        LinkState.sendKbm?.invoke(batch)
    }

    /** Ticks seguidos sin nada que hacer; al llegar a [IDLE_TICKS] el reloj se duerme. */
    private var idleTicks = 0
    private const val IDLE_TICKS = 40

    /** ¿Está el reloj en marcha? (para el diagnóstico y los tests) */
    val ticking: Boolean get() = tick != null

    /** Hay movimiento o algo por mandar: el reloj se pone en marcha si dormía. */
    @Synchronized
    private fun wake() {
        idleTicks = 0
        if (tick == null && (backend == Backend.APP || backend == Backend.BT)) startTicker()
    }

    /** Mismo cerrojo que [wake]: nada puede colarse entre comprobar y parar. */
    @Synchronized
    private fun sleepIfIdle() {
        if (idleTicks < IDLE_TICKS) return
        if (synchronized(lock) { pending.length() } != 0) return
        if (backend == Backend.BT && BtHid.pending) return
        stopTicker()
    }

    @Synchronized
    private fun startTicker() {
        if (tick != null) return
        val e = exec ?: Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "kbm").apply { isDaemon = true } }.also { exec = it }
        lastTickNs = System.nanoTime()
        tick = e.scheduleAtFixedRate({ runCatching { onTick() } }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun stopTicker() {
        tick?.cancel(false)
        tick = null
        accX = 0f
        accY = 0f
    }

    /** Integra las velocidades en píxeles y manda lo pendiente. */
    private fun onTick() {
        val now = System.nanoTime()
        val dtMs = ((now - lastTickNs) / 1_000_000f).coerceIn(0f, 50f)
        lastTickNs = now
        // El giro ya llega con la precisión aplicada ([GyroAim]); los dedos, aquí
        val fine = Precision.factor
        val (nx, ny) = synchronized(lock) {
            val n = nudgeX to nudgeY
            nudgeX = 0f
            nudgeY = 0f
            n
        }
        accX += ((touch[0] + touch[2] + touch[4]) * dtMs + nx) * fine * PX_PER_DP - yaw * dtMs / 1000f * PX_PER_RAD
        accY += ((touch[1] + touch[3] + touch[5]) * dtMs + ny) * fine * PX_PER_DP - pitch * dtMs / 1000f * PX_PER_RAD
        val dx = accX.toInt()
        val dy = accY.toInt()
        if (dx != 0 || dy != 0) {
            accX -= dx
            accY -= dy
            Diag.mousePixels.addAndGet((kotlin.math.abs(dx) + kotlin.math.abs(dy)).toLong())
            when (backend) {
                Backend.APP -> synchronized(lock) { pending.put(JSONArray().put("mv").put(dx).put(dy)) }
                Backend.BT -> BtHid.move(dx, dy)
                else -> Unit
            }
        }
        if (backend == Backend.APP) flush()
        if (backend == Backend.BT) BtHid.pump()
        // Sin dedos, sin giro y sin nada pendiente: a dormir (batería). Lo
        // que llegue después lo despierta con [wake].
        val still = touch.all { it == 0f } && yaw == 0f && pitch == 0f
        val empty = synchronized(lock) { pending.length() == 0 } && !(backend == Backend.BT && BtHid.pending)
        if (still && empty) {
            if (++idleTicks >= IDLE_TICKS) sleepIfIdle()
        } else {
            idleTicks = 0
        }
    }

    /** Para los tests: dp/ms durante [ms] → píxeles. */
    internal fun pixelsFor(vDpPerMs: Float, ms: Float): Int = (vDpPerMs * ms * PX_PER_DP).roundToInt()
}
