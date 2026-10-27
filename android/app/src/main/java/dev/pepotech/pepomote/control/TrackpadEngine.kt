package dev.pepotech.pepomote.control

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Convierte el desplazamiento suave del dedo (dx, dy en píxeles de pantalla)
 * en deflexión de stick analógico (−127..127, +x derecha, +y arriba) simulando
 * un ratón / trackpad ("Mouse-Joystick").
 */
object TrackpadEngine {
    fun processDelta(
        dx: Float,
        dy: Float,
        sensitivity: Float = 1.0f,
        antiDeadzone: Float = 0.15f
    ): Pair<Int, Int> {
        val len = hypot(dx, dy)
        if (len < 0.3f) return 0 to 0

        val dirX = dx / len
        val dirY = -dy / len

        val sens = sensitivity.coerceIn(0.2f, 4.0f)
        val speed = min(1.0f, (len * sens) / 25.0f)

        val adz = antiDeadzone.coerceIn(0.0f, 0.6f)
        val power = adz + speed * (1.0f - adz)

        val x = (dirX * power * 127f).roundToInt().coerceIn(-127, 127)
        val y = (dirY * power * 127f).roundToInt().coerceIn(-127, 127)
        return x to y
    }
}
