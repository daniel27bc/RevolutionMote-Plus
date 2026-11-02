package dev.pepotech.pepomote.control

import java.util.concurrent.atomic.AtomicLong

/**
 * Contadores para la pantalla de diagnóstico: qué está saliendo del móvil de
 * verdad (paquetes, píxeles de ratón, vibraciones) y qué está leyendo
 * (giroscopio). Baratos a propósito: solo sumas y escrituras volátiles en los
 * caminos calientes, sin listas ni marcas de tiempo.
 */
object Diag {
    /** Paquetes UDP de entrada enviados al receptor. */
    val packets = AtomicLong()

    /** Lotes `kbm` enviados por el canal de control. */
    val kbmBatches = AtomicLong()

    /** Píxeles de ratón enviados (|dx| + |dy|), por cualquier camino. */
    val mousePixels = AtomicLong()

    /** Clics y teclas enviados por [Kbm]. */
    val kbmEvents = AtomicLong()

    /** Vibraciones de los botones que se han pedido y cuántas se han emitido. */
    val hapticAsked = AtomicLong()
    val hapticPlayed = AtomicLong()

    /** Por qué no sonó la última (vacío = sonó). */
    @Volatile
    var hapticSkip: String = ""

    /** Camino de la última vibración: «primitivas», «efecto», «sistema»… */
    @Volatile
    var hapticPath: String = ""

    /** Último giro leído (rad/s, ejes del móvil). */
    @Volatile
    var gyroX = 0f

    @Volatile
    var gyroY = 0f

    @Volatile
    var gyroZ = 0f

    fun gyro(v: FloatArray) {
        gyroX = v[0]
        gyroY = v[1]
        gyroZ = v[2]
    }

    fun hapticSkipped(reason: String) {
        hapticAsked.incrementAndGet()
        hapticSkip = reason
    }

    fun hapticDone(path: String) {
        hapticAsked.incrementAndGet()
        hapticPlayed.incrementAndGet()
        hapticSkip = ""
        hapticPath = path
    }
}
