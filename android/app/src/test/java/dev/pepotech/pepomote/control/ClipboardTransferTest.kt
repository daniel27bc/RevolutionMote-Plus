package dev.pepotech.pepomote.control

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ClipboardTransferTest {
    @Test fun conserva_unicode_y_saltos_de_linea() {
        val text = "línea 1\n🎮"
        val outgoing = ClipboardTransfer.request("envío", text)!!
        assertEquals("set", outgoing.getString("op"))
        assertEquals(text, JSONObject(outgoing.toString()).getString("text"))
        val incoming = ClipboardTransfer.request("lectura", null)!!
        assertEquals("get", incoming.getString("op"))
        assertFalse(incoming.has("text"))
        val reply = ClipboardTransfer.reply(JSONObject()
            .put("id", "lectura").put("ok", true).put("text", text))
        assertEquals(text, reply?.text)
    }

    @Test fun rechaza_texto_que_no_cabe_o_contiene_un_nulo() {
        assertNotNull(ClipboardTransfer.request("id", "á".repeat(32_768)))
        assertNull(ClipboardTransfer.request("id", "á".repeat(32_769)))
        assertNull(ClipboardTransfer.request("id", "antes\u0000después"))
    }
}
