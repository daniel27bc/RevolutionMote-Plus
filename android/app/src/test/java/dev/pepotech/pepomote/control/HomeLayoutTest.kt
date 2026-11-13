package dev.pepotech.pepomote.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test fun conServidorAndroidNoSeOfreceLoQueSoloHaceElPc() {
        val a = HomeLayout.available(androidReceiver = true)
        assertFalse(HomeEntry.WiiU in a)
        assertFalse(HomeEntry.Gamepad in a)
        assertFalse(HomeEntry.Pointer in a)
        assertFalse(HomeEntry.Nunchuk in a)
        assertTrue(HomeEntry.Dolphin in a)
        assertTrue(HomeEntry.Kbm in a)
        assertEquals(HomeEntry.entries.toList(), HomeLayout.available(androidReceiver = false))
    }

    @Test fun losFavoritosSalenEnSuOrdenYDejanSuBloque() {
        val saved = listOf("kbm", "dolphin")
        assertEquals(listOf(HomeEntry.Kbm, HomeEntry.Dolphin), HomeLayout.favorites(saved, false))
        assertFalse(HomeEntry.Dolphin in HomeLayout.group(HomeGroup.Controllers, saved, false))
        assertFalse(HomeEntry.Kbm in HomeLayout.group(HomeGroup.Tools, saved, false))
        assertTrue(HomeEntry.WiiU in HomeLayout.group(HomeGroup.Controllers, saved, false))
    }

    @Test fun cadaTarjetaSaleUnaSolaVez() {
        val saved = listOf("gamepad", "switch", "nunchuk")
        for (android in listOf(false, true)) {
            val all = HomeLayout.favorites(saved, android) +
                HomeGroup.entries.flatMap { HomeLayout.group(it, saved, android) }
            assertEquals(HomeLayout.available(android).toSet(), all.toSet())
            assertEquals(all.size, all.toSet().size)
        }
    }

    @Test fun unFavoritoQueAhoraNoSePuedeOfrecerVuelveAlCambiarDeReceptor() {
        val saved = listOf("gamepad", "dolphin")
        assertEquals(listOf(HomeEntry.Dolphin), HomeLayout.favorites(saved, androidReceiver = true))
        assertEquals(listOf(HomeEntry.Gamepad, HomeEntry.Dolphin), HomeLayout.favorites(saved, androidReceiver = false))
    }

    @Test fun elMandoDeWiiEsLaPrimeraTarjetaDeConsolas() {
        assertEquals(HomeEntry.Pointer, HomeLayout.group(HomeGroup.Controllers, emptyList(), false).first())
    }

    @Test fun fijarYQuitar() {
        var s = HomeLayout.toggle(emptyList(), HomeEntry.Dolphin)
        s = HomeLayout.toggle(s, HomeEntry.Kbm)
        assertEquals(listOf("dolphin", "kbm"), s)
        assertEquals(listOf("kbm"), HomeLayout.toggle(s, HomeEntry.Dolphin))
        assertEquals(listOf("kbm", "dolphin"), HomeLayout.moveFirst(s, HomeEntry.Kbm))
        assertEquals(listOf("switch", "dolphin", "kbm"), HomeLayout.moveFirst(s, HomeEntry.Switch))
    }

    @Test fun sinNadaVinculadoSeEmpiezaPorConexion() {
        assertTrue(HomeLayout.startsOnConnection(paired = false, serverRunning = false))
        assertFalse(HomeLayout.startsOnConnection(paired = true, serverRunning = false))
        assertFalse(HomeLayout.startsOnConnection(paired = false, serverRunning = true))
    }

    @Test fun elTopeDeFavoritosSueltaElMasAntiguo() {
        var s = emptyList<String>()
        for (e in HomeEntry.entries) s = HomeLayout.toggle(s, e)
        assertEquals(HomeLayout.MAX_FAVORITES, s.size)
        assertFalse("pointer" in s)
    }

    @Test fun continuarSoloConLoQueSePuedeAbrirAhora() {
        assertEquals(HomeEntry.Gamepad, HomeLayout.last("gamepad", androidReceiver = false))
        assertNull(HomeLayout.last("gamepad", androidReceiver = true))
        assertNull(HomeLayout.last(null, false))
        assertNull(HomeLayout.last("ya-no-existe", false))
    }

    @Test fun losIdsGuardadosSobrevivenAIdaYVuelta() {
        val ids = listOf("kbm", "dolphin")
        assertEquals(ids, HomeLayout.parse(HomeLayout.join(ids)))
        assertEquals(emptyList<String>(), HomeLayout.parse(""))
        assertEquals(emptyList<String>(), HomeLayout.parse(null))
    }
}
