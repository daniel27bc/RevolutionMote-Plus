package dev.pepotech.pepomote.control

/**
 * Modo precisión del mando universal: mientras se mantiene su botón, el
 * giroscopio, los trackpads y el ratón van a [FACTOR] de su velocidad (para
 * apuntar fino). Con el receptor original en modo puntero también se pide
 * la precisión del PC (bit [ButtonState.PRECISION]).
 */
object Precision {
    const val FACTOR = 0.35f

    @Volatile
    var held: Boolean = false
        private set

    /** Botón del mando que también la activa mientras se mantiene (0 = ninguno). */
    @Volatile
    var bindBit: Int = 0

    /** Velocidad que queda con la precisión puesta. */
    @Volatile
    var amount: Float = FACTOR

    /** ¿Está puesta ahora? (botón en pantalla o el botón asignado mantenido) */
    val active: Boolean
        get() = held || (bindBit != 0 && ButtonState.current() and bindBit != 0)

    val factor: Float get() = if (active) amount else 1f

    fun set(on: Boolean) {
        held = on
        if (Kbm.backend == Kbm.Backend.POINTER) ButtonState.set(ButtonState.PRECISION, on)
    }
}
