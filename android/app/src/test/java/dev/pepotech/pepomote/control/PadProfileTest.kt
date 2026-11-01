package dev.pepotech.pepomote.control

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PadProfileTest {
    private val xboxFace = RetroLayouts.XBOX.face.map { it.bit }

    private fun factory() = PadProfiles.factory(
        "Mi diseño", hasLeftStick = true, hasRightStick = true, stickClicks = true,
        shoulderBits = listOf(ButtonState.L, ButtonState.ZL, ButtonState.R, ButtonState.ZR),
        centerBits = listOf(ButtonState.MINUS, ButtonState.PLUS), hasCapture = false
    )

    @Test fun elDisenoDeFabricaTieneTodoYSinRepetir() {
        val p = factory()
        val ids = p.elements.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (k in listOf(ElementKind.STICK_L, ElementKind.STICK_R, ElementKind.DPAD, ElementKind.FACE)) {
            assertTrue("$k", p.elements.any { it.kind == k })
        }
        assertTrue(p.elements.all { it.x in 0f..1f && it.y in 0f..1f })
        // Con el rombo puesto no se ofrecen sus botones sueltos
        val add = PadProfiles.addable(p, xboxFace)
        assertFalse(add.any { it.kind == ElementKind.BUTTON && it.bit in xboxFace })
        // Sin Capturar en este mando, es lo único que falta
        assertEquals(listOf(AddOption(ElementKind.BUTTON, ButtonState.SCREEN)), add)
    }

    @Test fun quitarElRomboDejaPonerBotonesSueltos() {
        val p = factory().let { f -> f.remove(f.elements.first { it.kind == ElementKind.FACE }) }
        val add = PadProfiles.addable(p, xboxFace)
        assertTrue(add.contains(AddOption(ElementKind.FACE)))
        for (bit in xboxFace) assertTrue(add.contains(AddOption(ElementKind.BUTTON, bit)))
        // Con A suelto, el rombo ya no se puede volver a poner
        val withA = p.add(PadProfiles.newElement(AddOption(ElementKind.BUTTON, ButtonState.A)))
        assertFalse(PadProfiles.addable(withA, xboxFace).contains(AddOption(ElementKind.FACE)))
    }

    @Test fun cambiarLaFuncionNoRepiteBits() {
        val p = factory()
        val l = p.elements.first { it.bit == ButtonState.L }
        val targets = PadProfiles.remapTargets(p, l, xboxFace)
        assertTrue(ButtonState.L in targets)
        assertFalse(ButtonState.R in targets)
        assertFalse(ButtonState.A in targets) // está en el rombo
    }

    @Test fun elJsonVaYVuelve() {
        val p = factory().let { f ->
            val r = f.elements.first { it.kind == ElementKind.STICK_R }
            f.replace(r, r.copy(trackpad = true, sensitivity = 2.2f, antiDeadzone = 0.3f))
        }.copy(opacity = 0.5f)
        val back = PadProfileJson.listFromString(PadProfileJson.listToString(listOf(p)))
        assertEquals(1, back.size)
        assertEquals(p, back[0])
    }

    @Test fun jsonRotoNoRompe() {
        assertTrue(PadProfileJson.listFromString("{basura").isEmpty())
        assertTrue(PadProfileJson.listFromString(null).isEmpty())
    }

    @Test fun nombresUnicos() {
        assertEquals("Mi diseño", PadProfiles.uniqueName("Mi diseño", emptyList()))
        assertEquals("Mi diseño 3", PadProfiles.uniqueName("Mi diseño", listOf("Mi diseño", "Mi diseño 2")))
    }

    @Test fun mandoFisicoPorPosicion() {
        // Xbox: A abajo → A; B derecha → B
        val xbox = PhysicalPad.faceFor(RetroLayouts.XBOX.face.associate { it.slot to it.bit })
        assertEquals(ButtonState.A, xbox[KeyEvent.KEYCODE_BUTTON_A])
        assertEquals(ButtonState.B, xbox[KeyEvent.KEYCODE_BUTTON_B])
        assertEquals(ButtonState.Y, xbox[KeyEvent.KEYCODE_BUTTON_Y])
        // Nintendo (RetroPad): el de abajo es B y el de la derecha A
        val nin = PhysicalPad.faceFor(RetroLayouts.RETROPAD.face.associate { it.slot to it.bit })
        assertEquals(ButtonState.B, nin[KeyEvent.KEYCODE_BUTTON_A])
        assertEquals(ButtonState.A, nin[KeyEvent.KEYCODE_BUTTON_B])
        assertNotNull(nin[KeyEvent.KEYCODE_BUTTON_X])
    }

    @Test fun trackpadQuietoEsCeroYRapidoSatura() {
        assertEquals(0 to 0, TrackpadEngine.fromVelocity(0f, 0f))
        val (x, _) = TrackpadEngine.fromVelocity(TrackpadEngine.FULL_SPEED * 2, 0f, 1f, 0f)
        assertEquals(127, x)
        val (_, y) = TrackpadEngine.fromVelocity(0f, -1f, 1f, 0f) // dedo hacia arriba = +y
        assertTrue(y > 0)
    }

    @Test fun stickConSensibilidadYAntiZonaMuerta() {
        // Sensibilidad 2: a mitad de recorrido ya está al tope
        assertEquals(127, StickMap.map(60f, 0f, 100f, 0.08f, 0f, 2f).first)
        // Anti-zona muerta: justo fuera del centro ya sale al 30 %
        val (x, _) = StickMap.map(9f, 0f, 100f, 0.08f, 0.3f, 1f)
        assertTrue("fue $x", x in 37..40)
        // Sin zona muerta no hay salto de signo cerca del centro
        assertTrue(StickMap.map(0.5f, 0f, 100f, 0f).first >= 0)
    }
}
