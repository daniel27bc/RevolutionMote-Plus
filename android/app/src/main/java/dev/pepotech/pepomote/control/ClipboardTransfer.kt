package dev.pepotech.pepomote.control

import org.json.JSONObject

/** Texto de portapapeles, independiente del mensaje que simula escribir teclas. */
object ClipboardTransfer {
    private const val MAX_BYTES = 64 * 1024

    data class Reply(val id: String, val ok: Boolean, val text: String?, val error: String)

    fun valid(text: String): Boolean =
        text.isNotEmpty() && '\u0000' !in text && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES

    fun request(id: String, text: String?): JSONObject? {
        if (text != null && !valid(text)) return null
        return JSONObject()
            .put("m", "clipboard")
            .put("id", id)
            .put("op", if (text == null) "get" else "set")
            .apply { if (text != null) put("text", text) }
    }

    fun reply(message: JSONObject): Reply? {
        val id = message.optString("id").takeIf { it.isNotEmpty() && it.length <= 36 } ?: return null
        val ok = message.optBoolean("ok", false)
        val text = message.opt("text") as? String
        if (ok && text != null && !valid(text)) return Reply(id, false, null, "invalid_text")
        return Reply(id, ok, if (ok) text else null, message.optString("error"))
    }
}
