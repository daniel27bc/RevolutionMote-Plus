package dev.pepotech.pepomote.control

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Modo trackpad ("Mouse-Joystick"): la VELOCIDAD del dedo (dp por
 * milisegundo) se convierte en deflexión del stick (−127..127, +x derecha,
 * +y arriba). Deslizar rápido gira rápido; despacio, microajustes. Con el
 * dedo quieto la velocidad es 0 y el stick vuelve al centro: sin deriva.
 * Puro, sin Android: testeable.
 */
object TrackpadEngine {
    /** Velocidad (dp/ms) que da el tope con sensibilidad 1. */
    const val FULL_SPEED = 0.45f

    /** Por debajo de esto (dp/ms) el dedo se considera quieto. */
    const val REST_SPEED = 0.004f

    fun fromVelocity(
        vx: Float,
        vy: Float,
        sensitivity: Float = 1f,
        antiDeadzone: Float = 0.15f
    ): Pair<Int, Int> {
        val speed = hypot(vx, vy)
        if (speed < REST_SPEED) return 0 to 0
        val sens = sensitivity.coerceIn(0.1f, 10f)
        val mag = min(1f, speed * sens / FULL_SPEED)
        val adz = antiDeadzone.coerceIn(0f, 0.9f)
        val power = adz + mag * (1f - adz)
        val x = (vx / speed * power * 127f).roundToInt().coerceIn(-127, 127)
        val y = (-vy / speed * power * 127f).roundToInt().coerceIn(-127, 127)
        return x to y
    }
}
