package dev.pepotech.pepomote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneReplyTest {
    @Test fun laInstalacionManualTraeLaCarpeta() {
        val r = MicrophoneClient.parse("""{"m":"microphone","status":"manual","bundled":true,"path":"C:\\Users\\d\\VB-CABLE"}""")
        assertEquals("manual", r.status)
        assertEquals("C:\\Users\\d\\VB-CABLE", r.path)
        assertTrue(r.bundled)
    }

    @Test fun sinCarpetaNiPaqueteNoSeInventaNada() {
        val r = MicrophoneClient.parse("""{"m":"microphone","status":"manual","bundled":false,"path":null}""")
        assertNull(r.path)
        assertFalse(r.bundled)
    }

    @Test fun unaLineaRotaNoTumbaNada() {
        assertEquals("unavailable", MicrophoneClient.parse("no es json").status)
        assertEquals("installed", MicrophoneClient.parse("""{"status":"installed"}""").status)
    }
}
