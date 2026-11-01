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
}
