package dev.pepotech.pepomote.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El giro "player space": horizontal alrededor de la vertical real. */
class GyroAimTest {
    @Test fun girarAlrededorDeLaVerticalEsHorizontalDePieYTumbado() {
        // Móvil de pie (pantalla hacia ti): la vertical es +y; girar a la izquierda = +gy
        val (yawUp, pitchUp) = GyroAim.playerSpace(0f, 1f, 0f, 0f, 1f, 0f)
        assertTrue(yawUp > 0.9f)
        assertEquals(0f, pitchUp, 1e-6f)
        // Tumbado (pantalla hacia arriba): la vertical es +z; el mismo giro = +gz
        val (yawFlat, _) = GyroAim.playerSpace(0f, 0f, 1f, 0f, 0f, 1f)
        assertTrue(yawFlat > 0.9f)
        // Inclinado a 45°: sigue saliendo horizontal entero
        val s = 0.7071f
        val (yaw45, _) = GyroAim.playerSpace(0f, s, s, 0f, s, s)
        assertTrue(yaw45 > 0.95f)
    }

    @Test fun inclinarHaciaTiEsApuntarArriba() {
        val (yaw, pitch) = GyroAim.playerSpace(1f, 0f, 0f, 0f, 1f, 0f)
        assertEquals(0f, yaw, 1e-6f)
        assertTrue(pitch > 0f)
    }

    @Test fun pulsoQuietoNoMueveYRapidoSatura() {
        assertEquals(0 to 0, GyroAim.toStick(0.005f, 0f, 0.2f))
        assertEquals(127, GyroAim.toStick(3f, 0f, 0f).first)
        // Justo fuera del reposo ya sale con la anti-zona muerta
        val (x, _) = GyroAim.toStick(0.02f, 0f, 0.2f)
        assertTrue("fue $x", x in 25..28)
    }

    /** Con el móvil de pie en cualquiera de los dos apaisados, girar a la izquierda es horizontal. */
    @Test fun losDosApaisadosDanElMismoGiroHorizontal() {
        for ((rot, gyroDev, accelDev) in listOf(
            // ROTATION_90: el borde de arriba a la izquierda; la vertical de la pantalla es +x del móvil
            Triple(dev.pepotech.pepomote.sensor.Frame.ROTATION_90, floatArrayOf(1f, 0f, 0f), floatArrayOf(9.8f, 0f, 0f)),
            // ROTATION_270: el borde de arriba a la derecha; la vertical de la pantalla es −x
            Triple(dev.pepotech.pepomote.sensor.Frame.ROTATION_270, floatArrayOf(-1f, 0f, 0f), floatArrayOf(-9.8f, 0f, 0f))
        )) {
            val g = dev.pepotech.pepomote.sensor.Frame.remapGyro(gyroDev, rot)
            val a = dev.pepotech.pepomote.sensor.Frame.remapAccel(accelDev, rot)
            val n = kotlin.math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
            val (yaw, pitch) = GyroAim.playerSpace(g[0], g[1], g[2], a[0] / n, a[1] / n, a[2] / n)
            assertTrue("rot $rot: yaw $yaw", yaw > 0.9f)
            assertEquals("rot $rot: pitch", 0f, pitch, 1e-5f)
        }
    }

    @Test fun trackpadDeRatonVaHaciaDondeVaElDedo() {
        PointerTrackpad.sensitivity = 1f
        // El PC mueve dx = −gz·dt·k: dedo a la derecha → gz negativo
        assertTrue(PointerTrackpad.toGyro(1f) < -0.03f)
        assertTrue(PointerTrackpad.toGyro(-1f) > 0.03f)
        assertEquals(0f, PointerTrackpad.toGyro(0f), 0f)
    }
}
