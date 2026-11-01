package dev.pepotech.pepomote.control

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Del desplazamiento del pulgar sobre el stick virtual (px, y hacia ABAJO como
 * en pantalla) al stick del Nunchuk (−127..127, +y ARRIBA). Zona muerta pequeña
 * en el centro, reescalada para que el borde de la zona muerta sea el 0 y el
 * radio el tope: sin escalón al salir del centro. Puro, sin Android: testeable.
 *
 * - [deadzone]: fracción del radio que se ignora en el centro (0..0.9).
 * - [antiDeadzone]: salida mínima en cuanto se sale de la zona muerta, para
 *   vencer la zona muerta interna del juego (0..0.8).
 * - [sensitivity]: ganancia; con más de 1 se llega al tope antes de tocar el
 *   borde, con menos de 1 el tope no se alcanza nunca (0.2..3).
 */
object StickMap {
    /** Fracción del radio que se ignora en el centro. */
    const val DEAD_ZONE = 0.08f

    /** @return (x, y) del stick; (0, 0) dentro de la zona muerta. */
    fun map(
        dx: Float,
        dy: Float,
        radius: Float,
        deadzone: Float = DEAD_ZONE,
        antiDeadzone: Float = 0f,
        sensitivity: Float = 1f
    ): Pair<Int, Int> {
        val len = hypot(dx, dy)
        if (radius <= 0f || len <= 0f) return 0 to 0
        val m = len / radius
        val dz = deadzone.coerceIn(0f, 0.9f)
        if (m <= dz) return 0 to 0
        val raw = ((min(m, 1f) - dz) / (1f - dz)).coerceIn(0f, 1f)
        val gained = (raw * sensitivity.coerceIn(0.2f, 3f)).coerceAtMost(1f)
        val adz = antiDeadzone.coerceIn(0f, 0.8f)
        val scaled = if (adz > 0f) adz + gained * (1f - adz) else gained
        val x = (dx / len * scaled * 127f).roundToInt().coerceIn(-127, 127)
        val y = (-dy / len * scaled * 127f).roundToInt().coerceIn(-127, 127)
        return x to y
    }
}
