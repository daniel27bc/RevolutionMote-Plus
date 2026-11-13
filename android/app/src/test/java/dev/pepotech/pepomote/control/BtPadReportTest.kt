package dev.pepotech.pepomote.control

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BtPadReportTest {
    private fun u(b: Byte) = b.toInt() and 0xFF

    @Test fun enReposoTodoSueltoYLosSticksCentrados() {
        val r = BtPadReport.NEUTRAL
        assertEquals(7, r.size)
        assertEquals(listOf(0, 0, BtPadReport.HAT_NONE, 128, 128, 128, 128), r.map { u(it) })
    }

    @Test fun cadaBotonVaASuSitioDelMandoEstandar() {
        assertEquals(0x01, u(BtPadReport.build(ButtonState.A, 0, 0, 0, 0)[0]))
        assertEquals(0x02, u(BtPadReport.build(ButtonState.B, 0, 0, 0, 0)[0]))
        assertEquals(0x08, u(BtPadReport.build(ButtonState.X, 0, 0, 0, 0)[0]))
        assertEquals(0x10, u(BtPadReport.build(ButtonState.Y, 0, 0, 0, 0)[0]))
        assertEquals(0xC0, u(BtPadReport.build(ButtonState.L or ButtonState.R, 0, 0, 0, 0)[0]))
        val high = BtPadReport.build(ButtonState.ZL or ButtonState.PLUS or ButtonState.STICK_R, 0, 0, 0, 0)
        assertEquals(0, u(high[0]))
        assertEquals(0x01 or 0x08 or 0x40, u(high[1]))
    }

    @Test fun laCrucetaEsUnHatConDiagonales() {
        assertEquals(0, BtPadReport.hat(ButtonState.DPAD_UP))
        assertEquals(1, BtPadReport.hat(ButtonState.DPAD_UP or ButtonState.DPAD_RIGHT))
        assertEquals(2, BtPadReport.hat(ButtonState.DPAD_RIGHT))
        assertEquals(4, BtPadReport.hat(ButtonState.DPAD_DOWN))
        assertEquals(6, BtPadReport.hat(ButtonState.DPAD_LEFT))
        assertEquals(7, BtPadReport.hat(ButtonState.DPAD_UP or ButtonState.DPAD_LEFT))
        // Opuestos a la vez: se anulan
        assertEquals(BtPadReport.HAT_NONE, BtPadReport.hat(ButtonState.DPAD_UP or ButtonState.DPAD_DOWN))
        assertEquals(BtPadReport.HAT_NONE, BtPadReport.hat(ButtonState.A))
    }

    @Test fun losSticksVanDe1A255YArribaEsHaciaElCero() {
        val r = BtPadReport.build(0, 127, 127, -127, -127)
        assertEquals(255, u(r[3]))
        assertEquals(1, u(r[4])) // +y arriba → hacia 0 en HID
        assertEquals(1, u(r[5]))
        assertEquals(255, u(r[6]))
        assertArrayEquals(BtPadReport.build(0, 127, 0, 0, 0), BtPadReport.build(0, 9999, 0, 0, 0))
    }
}
