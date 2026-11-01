package dev.pepotech.pepomote.control

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Mando físico conectado al móvil (USB-C, Bluetooth o telescópico tipo
 * Kishi / Backbone / GameSir): sus botones, sticks y gatillos entran en
 * [ButtonState] igual que los del táctil, así que viajan al PC (o a
 * RetroArch) por el mismo camino, y el giroscopio del móvil sigue
 * funcionando a la vez. Así no hace falta configurar otro mando en Dolphin
 * ni en Windows.
 *
 * Solo actúa con una pantalla de mando abierta, que dice cómo traducir
 * ([target]) y, en el mando apaisado, qué bit hay en cada posición del rombo
 * ([setFace]): el botón de ABAJO del mando físico es el de abajo en pantalla,
 * sea «A» en Xbox o «B» en Nintendo. Fuera de un mando no se toca nada: el
 * mando sigue sirviendo para moverse por Android.
 */
object PhysicalPad {
    enum class Target {
        /** Ninguna pantalla de mando: los eventos siguen su camino normal. */
        NONE,

        /** GamePad / Pro / Switch / RetroArch / mando universal. */
        PAD,

        /** Mando de Wii (+ Nunchuk): A, B, 1, 2, C, Z y el stick del Nunchuk. */
        WIIMOTE
    }

    @Volatile
    var target: Target = Target.NONE
        set(value) {
            if (field != value) releaseAll()
            field = value
        }

    /** Tecla física del rombo → bit, según la plantilla en pantalla. */
    @Volatile
    private var face: Map<Int, Int> = faceFor(mapOf("top" to ButtonState.X, "left" to ButtonState.Y, "right" to ButtonState.A, "bottom" to ButtonState.B))

    private var keyBits = 0
    private var hatBits = 0
    private var triggerBits = 0
    private var sent = 0
    private var stickL = 0 to 0
    private var stickR = 0 to 0

    /** Las posiciones del rombo de la plantilla (slot → bit). */
    fun setFace(slots: Map<String, Int>) {
        face = faceFor(slots)
    }

    /**
     * Qué posición física va a qué hueco de la plantilla: el de abajo
     * (KEYCODE_BUTTON_A) al de abajo, el de la derecha al de la derecha… y
     * en las consolas de dos o seis botones, lo que RetroArch hace de serie.
     */
    internal fun faceFor(slots: Map<String, Int>): Map<Int, Int> {
        fun pick(vararg names: String): Int? = names.firstNotNullOfOrNull { slots[it] }
        val hasBottom = "bottom" in slots
        val out = HashMap<Int, Int>()
        (if (hasBottom) pick("bottom") else pick("left", "bl", "l", "center"))?.let { out[KeyEvent.KEYCODE_BUTTON_A] = it }
        pick("right", "bm", "m")?.let { out[KeyEvent.KEYCODE_BUTTON_B] = it }
        (if (hasBottom) pick("left") else pick("tl"))?.let { out[KeyEvent.KEYCODE_BUTTON_X] = it }
        pick("top", "tm", "r")?.let { out[KeyEvent.KEYCODE_BUTTON_Y] = it }
        return out
    }

