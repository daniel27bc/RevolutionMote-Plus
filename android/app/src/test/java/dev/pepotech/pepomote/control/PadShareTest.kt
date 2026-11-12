package dev.pepotech.pepomote.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadShareTest {
    private val profile = PadProfile(
        name = "Carreras ñ",
        elements = listOf(
            PadElement(ElementKind.STICK_L, 0.2f, 0.7f, 0.4f),
            PadElement(ElementKind.BUTTON, 0.8f, 0.6f, 0.2f, bit = ButtonState.A, turbo = true),
            PadElement(ElementKind.KEY, 0.5f, 0.2f, 0.12f, key = "Escape")
        )
    )

    @Test fun unPerfilVaYVuelvePorElQr() {
        val text = PadShare.encode(profile)
        assertNotNull(text)
        assertTrue(PadShare.isShare(text!!))
        val json = PadShare.decode(text)
        assertNotNull(json)
        // Lo mismo que guardar y abrir el perfil (que recoloca lo que se sale)
        assertEquals(PadProfileJson.fromJson(PadProfileJson.toJson(profile)), PadProfileJson.fromJson(org.json.JSONObject(json!!)))
    }

    @Test fun unQrDeEmparejarNoEsUnPerfil() {
        assertFalse(PadShare.isShare("pepomote://pair?h=1.2.3.4"))
        assertNull(PadShare.decode("pepomote://pair?h=1.2.3.4"))
    }

    @Test fun unQrRotoNoRevienta() {
        assertNull(PadShare.decode(PadShare.PREFIX + "%%%no-es-base64%%%"))
        assertNull(PadShare.decode(PadShare.PREFIX + "AAAA"))
        assertNull(PadShare.decode(PadShare.encode(profile)!!.dropLast(12)))
    }

    @Test fun unPerfilEnormeNoCabe() {
        val big = profile.copy(elements = (0 until 600).map {
            PadElement(ElementKind.KEY, it / 600f, (it * 7 % 600) / 600f, 0.1f + it * 0.0001f, key = "Tecla$it")
        })
        assertNull(PadShare.encode(big))
    }
}
