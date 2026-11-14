package dev.pepotech.pepomote.control

import dev.pepotech.pepomote.control.CablePrompt.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class CablePromptTest {
    private fun decide(
        pref: String = CablePrompt.ASK,
        cableUp: Boolean = true,
        overUsb: Boolean = false,
        androidReceiver: Boolean = false,
        paired: Boolean = true,
        declined: Boolean = false
    ) = CablePrompt.decide(pref, cableUp, overUsb, androidReceiver, paired, declined)

    @Test fun conElCablePuestoSePreguntaUnaVez() {
        assertEquals(Action.Ask, decide())
        assertEquals("tras un «No» no se vuelve a preguntar en la sesión", Action.Skip, decide(declined = true))
    }

    @Test fun sinCableOYaPorCableNoSeDiceNada() {
        assertEquals(Action.Skip, decide(cableUp = false))
        assertEquals(Action.Skip, decide(overUsb = true))
        assertEquals(Action.Skip, decide(androidReceiver = true))
        assertEquals(Action.Skip, decide(paired = false))
    }

    @Test fun elAjusteManda() {
        assertEquals(Action.UseCable, decide(pref = CablePrompt.ALWAYS))
        assertEquals(Action.UseCable, decide(pref = CablePrompt.ALWAYS, declined = true))
        assertEquals(Action.Skip, decide(pref = CablePrompt.NEVER))
    }

    @Test fun unValorRaroEsPreguntar() {
        assertEquals(CablePrompt.ASK, CablePrompt.normalize(null))
        assertEquals(CablePrompt.ASK, CablePrompt.normalize("quizá"))
        assertEquals(CablePrompt.NEVER, CablePrompt.normalize(CablePrompt.NEVER))
    }
}
