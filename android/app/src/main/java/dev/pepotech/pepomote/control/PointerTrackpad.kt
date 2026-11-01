package dev.pepotech.pepomote.control

/**
 * Mover el ratón del PC con el dedo (modo puntero), como el trackpad de un
 * portátil, sin cambiar nada en el PC.
 *
 * Cómo: el receptor del PC, cuando un paquete NO trae orientación absoluta
 * (sin FLAG_QUAT_VALID), mueve el cursor de forma RELATIVA integrando el giro
 * que llega (dx = −gz·dt·px/rad, dy = −gx·dt·px/rad, con una zona muerta
 * suave de 0,03 rad/s). Así que, con el trackpad activo, el móvil manda un
 * giro sintético sacado de la velocidad del dedo y sin orientación: el
 * cursor se mueve con el dedo y mover el móvil no hace nada.
 */
object PointerTrackpad {
    /** Zona muerta suave del receptor (rad/s): se compensa para que no se coma los movimientos lentos. */
    private const val PC_DEADZONE = 0.03f

    /** rad/s por dp/ms con sensibilidad 1 (≈ 2 px de cursor por dp de dedo en 1080p con 40°). */
    private const val GAIN = 0.75f

    /** Lo pone la pantalla: el modo puntero con el trackpad encendido. */
    @Volatile
    var active: Boolean = false
        set(value) {
            field = value
            if (!value) setVelocity(0f, 0f)
        }

    @Volatile
    var sensitivity: Float = 1f

    @Volatile
    private var gx = 0f

    @Volatile
    private var gz = 0f

    /** Velocidad del dedo en dp/ms (+x derecha, +y abajo, como en pantalla). */
    fun setVelocity(vxDp: Float, vyDp: Float) {
        gz = toGyro(vxDp)
        gx = toGyro(vyDp)
    }

    internal fun toGyro(v: Float): Float {
        if (v == 0f) return 0f
        val rate = -v * GAIN * sensitivity
        return rate + (if (rate > 0f) PC_DEADZONE else -PC_DEADZONE)
    }

    /** Escribe el giro sintético en [out] (marco del paquete). */
    fun fill(out: FloatArray) {
        out[0] = gx
        out[1] = 0f
        out[2] = gz
    }
}