    fun isFromPad(source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    /** Bit de una tecla del mando físico, o 0 si no se usa. */
    internal fun bitFor(keyCode: Int, t: Target): Int {
        val common = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> ButtonState.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> ButtonState.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> ButtonState.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> ButtonState.DPAD_RIGHT
            KeyEvent.KEYCODE_BUTTON_START -> ButtonState.PLUS
            KeyEvent.KEYCODE_BUTTON_SELECT -> ButtonState.MINUS
            KeyEvent.KEYCODE_BUTTON_MODE -> ButtonState.HOME
            else -> 0
        }
        if (common != 0) return common
        return when (t) {
            Target.NONE -> 0
            Target.PAD -> when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> face[keyCode] ?: 0
                KeyEvent.KEYCODE_BUTTON_L1 -> ButtonState.L
                KeyEvent.KEYCODE_BUTTON_R1 -> ButtonState.R
                KeyEvent.KEYCODE_BUTTON_L2 -> ButtonState.ZL
                KeyEvent.KEYCODE_BUTTON_R2 -> ButtonState.ZR
                KeyEvent.KEYCODE_BUTTON_THUMBL -> ButtonState.STICK_L
                KeyEvent.KEYCODE_BUTTON_THUMBR -> ButtonState.STICK_R
                else -> 0
            }
            Target.WIIMOTE -> when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> ButtonState.A
                KeyEvent.KEYCODE_BUTTON_B -> ButtonState.B
                KeyEvent.KEYCODE_BUTTON_X -> ButtonState.ONE
                KeyEvent.KEYCODE_BUTTON_Y -> ButtonState.TWO
                KeyEvent.KEYCODE_BUTTON_L1 -> ButtonState.C
                KeyEvent.KEYCODE_BUTTON_L2 -> ButtonState.Z
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> ButtonState.B
                else -> 0
            }
        }
    }

    /** @return true si la tecla era del mando físico y se ha usado. */
    fun onKey(context: Context, event: KeyEvent): Boolean {
        val t = target
        if (t == Target.NONE || !isFromPad(event.source) || !AppPrefs.physicalPad(context)) return false
        val bit = bitFor(event.keyCode, t)
        if (bit == 0) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                keyBits = keyBits or bit
                flush()
            }
            KeyEvent.ACTION_UP -> {
                keyBits = keyBits and bit.inv()
                flush()
            }
        }
        return true
    }

    /** Sticks, cruceta de ejes (hat) y gatillos analógicos. */
    fun onMotion(context: Context, event: MotionEvent): Boolean {
        val t = target
        if (t == Target.NONE || !isFromPad(event.source) || event.action != MotionEvent.ACTION_MOVE) return false
        if (!AppPrefs.physicalPad(context)) return false
        val dev = event.device

        val l = stick(event, dev, MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
        if (l != stickL) {
            stickL = l
            ButtonState.setStick(l.first, l.second)
        }
        if (t == Target.PAD) {
            val useZ = dev?.getMotionRange(MotionEvent.AXIS_Z, event.source) != null
            val r = if (useZ) {
                stick(event, dev, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
            } else {
                stick(event, dev, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY)
            }
            if (r != stickR) {
                stickR = r
                ButtonState.setStick2(r.first, r.second)
            }
        }

        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        var hat = 0
        if (hx < -0.5f) hat = hat or ButtonState.DPAD_LEFT
        if (hx > 0.5f) hat = hat or ButtonState.DPAD_RIGHT
        if (hy < -0.5f) hat = hat or ButtonState.DPAD_UP
        if (hy > 0.5f) hat = hat or ButtonState.DPAD_DOWN
        hatBits = hat

        val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        val ltBit = if (t == Target.PAD) ButtonState.ZL else ButtonState.Z
        val rtBit = if (t == Target.PAD) ButtonState.ZR else ButtonState.B
        triggerBits = trigger(triggerBits, ltBit, lt)
        triggerBits = trigger(triggerBits, rtBit, rt)
        flush()
        return true
    }

    /** Con histéresis: pulsa a más del 40 % y suelta por debajo del 25 %. */
    private fun trigger(bits: Int, bit: Int, v: Float): Int = when {
        v > 0.40f -> bits or bit
        v < 0.25f -> bits and bit.inv()
        else -> bits
    }

    private fun stick(event: MotionEvent, dev: InputDevice?, ax: Int, ay: Int): Pair<Int, Int> {
        val x = event.getAxisValue(ax)
        val y = event.getAxisValue(ay)
        val flat = maxOf(
            dev?.getMotionRange(ax, event.source)?.flat ?: 0f,
            dev?.getMotionRange(ay, event.source)?.flat ?: 0f,
            MIN_FLAT
        )
        // StickMap: y hacia abajo de entrada, +y arriba de salida (como el táctil)
        return StickMap.map(x, y, 1f, flat.coerceAtMost(0.5f))
    }

    private fun flush() {
        val now = keyBits or hatBits or triggerBits
        val changed = now xor sent
        if (changed == 0) return
        var b = 1
        while (b != 0) {
            if (changed and b != 0) ButtonState.set(b, now and b != 0)
            b = b shl 1
        }
        sent = now
    }

    /** Nada se queda pulsado al salir del mando o cambiar de modo. */
    fun releaseAll() {
        keyBits = 0
        hatBits = 0
        triggerBits = 0
        flush()
        if (stickL != (0 to 0)) ButtonState.setStick(0, 0)
        if (stickR != (0 to 0)) ButtonState.setStick2(0, 0)
        stickL = 0 to 0
        stickR = 0 to 0
    }

    /** Nombres de los mandos físicos conectados ahora (para Ajustes). */
    fun connectedNames(context: Context): List<String> {
        val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return emptyList()
        return im.inputDeviceIds.mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual && isFromPad(it.sources) }
            .map { it.name }
            .distinct()
    }

    private const val MIN_FLAT = 0.10f
}
