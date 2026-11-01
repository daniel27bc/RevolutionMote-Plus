package dev.pepotech.pepomote.control

import dev.pepotech.pepomote.sensor.Frame
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Apuntar con el giroscopio en el propio móvil (mando universal): el giro se
 * convierte aquí en stick derecho, en vez de mandarlo crudo al PC.
 *
 * Giro horizontal "player space" (como Steam Input / JoyShockMapper): se
 * mide alrededor de la VERTICAL real (la gravedad), así que girar la muñeca a
 * izquierda o derecha mueve la cámara en horizontal tanto si sujetas el móvil
 * de pie como tumbado o inclinado. El vertical es inclinar el borde de arriba
 * hacia ti o hacia fuera.
 *
 * Velocidad de giro = deflexión del stick (como un ratón): girar despacio,
 * microajustes; girar rápido, giro rápido. Con su zona muerta mínima (el
 * temblor del pulso no mueve nada), suavizado y anti-zona muerta.
 */
object GyroAim {
    /** Giro (rad/s) que da el tope con sensibilidad 1. */
    const val FULL_RATE = 2.2f

    /** Por debajo de esto (fracción del tope) se considera pulso quieto. */
    const val REST = 0.012f

    /** Cuánto se relaja el giro horizontal combinado (JoyShockMapper). */
    private const val YAW_RELAX = 1.41f

    @Volatile
    var config: GyroConfig = GyroConfig()

    /** Lo pone la pantalla del mando universal con el modo confirmado. */
    @Volatile
    var active: Boolean = false
        set(value) {
            field = value
            if (!value) {
                buttonHeld = false
                zero()
            }
        }

    /** El botón 🎯 mantenido (modo «mantener botón»). */
    @Volatile
    var buttonHeld: Boolean = false

    /** El botón 🎯 alternado (modo «alternar con botón»). */
    @Volatile
    var toggled: Boolean = false

    private val g = FloatArray(3)
    private val a = FloatArray(3)
    private var ux = 0f
    private var uy = 1f
    private var uz = 0f
    private var hasUp = false
    private var sx = 0f
    private var sy = 0f
    private var lastX = 0
    private var lastY = 0

    /** Si el giro debe apuntar ahora mismo, según cómo se activa. */
    fun gateOpen(c: GyroConfig = config): Boolean = c.enabled && when (c.activation) {
        GyroActivation.ALWAYS -> true
        GyroActivation.HOLD_LT -> ButtonState.current() and ButtonState.ZL != 0
        GyroActivation.HOLD_BUTTON -> buttonHeld
        GyroActivation.TOGGLE_BUTTON -> toggled
        GyroActivation.TOGGLE_LT -> ltToggled
    }

    /** Modo «alternar con LT / ZL»: cada pulsación (flanco de bajada) lo cambia. */
    @Volatile
    var ltToggled: Boolean = false
    private var ltWasDown = false

    private fun trackLt() {
        val down = ButtonState.current() and ButtonState.ZL != 0
        if (down && !ltWasDown) ltToggled = !ltToggled
        ltWasDown = down
    }

    /** Una muestra del giroscopio (hilo de sensores). */
    fun onSample(gyro: FloatArray, accel: FloatArray, rotation: Int) {
        if (!active) return
        Frame.remapGyro(gyro, rotation, g)
        Frame.remapAccel(accel, rotation, a)
        // La vertical: el acelerómetro filtrado (en reposo apunta hacia arriba)
        val an = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        if (an > 1f) {
            val k = if (hasUp) 0.04f else 1f
            ux += (a[0] / an - ux) * k
            uy += (a[1] / an - uy) * k
            uz += (a[2] / an - uz) * k
            hasUp = true
        }
        val c = config
        if (c.enabled && c.activation == GyroActivation.TOGGLE_LT) trackLt()
        if (!gateOpen(c)) {
            sx = 0f
            sy = 0f
            zero()
            return
        }
        val (yaw0, pitch0) = playerSpace(g[0], g[1], g[2], ux, uy, uz)
        val yaw = if (c.swapAxes) pitch0 else yaw0
        val pitch = if (c.swapAxes) yaw0 else pitch0
        var tx = -yaw * c.sensX / FULL_RATE
        var ty = pitch * c.sensY / FULL_RATE
        if (c.invertX) tx = -tx
        if (c.invertY) ty = -ty
        val alpha = (1f - c.smoothing).coerceIn(0.1f, 1f)
        sx += (tx - sx) * alpha
        sy += (ty - sy) * alpha
        val (ox, oy) = toStick(sx, sy, c.antiDeadzone)
        if (ox != lastX || oy != lastY) {
            lastX = ox
            lastY = oy
            ButtonState.setStick2From(ButtonState.SRC_GYRO, ox, oy)
        }
    }

    private fun zero() {
        if (lastX != 0 || lastY != 0) {
            lastX = 0
            lastY = 0
            ButtonState.setStick2From(ButtonState.SRC_GYRO, 0, 0)
        }
    }

    /**
     * (yaw, pitch) en rad/s. Marco del mando apaisado: x a la derecha de la
     * pantalla, y hacia arriba de la pantalla, z hacia ti; (ux, uy, uz) la
     * vertical hacia arriba. yaw > 0 = girar a la izquierda; pitch > 0 = el
     * borde de arriba hacia ti (apuntar arriba).
     */
    internal fun playerSpace(gx: Float, gy: Float, gz: Float, ux: Float, uy: Float, uz: Float): Pair<Float, Float> {
        val worldYaw = gy * uy + gz * uz
        val mag = hypot(gy, gz)
        val yaw = sign(worldYaw) * min(abs(worldYaw) * YAW_RELAX, mag)
        return yaw to gx
    }

    /** Fracción del tope (−∞..∞ por eje) → stick −127..127, con reposo y anti-zona muerta. */
    internal fun toStick(x: Float, y: Float, antiDeadzone: Float): Pair<Int, Int> {
        val m = hypot(x, y)
        if (m < REST) return 0 to 0
        val adz = antiDeadzone.coerceIn(0f, 0.8f)
        val out = adz + min(1f, (m - REST) / (1f - REST)) * (1f - adz)
        val px = (x / m * out * 127f).roundToInt().coerceIn(-127, 127)
        val py = (y / m * out * 127f).roundToInt().coerceIn(-127, 127)
        return px to py
    }
}
