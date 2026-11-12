package dev.pepotech.pepomote.control

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Un perfil del mando universal en un texto corto que cabe en un QR:
 * el JSON de siempre, comprimido y en base64 para URL, con un prefijo que
 * lo distingue de un QR de emparejamiento.
 */
object PadShare {
    const val PREFIX = "RMPAD1:"

    /** Más que esto ya es un QR demasiado denso para leerlo de pantalla a pantalla. */
    const val MAX_QR_CHARS = 2300

    /** Tope al descomprimir: un QR no puede inflarse hasta llenar la memoria. */
    private const val MAX_JSON_BYTES = 256 * 1024

    fun isShare(text: String): Boolean = text.trim().startsWith(PREFIX)

    /** El texto del QR de [profile], o null si no cabe. */
    fun encode(profile: PadProfile): String? {
        val json = PadProfileJson.toJson(profile).toString().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(json)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val text = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        return text.takeIf { it.length <= MAX_QR_CHARS }
    }

    /** El JSON del perfil que lleva [text] (para PadProfileStore.importJson), o null si no es un QR de perfil válido. */
    fun decode(text: String): String? {
        val t = text.trim()
        if (!t.startsWith(PREFIX)) return null
        return try {
            val packed = Base64.getUrlDecoder().decode(t.removePrefix(PREFIX))
            val inflater = Inflater()
            inflater.setInput(packed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_JSON_BYTES) return null
            }
            val done = inflater.finished()
            inflater.end()
            if (done) out.toString("UTF-8") else null
        } catch (_: Exception) {
            null
        }
    }
}
