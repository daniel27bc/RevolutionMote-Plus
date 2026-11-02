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
            if (!value) {
                setVelocity(0f, 0f)
                setGyro(0f, 0f)
            }
        }

    @Volatile
    var sensitivity: Float = 1f

    @Volatile
    private var gx = 0f

    @Volatile
    private var gz = 0f

    // Lo que aporta el giro del móvil (modo ratón), ya en rad/s sintéticos
    @Volatile
    private var gyroGx = 0f

    @Volatile
    private var gyroGz = 0f

    /** Velocidad del dedo en dp/ms (+x derecha, +y abajo, como en pantalla). */
    fun setVelocity(vxDp: Float, vyDp: Float) {
        gz = toGyro(vxDp)
        gx = toGyro(vyDp)
    }

    /**
     * El giro del móvil como ratón relativo: [yaw] (rad/s, + = girar a la
     * izquierda) y [pitch] (+ = apuntar arriba), ya con su sensibilidad.
     */
    fun setGyro(yaw: Float, pitch: Float) {
        // Cursor a la derecha (dx > 0) = gz < 0; arriba (dy < 0) = gx > 0
        gyroGz = withPcDeadzone(yaw)
        gyroGx = withPcDeadzone(pitch)
    }

    private fun withPcDeadzone(rate: Float): Float =
        if (rate == 0f) 0f else rate + (if (rate > 0f) PC_DEADZONE else -PC_DEADZONE)

    /** Solo apuntar con el móvil (lo de siempre). */
    const val OFF = 0

    /** Solo el dedo: mover el móvil no hace nada. */
    const val ON = 1

    /**
     * Los dos a la vez: el móvil mueve el cursor en RELATIVO (como un ratón
     * giroscópico, sin anclaje) y el dedo lo reubica cuando hace falta.
     */
    const val BOTH = 2

    /** El ajuste del usuario (pantallas del mando de Wii): [OFF], [ON] o [BOTH]. */
    private val _mode = kotlinx.coroutines.flow.MutableStateFlow(OFF)
    val mode: kotlinx.coroutines.flow.StateFlow<Int> = _mode

    /** Lo pone la capa táctil: con [BOTH], el giro real se suma al del dedo. */
    @Volatile
    var mixGyro: Boolean = false

    fun loadEnabled(context: android.content.Context) {
        _mode.value = AppPrefs.pointerTrackpadMode(context)
    }

    fun setMode(context: android.content.Context, m: Int) {
        _mode.value = m.coerceIn(OFF, BOTH)
        AppPrefs.setPointerTrackpadMode(context, _mode.value)
    }

    internal fun toGyro(v: Float): Float {
        if (v == 0f) return 0f
        return withPcDeadzone(-v * GAIN * sensitivity * Precision.factor)
    }

    /** Escribe el giro sintético en [out] (marco del paquete). */
    fun fill(out: FloatArray) {
        if (mixGyro) {
            // [out] ya trae el giro real del móvil: el receptor lo integra
            // en relativo y el dedo se le suma
            out[0] += gx + gyroGx
            out[2] += gz + gyroGz
            return
        }
        out[0] = gx + gyroGx
        out[1] = 0f
        out[2] = gz + gyroGz
    }
}
