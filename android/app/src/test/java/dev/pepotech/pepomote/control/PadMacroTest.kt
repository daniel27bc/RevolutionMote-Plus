package dev.pepotech.pepomote.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadMacroTest {
    @Test fun elTextoDeUnaMacroVaYVuelve() {
        val spec = "DOWN RIGHT A+B 150 key:KeyW X+key:Space"
        val steps = PadMacro.parse(spec)
        assertEquals(6, steps.size)
        assertEquals(ButtonState.A or ButtonState.B, steps[2].bits)
        assertEquals(150, steps[3].waitMs)
        assertEquals(listOf("KeyW"), steps[4].keys)
        assertEquals(spec, PadMacro.format(steps))
    }

    @Test fun loQueNoSeEntiendeSeIgnora() {
        assertEquals(emptyList<PadMacro.Step>(), PadMacro.parse(""))
        assertEquals(emptyList<PadMacro.Step>(), PadMacro.parse("  NOEXISTE  0  -5 "))
        assertEquals("A", PadMacro.format(PadMacro.parse("A+NOEXISTE key:../../x")))
        assertEquals(PadMacro.MAX_WAIT_MS, PadMacro.parse("999999").single().waitMs)
        assertEquals(PadMacro.MAX_STEPS, PadMacro.parse("A ".repeat(100)).size)
    }

    @Test fun cadaPulsacionSeMantieneYSeSueltaAntesDeLaSiguiente() {
        val plan = PadMacro.plan(PadMacro.parse("A 100 B"))
        assertEquals(4, plan.size)
        assertEquals(PadMacro.Action(0, ButtonState.A, emptyList(), true), plan[0])
        assertEquals(PadMacro.Action(PadMacro.HOLD_MS, ButtonState.A, emptyList(), false), plan[1])
        val second = PadMacro.HOLD_MS + PadMacro.GAP_MS + 100
        assertEquals(second, plan[2].atMs)
        assertTrue(plan[2].down)
        assertEquals(second + PadMacro.HOLD_MS, plan[3].atMs)
    }

    @Test fun losHuecosDeMacroSeAcaban() {
        assertEquals("M1", PadMacro.freeSlot(emptySet()))
        assertEquals("M2", PadMacro.freeSlot(setOf("M1", "M3")))
        assertNull(PadMacro.freeSlot((1..PadMacro.MAX_SLOTS).map { "M$it" }.toSet()))
    }

    @Test fun conShiftElBotonEnviaSuSegundaFuncionYSeSueltaComoSePulso() {
        PadShift.clear()
        PadShift.map = mapOf(ButtonState.A to ButtonState.X)
        assertEquals(ButtonState.A, PadShift.route(ButtonState.A, true))
        assertEquals(ButtonState.A, PadShift.route(ButtonState.A, false))
        PadShift.held = true
        assertEquals(ButtonState.X, PadShift.route(ButtonState.A, true))
        assertEquals(ButtonState.B, PadShift.route(ButtonState.B, true))
        // Shift se levanta antes que el botón: se suelta X, no A
        PadShift.held = false
        assertEquals(ButtonState.X, PadShift.route(ButtonState.A, false))
        assertEquals(ButtonState.A, PadShift.route(ButtonState.A, true))
        PadShift.clear()
    }

    @Test fun elMapaDeShiftSaleDeLosBotonesDelDiseno() {
        val a = PadElement(ElementKind.BUTTON, 0.5f, 0.5f, 0.13f, bit = ButtonState.A, shiftBit = ButtonState.X)
        val b = PadElement(ElementKind.BUTTON, 0.6f, 0.5f, 0.13f, bit = ButtonState.B)
        val shift = PadElement(ElementKind.SHIFT, 0.2f, 0.2f, 0.15f)
        assertEquals(emptyMap<Int, Int>(), PadProfiles.shiftMap(PadProfile("p", listOf(a, b))))
        assertEquals(mapOf(ButtonState.A to ButtonState.X), PadProfiles.shiftMap(PadProfile("p", listOf(a, b, shift))))
    }

    @Test fun macroYShiftVanYVuelvenEnElPerfil() {
        val p = PadProfile(
            "p",
            listOf(
                PadElement(ElementKind.MACRO, 0.5f, 0.5f, 0.13f, key = "M2", macro = "A 100 B"),
                PadElement(ElementKind.BUTTON, 0.6f, 0.5f, 0.13f, bit = ButtonState.A, shiftBit = ButtonState.Y),
                PadElement(ElementKind.SHIFT, 0.2f, 0.2f, 0.15f)
            )
        )
        assertEquals(p, PadProfileJson.fromJson(PadProfileJson.toJson(p)))
    }
}
