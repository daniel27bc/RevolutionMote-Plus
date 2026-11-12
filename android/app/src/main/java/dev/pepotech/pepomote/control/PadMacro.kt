package dev.pepotech.pepomote.control

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Macros del mando universal: una secuencia de pulsaciones que se lanza con
 * un solo botón.
 *
 * Se guarda como texto, un paso por palabra: «A» (un botón), «A+B» (a la
 * vez), «key:KeyW» (una tecla), «150» (esperar 150 ms). Cada pulsación se
 * mantiene [HOLD_MS] y deja [GAP_MS] antes de la siguiente, para que el
 * juego vea cada una.
 */
object PadMacro {
    const val HOLD_MS = 70
    const val GAP_MS = 50
    const val MAX_STEPS = 24
    const val MAX_WAIT_MS = 3000
    const val MAX_SLOTS = 8

    /** Nombre en el texto de la macro → bit del mando. */
    val BUTTONS: List<Pair<String, Int>> = listOf(
        "A" to ButtonState.A, "B" to ButtonState.B, "X" to ButtonState.X, "Y" to ButtonState.Y,
        "LB" to ButtonState.L, "RB" to ButtonState.R, "LT" to ButtonState.ZL, "RT" to ButtonState.ZR,
        "L3" to ButtonState.STICK_L, "R3" to ButtonState.STICK_R,
        "UP" to ButtonState.DPAD_UP, "DOWN" to ButtonState.DPAD_DOWN,
        "LEFT" to ButtonState.DPAD_LEFT, "RIGHT" to ButtonState.DPAD_RIGHT,
        "START" to ButtonState.PLUS, "SELECT" to ButtonState.MINUS
    )

    /** Un paso: pulsar [bits] y [keys] a la vez, o (si [waitMs] > 0) esperar. */
    data class Step(val bits: Int = 0, val keys: List<String> = emptyList(), val waitMs: Int = 0) {
        val isWait: Boolean get() = waitMs > 0
    }

    /** Un flanco en el tiempo: en [atMs], pulsar o soltar. */
    data class Action(val atMs: Int, val bits: Int, val keys: List<String>, val down: Boolean)

    fun parse(spec: String): List<Step> {
        val out = ArrayList<Step>()
        for (token in spec.trim().split(Regex("\\s+"))) {
            if (token.isEmpty() || out.size >= MAX_STEPS) continue
            val wait = token.toIntOrNull()
            if (wait != null) {
                if (wait > 0) out += Step(waitMs = wait.coerceAtMost(MAX_WAIT_MS))
                continue
            }
            var bits = 0
            val keys = ArrayList<String>()
            for (part in token.split('+')) {
                if (part.startsWith("key:")) {
                    val name = part.removePrefix("key:")
                    if (name.isNotEmpty() && name.all { it.isLetterOrDigit() } && name !in keys) keys += name
                } else {
                    BUTTONS.firstOrNull { it.first == part }?.let { bits = bits or it.second }
                }
            }
            if (bits != 0 || keys.isNotEmpty()) out += Step(bits, keys)
        }
        return out
    }

    fun format(steps: List<Step>): String = steps.joinToString(" ") { s ->
        if (s.isWait) s.waitMs.toString()
        else (BUTTONS.filter { s.bits and it.second != 0 }.map { it.first } + s.keys.map { "key:$it" }).joinToString("+")
    }

    /** Cuándo se pulsa y se suelta cada cosa. */
    fun plan(steps: List<Step>): List<Action> {
        val out = ArrayList<Action>()
        var t = 0
        for (s in steps) {
            if (s.isWait) {
                t += s.waitMs
                continue
            }
            out += Action(t, s.bits, s.keys, true)
            t += HOLD_MS
            out += Action(t, s.bits, s.keys, false)
            t += GAP_MS
        }
        return out
    }

    /** El primer hueco libre (M1, M2…) o null si ya están todos. */
    fun freeSlot(used: Set<String>): String? = (1..MAX_SLOTS).map { "M$it" }.firstOrNull { it !in used }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "pad-macro").apply { isDaemon = true } }
    private val generation = AtomicInteger(0)

    /** Lanza la macro; si había otra en marcha, la corta. */
    fun run(spec: String) {
        val actions = plan(parse(spec))
        if (actions.isEmpty()) return
        val mine = generation.incrementAndGet()
        worker.execute {
            val start = System.nanoTime()
            var heldBits = 0
            val heldKeys = ArrayList<String>()
            try {
                for (a in actions) {
                    val wait = a.atMs - (System.nanoTime() - start) / 1_000_000
                    if (wait > 0) Thread.sleep(wait)
                    if (generation.get() != mine) break
                    apply(a.bits, a.keys, a.down)
                    if (a.down) {
                        heldBits = heldBits or a.bits
                        heldKeys += a.keys
                    } else {
                        heldBits = heldBits and a.bits.inv()
                        heldKeys -= a.keys.toSet()
                    }
                }
            } catch (_: InterruptedException) {
            } finally {
                // Cortada a medias: nada se queda pulsado
                if (heldBits != 0 || heldKeys.isNotEmpty()) apply(heldBits, heldKeys, false)
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
    }

    private fun apply(bits: Int, keys: List<String>, down: Boolean) {
        var rest = bits
        while (rest != 0) {
            val bit = Integer.lowestOneBit(rest)
            ButtonState.setDirect(bit, down)
            rest = rest and bit.inv()
        }
        for (k in keys) Kbm.key(k, down)
    }
}

/**
 * Capa «Shift» del mando universal: con el botón Shift mantenido, un botón
 * suelto envía su segunda función. Lo que se pulsó con Shift se suelta como
 * tal aunque Shift se levante antes.
 */
object PadShift {
    @Volatile
    var held = false

    /** bit del botón → bit que envía con Shift. */
    @Volatile
    var map: Map<Int, Int> = emptyMap()

    private val pressedAs = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun route(bit: Int, down: Boolean): Int {
        if (down) {
            val target = if (held) map[bit] ?: bit else bit
            if (target != bit) pressedAs[bit] = target else pressedAs.remove(bit)
            return target
        }
        return pressedAs.remove(bit) ?: bit
    }

    fun clear() {
        held = false
        map = emptyMap()
        pressedAs.clear()
    }
}
