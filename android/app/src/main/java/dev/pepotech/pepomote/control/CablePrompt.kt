package dev.pepotech.pepomote.control

/**
 * Conexión por cable sin pantalla propia: al abrir un modo que va por el
 * receptor con la conexión USB compartida activa, se sugiere usarla. Qué
 * hacer lo decide el ajuste «Conexión por cable» (preguntar, usarla siempre o
 * nunca) y si ya se dijo que no en esta sesión de la app.
 */
object CablePrompt {
    const val ASK = "ask"
    const val ALWAYS = "always"
    const val NEVER = "never"

    enum class Action { Ask, UseCable, Skip }

    fun decide(
        pref: String,
        /** La conexión USB compartida de Android está activa. */
        cableUp: Boolean,
        /** El enlace de ahora ya va por el cable. */
        overUsb: Boolean,
        /** Con un servidor Android al otro lado no hay cable que valga. */
        androidReceiver: Boolean,
        paired: Boolean,
        /** Ya se dijo «No» (sin recordar) desde que se abrió la app. */
        declinedThisSession: Boolean
    ): Action = when {
        !paired || androidReceiver || !cableUp || overUsb -> Action.Skip
        pref == NEVER -> Action.Skip
        pref == ALWAYS -> Action.UseCable
        declinedThisSession -> Action.Skip
        else -> Action.Ask
    }

    /** Un valor guardado que no se reconoce cuenta como «preguntar». */
    fun normalize(saved: String?): String = when (saved) {
        ALWAYS, NEVER -> saved
        else -> ASK
    }
}
