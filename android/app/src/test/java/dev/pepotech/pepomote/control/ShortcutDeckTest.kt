package dev.pepotech.pepomote.control

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutDeckTest {
    @Test fun deFabricaHayDoceAtajosConTeclas() {
        for (lang in listOf("es", "en")) {
            val d = ShortcutDeck.defaults(lang)
            assertEquals(ShortcutDeck.SIZE, d.size)
            assertTrue(d.none { it.isEmpty })
            assertTrue(d.all { it.label.length <= ShortcutDeck.MAX_LABEL })
        }
    }

    @Test fun loGuardadoVaYVuelveYLoRotoEsDeFabrica() {
        val mine = ShortcutDeck.defaults("es").toMutableList()
        mine[3] = Shortcut("Hola", text = "hola, mundo")
        assertEquals(mine, ShortcutDeck.fromJson(ShortcutDeck.toJson(mine), "es"))
        assertEquals(ShortcutDeck.defaults("es"), ShortcutDeck.fromJson(null, "es"))
        assertEquals(ShortcutDeck.defaults("en"), ShortcutDeck.fromJson("{no es json", "en"))
        // Una lista corta se completa con los de fábrica
        val short = ShortcutDeck.fromJson(ShortcutDeck.toJson(mine.take(4)), "es")
        assertEquals(ShortcutDeck.SIZE, short.size)
        assertEquals(mine[3], short[3])
        assertEquals(ShortcutDeck.defaults("es")[7], short[7])
    }

    @Test fun losModificadoresSePulsanAntes() {
        assertEquals(listOf("ControlLeft", "ShiftLeft", "KeyS"), ShortcutDeck.chord(Shortcut("x", listOf("KeyS", "ShiftLeft", "ControlLeft"))))
        assertEquals("Ctrl+Shift+S", ShortcutDeck.describe(Shortcut("x", listOf("KeyS", "ShiftLeft", "ControlLeft")), "es"))
    }

    @Test fun laMacSeLeeConCualquierSeparador() {
        val mac = byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E)
        assertArrayEquals(mac, WakeOnLan.parseMac("00:1a:2B:3c:4D:5e"))
        assertArrayEquals(mac, WakeOnLan.parseMac(" 00-1A-2B-3C-4D-5E "))
        assertArrayEquals(mac, WakeOnLan.parseMac("001A2B3C4D5E"))
        assertNull(WakeOnLan.parseMac("00:1A:2B:3C:4D"))
        assertNull(WakeOnLan.parseMac("zz:1A:2B:3C:4D:5E"))
        assertEquals("00:1A:2B:3C:4D:5E", WakeOnLan.format(mac))
    }

    @Test fun elPaqueteMagicoSonSeisFFYLaMacDieciseisVeces() {
        val mac = byteArrayOf(1, 2, 3, 4, 5, 6)
        val p = WakeOnLan.packet(mac)
        assertEquals(102, p.size)
        assertTrue(p.take(6).all { it == 0xFF.toByte() })
        for (i in 0 until 16) assertArrayEquals(mac, p.copyOfRange(6 + i * 6, 12 + i * 6))
    }

    @Test fun seDifundeATodaLaRedYALaSubredDelPc() {
        assertEquals(listOf("255.255.255.255", "192.168.1.255"), WakeOnLan.targets("192.168.1.40"))
        assertEquals(listOf("255.255.255.255"), WakeOnLan.targets(null))
        assertEquals(listOf("255.255.255.255"), WakeOnLan.targets("mi-pc.local"))
    }
}
