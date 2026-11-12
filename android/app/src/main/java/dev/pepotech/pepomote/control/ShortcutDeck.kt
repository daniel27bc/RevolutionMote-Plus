package dev.pepotech.pepomote.control

import org.json.JSONArray
import org.json.JSONObject

/**
 * Un botón del panel de atajos: pulsa [keys] a la vez (Ctrl+C) o, si lleva
 * [text], lo teclea.
 */
data class Shortcut(val label: String, val keys: List<String> = emptyList(), val text: String = "") {
    val isEmpty: Boolean get() = keys.isEmpty() && text.isEmpty()
}

/** El panel de atajos de Teclado y ratón: doce botones a gusto de cada uno. */
object ShortcutDeck {
    const val SIZE = 12
    const val MAX_LABEL = 18
    const val MAX_TEXT = 200
    val MODIFIERS = listOf("ControlLeft" to "Ctrl", "AltLeft" to "Alt", "ShiftLeft" to "Shift", "MetaLeft" to "Win")

    /** Los de fábrica (los rótulos, en el idioma de la app). */
    fun defaults(lang: String): List<Shortcut> {
        val es = lang != "en"
        return listOf(
            Shortcut(if (es) "Copiar" else "Copy", listOf("ControlLeft", "KeyC")),
            Shortcut(if (es) "Pegar" else "Paste", listOf("ControlLeft", "KeyV")),
            Shortcut(if (es) "Deshacer" else "Undo", listOf("ControlLeft", "KeyZ")),
            Shortcut(if (es) "Guardar" else "Save", listOf("ControlLeft", "KeyS")),
            Shortcut(if (es) "Cambiar ventana" else "Switch window", listOf("AltLeft", "Tab")),
            Shortcut(if (es) "Escritorio" else "Desktop", listOf("MetaLeft", "KeyD")),
            Shortcut(if (es) "Pestaña nueva" else "New tab", listOf("ControlLeft", "KeyT")),
            Shortcut(if (es) "Cerrar pestaña" else "Close tab", listOf("ControlLeft", "KeyW")),
            Shortcut(if (es) "Captura" else "Screenshot", listOf("MetaLeft", "ShiftLeft", "KeyS")),
            Shortcut(if (es) "Explorador" else "Explorer", listOf("MetaLeft", "KeyE")),
            Shortcut(if (es) "Cerrar ventana" else "Close window", listOf("AltLeft", "F4")),
            Shortcut(if (es) "Bloquear" else "Lock", listOf("MetaLeft", "KeyL"))
        )
    }

    fun toJson(list: List<Shortcut>): String = JSONArray().apply {
        for (s in list) put(
            JSONObject().put("label", s.label).put("keys", JSONArray(s.keys)).put("text", s.text)
        )
    }.toString()

    /** Lo guardado, completado con los de fábrica hasta [SIZE] (null o roto = todos de fábrica). */
    fun fromJson(json: String?, lang: String): List<Shortcut> {
        val base = defaults(lang)
        if (json.isNullOrBlank()) return base
        return try {
            val arr = JSONArray(json)
            List(SIZE) { i ->
                val o = arr.optJSONObject(i) ?: return@List base[i]
                val keys = o.optJSONArray("keys")
                Shortcut(
                    o.optString("label").take(MAX_LABEL),
                    List(keys?.length() ?: 0) { keys!!.optString(it) }.filter { it.isNotEmpty() },
                    o.optString("text").take(MAX_TEXT)
                )
            }
        } catch (_: Exception) {
            base
        }
    }

    /** Las teclas en el orden de pulsar: primero los modificadores. */
    fun chord(s: Shortcut): List<String> {
        val mods = MODIFIERS.map { it.first }
        return mods.filter { it in s.keys } + s.keys.filter { it !in mods }
    }

    fun fire(s: Shortcut) {
        if (s.text.isNotEmpty()) {
            Kbm.text(s.text)
            return
        }
        val order = chord(s)
        for (k in order) Kbm.key(k, true)
        for (k in order.reversed()) Kbm.key(k, false)
    }

    /** «Ctrl+C» para enseñarlo bajo el rótulo. */
    fun describe(s: Shortcut, lang: String): String =
        if (s.text.isNotEmpty()) "“${s.text.take(14)}”" else chord(s).joinToString("+") { PadKeys.label(it, lang) }
}

/** Encender el PC por la red (Wake-on-LAN): el «paquete mágico» a su dirección MAC. */
object WakeOnLan {
    /** «AA:BB:CC:DD:EE:FF» (o con guiones, o sin separar) → 6 bytes; null si no es una MAC. */
    fun parseMac(text: String): ByteArray? {
        val hex = text.trim().replace(":", "").replace("-", "").replace(".", "")
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun format(mac: ByteArray): String = mac.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    /** 6 × 0xFF y la MAC repetida 16 veces. */
    fun packet(mac: ByteArray): ByteArray {
        val p = ByteArray(6 + 16 * 6) { 0xFF.toByte() }
        for (i in 0 until 16) System.arraycopy(mac, 0, p, 6 + i * 6, 6)
        return p
    }

    /** A dónde difundirlo: a toda la red y a la subred del PC (x.y.z.255). */
    fun targets(pcHost: String?): List<String> {
        val out = arrayListOf("255.255.255.255")
        val parts = pcHost?.split('.')
        if (parts != null && parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            out += parts.take(3).joinToString(".") + ".255"
        }
        return out
    }

    /** Manda el paquete (en un hilo aparte). [onDone]: si salió al menos una vez. */
    fun send(mac: ByteArray, pcHost: String?, onDone: (Boolean) -> Unit = {}) {
        Thread({
            var ok = false
            try {
                java.net.DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val data = packet(mac)
                    for (target in targets(pcHost)) for (port in intArrayOf(9, 7)) {
                        try {
                            socket.send(java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName(target), port))
                            ok = true
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
            onDone(ok)
        }, "wake-on-lan").start()
    }
}
