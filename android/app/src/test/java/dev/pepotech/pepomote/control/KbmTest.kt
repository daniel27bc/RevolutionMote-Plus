package dev.pepotech.pepomote.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KbmTest {
    @Test fun codigosHidDelTeclado() {
        assertEquals(0x1A, HidKeys.usage("KeyW"))
        assertEquals(0x27, HidKeys.usage("Digit0"))
        assertEquals(0x1E, HidKeys.usage("Digit1"))
        assertEquals(0x3E, HidKeys.usage("F5"))
        assertEquals(0x29, HidKeys.usage("Escape"))
        assertNull(HidKeys.usage("Nada"))
        assertEquals(0x01, HidKeys.modifierBit("ControlLeft"))
        assertEquals(0x08, HidKeys.modifierBit("MetaLeft"))
        assertEquals(0x04 to true, HidKeys.forChar('A'))
        assertEquals(0x1F to true, HidKeys.forChar('@'))
        assertNull(HidKeys.forChar('ñ'))
    }

    @Test fun elTouchpadMuevePixelesProporcionales() {
        // 1 dp/ms durante 10 ms = 22 px con sensibilidad 1
        assertEquals(22, Kbm.pixelsFor(1f, 10f))
    }

    @Test fun teclasEnElDisenoNoSeRepiten() {
        val p = PadProfile("x", emptyList())
        val k1 = PadProfiles.newElement(AddOption(ElementKind.KEY)).copy(key = PadProfiles.freeKey(p))
        val p2 = p.add(k1)
        val k2 = PadProfiles.newElement(AddOption(ElementKind.KEY)).copy(key = PadProfiles.freeKey(p2))
        assertTrue(k1.key != k2.key)
        val back = PadProfileJson.listFromString(PadProfileJson.listToString(listOf(p2.add(k2))))
        assertEquals(setOf(k1.key, k2.key), back[0].elements.map { it.key }.toSet())
    }
}
