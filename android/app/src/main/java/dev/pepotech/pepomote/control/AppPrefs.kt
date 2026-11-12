package dev.pepotech.pepomote.control

import android.content.Context
import androidx.core.content.edit
import dev.pepotech.pepomote.service.PadPreference
import dev.pepotech.pepomote.service.RetroLayoutChoice

/** Preferencias simples de la app (aparte del emparejamiento). */
object AppPrefs {
    private const val PREFS = "app"

    fun pad(context: Context, mode: String): String {
        val key = PadPreference.key(mode) ?: return PadPreference.normalize(mode, null)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(key, null)
        val normalized = PadPreference.normalize(mode, saved)
        if (saved != normalized) prefs.edit { putString(key, normalized) }
        return normalized
    }

    fun setPad(context: Context, mode: String, pad: String) {
        val key = PadPreference.key(mode) ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(key, PadPreference.normalize(mode, pad))
        }
    }

    /** RetroArch: mando de consola elegido a mano (JSON de [RetroLayoutChoice]). */
    fun retroLayoutChoice(context: Context): RetroLayoutChoice =
        RetroLayoutChoice.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("retroLayouts", null))

    fun setRetroLayoutChoice(context: Context, choice: RetroLayoutChoice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("retroLayouts", choice.encode()) }
    }

    /**
     * El botón «Teclado» del modo puntero hace antes un clic izquierdo donde
     * apunta el usuario, para dejar el cursor dentro del campo. Apagado, el
     * clic lo da el usuario con A antes de escribir. De serie, encendido.
     */
    fun keyboardClickFirst(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("kbClickFirst", true)

    fun setKeyboardClickFirst(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("kbClickFirst", value).apply()
    }

    fun volDownIsB(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("volDownB", true)

    fun setVolDownIsB(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("volDownB", value).apply()
    }

    const val VOL_ACTION_DEFAULT_UP = "A"
    const val VOL_ACTION_DEFAULT_DOWN = "B"
    const val RIGHT_STICK_MODE_ANALOG = "analog"
    const val RIGHT_STICK_MODE_TRACKPAD = "trackpad"

    fun volUpAction(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("volUpAction", VOL_ACTION_DEFAULT_UP) ?: VOL_ACTION_DEFAULT_UP

    fun setVolUpAction(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("volUpAction", value).apply()
    }

    fun volDownAction(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("volDownAction", VOL_ACTION_DEFAULT_DOWN) ?: VOL_ACTION_DEFAULT_DOWN

    fun setVolDownAction(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("volDownAction", value).apply()
    }

    fun actionToBit(action: String): Int = when (action) {
        "A" -> ButtonState.A
        "B" -> ButtonState.B
        "X" -> ButtonState.X
        "Y" -> ButtonState.Y
        "L" -> ButtonState.L
        "R" -> ButtonState.R
        "ZL" -> ButtonState.ZL
        "ZR" -> ButtonState.ZR
        "STICK_L" -> ButtonState.STICK_L
        "STICK_R" -> ButtonState.STICK_R
        else -> 0
    }

    fun volUpBit(context: Context): Int = actionToBit(volUpAction(context))
    fun volDownBit(context: Context): Int = actionToBit(volDownAction(context))

    fun rightStickMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("rightStickMode", RIGHT_STICK_MODE_ANALOG) ?: RIGHT_STICK_MODE_ANALOG

    fun setRightStickMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("rightStickMode", mode).apply()
    }

    fun trackpadSensitivity(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat("trackpadSensitivity", 1.0f)

    fun setTrackpadSensitivity(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("trackpadSensitivity", value).apply()
    }

    fun rightStickAntiDeadzone(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat("rightStickAntiDeadzone", 0.15f)

    fun setRightStickAntiDeadzone(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("rightStickAntiDeadzone", value).apply()
    }

    /** Inercia del trackpad derecho del diseño original (0 = sin inercia). */
    fun trackpadInertia(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat("trackpadInertia", 0f)

    fun setTrackpadInertia(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("trackpadInertia", value).apply()
    }

    /** Modo puntero: el ratón se mueve con el dedo (trackpad) en vez de apuntando con el móvil. */
    fun pointerTrackpad(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("pointerTrackpad", false)

    fun setPointerTrackpad(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("pointerTrackpad", value).apply()
    }

    /** Gestos de varios dedos en los touchpads (zoom, vista de tareas…). */
    fun touchGestures(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("touchGestures", true)

    fun setTouchGestures(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("touchGestures", value).apply()
    }

    /** Pistas de primera vez ya vistas («Entendido»). */
    fun hintSeen(context: Context, id: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("hint_$id", false)

    fun setHintSeen(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("hint_$id", true).apply()
    }

    /** Imán de la cuadrícula en el editor de diseños. */
    fun editorSnap(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("editorSnap", true)

    fun setEditorSnap(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("editorSnap", value).apply()
    }

    /** Selector de modos (Puntero / Dolphin / Wii U…) plegado en vertical. */
    fun modeChipsFolded(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("modeChipsFolded", true)

    fun setModeChipsFolded(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("modeChipsFolded", value).apply()
    }

    /** 0 = solo apuntar, 1 = solo el dedo, 2 = los dos (antes era un sí/no). */
    fun pointerTrackpadMode(context: Context): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.contains("pointerTrackpadMode")) p.getInt("pointerTrackpadMode", 0)
        else if (p.getBoolean("pointerTrackpad", false)) 1 else 0
    }

    fun setPointerTrackpadMode(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("pointerTrackpadMode", value).apply()
    }

    fun pointerTrackpadInertia(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat("pointerTrackpadInertia", 0f)

    fun setPointerTrackpadInertia(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("pointerTrackpadInertia", value).apply()
    }

    fun pointerTrackpadSens(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat("pointerTrackpadSens", 1f)

    fun setPointerTrackpadSens(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("pointerTrackpadSens", value).apply()
    }

    /** Zona muerta del stick derecho del diseño original (los perfiles llevan la suya). */
    fun rightStickDeadzone(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat("rightStickDeadzone", StickMap.DEAD_ZONE)

    fun setRightStickDeadzone(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("rightStickDeadzone", value).apply()
    }

    /** Fuerza del clic háptico: [Haptics.OFF]..[Haptics.STRONG]; de serie, nítido. */
    fun hapticLevel(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("hapticLevel", Haptics.CRISP).coerceIn(Haptics.OFF, Haptics.CUSTOM)

    fun setHapticLevel(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("hapticLevel", value.coerceIn(Haptics.OFF, Haptics.CUSTOM)).apply()
    }

    /** ¿Vibra este tipo de control? De serie: botones y cruceta sí; sticks y touchpad no. */
    fun hapticSource(context: Context, source: Haptics.Source): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("haptic_" + source.key, source.default)

    fun setHapticSource(context: Context, source: Haptics.Source, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("haptic_" + source.key, on).apply()
    }

    /** La vibración personalizada. */
    fun hapticCustom(context: Context): Haptics.Custom {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Haptics.Custom(
            strength = p.getFloat("hapticCustomStrength", 0.7f),
            shape = runCatching { Haptics.Shape.valueOf(p.getString("hapticCustomShape", "CLICK") ?: "CLICK") }.getOrDefault(Haptics.Shape.CLICK),
            pulseMs = p.getInt("hapticCustomMs", 14),
            releaseTick = p.getBoolean("hapticCustomRelease", true)
        )
    }

    fun setHapticCustom(context: Context, c: Haptics.Custom) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("hapticCustomStrength", c.strength)
            .putString("hapticCustomShape", c.shape.name)
            .putInt("hapticCustomMs", c.pulseMs)
            .putBoolean("hapticCustomRelease", c.releaseTick)
            .apply()
    }

    /** Sticks flotantes (el centro aparece donde cae el pulgar). De serie, sí. */
    @Volatile
    var floatingSticksDefault: Boolean = true

    fun loadFloatingSticks(context: Context) {
        floatingSticksDefault = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("floatingSticks", true)
    }

    fun setFloatingSticks(context: Context, value: Boolean) {
        floatingSticksDefault = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("floatingSticks", value).apply()
    }

    /** Vibración por el camino del sistema (performHapticFeedback) en vez del clic propio. */
    fun hapticSystem(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("hapticSystem", false)

    fun setHapticSystem(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("hapticSystem", value).apply()
    }

    /** Mando físico (USB, Bluetooth, telescópico): sus botones van al PC. Encendido de serie. */
    fun physicalPad(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("physicalPad", true)

    fun setPhysicalPad(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("physicalPad", value).apply()
    }

    /** Cámara en el espacio libre del diseño original (los perfiles llevan la suya). */
    fun globalLook(context: Context): LookConfig =
        PadProfileJson.lookFromJson(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("globalLook", null)
                ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        )

    fun setGlobalLook(context: Context, value: LookConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("globalLook", PadProfileJson.lookToJson(value).toString()).apply()
    }

    /** Panel de atajos de Teclado y ratón (JSON de [ShortcutDeck]; null = los de fábrica). */
    fun shortcutDeck(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("shortcutDeck", null)

    fun setShortcutDeck(context: Context, json: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("shortcutDeck", json).apply()
    }

    /** Dirección MAC del PC para encenderlo por la red (vacío = sin configurar). */
    fun wolMac(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("wolMac", "") ?: ""

    fun setWolMac(context: Context, mac: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("wolMac", mac).apply()
    }

    /** Tarjetas fijadas arriba en el inicio (ids de [HomeEntry], en orden). */
    fun homeFavorites(context: Context): List<String> =
        HomeLayout.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("homeFavorites", ""))

    fun setHomeFavorites(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("homeFavorites", HomeLayout.join(ids)).apply()
    }

    /** Último modo abierto desde el inicio (id de [HomeEntry]) para «Continuar». */
    fun homeLast(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("homeLast", null)

    fun setHomeLast(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("homeLast", id).apply()
    }

    /** Teclado y ratón: por dónde va de serie ("app", "bt"; vacío = preguntar). */
    fun kbmTransport(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("kbmTransport", "") ?: ""

    fun setKbmTransport(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("kbmTransport", value).apply()
    }

    /** Modo ratón del diseño original (los perfiles llevan el suyo). */
    fun globalMouse(context: Context): MouseConfig =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("globalMouse", null)
            ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            ?.let { PadProfileJson.mouseFromJson(it) } ?: MouseConfig()

    fun setGlobalMouse(context: Context, value: MouseConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("globalMouse", PadProfileJson.mouseToJson(value).toString()).apply()
    }

    /** Giroscopio del diseño original (los perfiles llevan el suyo). */
    fun globalGyro(context: Context): GyroConfig =
        PadProfileJson.gyroFromJson(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("globalGyro", null)
                ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        )

    fun setGlobalGyro(context: Context, value: GyroConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("globalGyro", PadProfileJson.gyroToJson(value).toString()).apply()
    }

    /** Sin bloqueo de orientación en el mando. */
    const val ORIENTATION_FREE = Int.MIN_VALUE

    /** Orientación fijada con el candado del mando (`ActivityInfo.SCREEN_ORIENTATION_*`), o [ORIENTATION_FREE]. */
    fun orientationLock(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("orientationLock", ORIENTATION_FREE)

    fun setOrientationLock(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("orientationLock", value).apply()
    }

    /**
     * Nunchuk en el mismo móvil (modo Dolphin): el mando lleva su propio
     * Nunchuk y en apaisado sale el trazado con stick, C y Z. Apagado de
     * serie (entrar en Dolphin nunca lo enciende solo); se recuerda porque
     * cambiarlo obliga a reabrir Dolphin.
     */
    fun ownNunchuk(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("ownNunchuk", false)

    /**
     * Avisos del receptor en pantalla («Dolphin configurado…», «Cemu está
     * abierto…»): unos segundos sobre el mando al cambiar de modo. Apagados,
     * no se enseñan (los avisos locales de una petición fallida, sí).
     */
    fun receiverNotices(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("receiverNotices", true)

    fun setReceiverNotices(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("receiverNotices", value).apply()
    }

    fun setOwnNunchuk(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("ownNunchuk", value).apply()
    }

    const val MOTION_AUTO = ""
    const val MOTION_GYRO = "gyro"
    const val MOTION_ACCEL = "accel"

    /**
     * Sensor con el que el puntero mueve el cursor: "" (automático: giroscopio
     * si es real, si no acelerómetro), "gyro" o "accel"
     * ([dev.pepotech.pepomote.sensor.MotionSource]).
     */
    fun motionSource(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("motionSource", MOTION_AUTO) ?: MOTION_AUTO

    fun setMotionSource(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("motionSource", value).apply()
    }

    const val PAD_AIM_ASK = ""
    const val PAD_AIM_ON = "on"
    const val PAD_AIM_OFF = "off"

    /**
     * Mando universal: mover el móvil mueve el stick derecho del mando de
     * Xbox. "" hasta que se elige (el mando lo pregunta la primera vez que
     * conecta), "on" u "off"; Ajustes lo cambia cuando se quiera. Sin elegir
     * va apagado: así ningún juego hace cosas raras mientras se lee el aviso.
     * Solo vale para ese modo (el puntero, Cemu, Dolphin, Switch y RetroArch
     * apuntan con el giro igual que siempre).
     */
    fun padAim(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("padAim", PAD_AIM_ASK) ?: PAD_AIM_ASK

    fun padAimEnabled(context: Context): Boolean = padAim(context) == PAD_AIM_ON

    /** Guarda la elección, y con ella deja de preguntarse. */
    fun setPadAim(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("padAim", if (enabled) PAD_AIM_ON else PAD_AIM_OFF).apply()
    }

    /**
     * «Vibración en los juegos»: cuánto de lo que pide el juego llega al
     * motor ("high", "normal", "low", "off"). La misma cadena que en iOS y en
     * Linux móvil; un valor desconocido se lee como "normal" ([RumblePref]).
     */
    fun gameRumble(context: Context): String =
        RumblePref.normalize(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("gameRumble", null)
        )

    /** Guarda el ajuste y se lo pasa en vivo al motor: Apagada para al instante ([GameRumble.scale]). */
    fun setGameRumble(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("gameRumble", RumblePref.normalize(value)).apply()
        GameRumble.scale = RumblePref.scale(value)
    }

    /** El aviso de «sin giroscopio real» del modo puntero ya se enseñó (una vez por instalación). */
    fun gyroWarnShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("gyroWarnShown", false)

    fun setGyroWarnShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("gyroWarnShown", true).apply()
    }

    /**
     * Lado de un mando apaisado fijo ([dev.pepotech.pepomote.service.LandscapeSide]):
     * "" hasta que se elige la primera vez; "left", "right" o "sensor"
     * (Ajustes). [key]: "nunchukSide" (mando + Nunchuk) o "gamePadSide" (GamePad).
     */
    fun landscapeSide(context: Context, key: String): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "") ?: ""

    fun setLandscapeSide(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }

    /** GamePad de Wii U sin pantalla táctil (ni doble pantalla): los botones crecen (Ajustes). */
    fun gamePadNoScreen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("gamePadNoScreen", false)

    fun setGamePadNoScreen(context: Context, value: Boolean) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("gamePadNoScreen", value)
        // excluyente con la pantalla completa
        if (value) e.putBoolean("gamePadFullScreen", false)
        e.apply()
    }

    /**
     * Pantalla del GamePad a pantalla completa: solo la pantalla de Cemu y el
     * táctil, sin sticks ni botones (el mando real va en el PC). Ajustes.
     */
    fun gamePadFullScreen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("gamePadFullScreen", false)

    fun setGamePadFullScreen(context: Context, value: Boolean) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("gamePadFullScreen", value)
        // excluyente con «GamePad sin pantalla táctil»
        if (value) e.putBoolean("gamePadNoScreen", false)
        e.apply()
    }

    /** En pantalla completa, botón de teclado arriba a la derecha (Ajustes). */
    fun gamePadFullScreenKeyboard(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("gamePadFullScreenKeyboard", true)

    fun setGamePadFullScreenKeyboard(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("gamePadFullScreenKeyboard", value).apply()
    }

    /** Mostrar el selector Puntero/Dolphin en el mando al entrar por Conectar. */
    fun showDolphinChips(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("showDolphinChips", true)

    fun setShowDolphinChips(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("showDolphinChips", value).apply()
    }

    /**
     * «Multimedia en todos los modos»: la fila multimedia del mando vertical
     * sale siempre en modo puntero (el único en el que el PC atiende esas
     * teclas); con esto encendido sale también en Dolphin, Wii U y RetroArch.
     * Apagado de serie.
     */
    fun mediaEverywhere(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("mediaEverywhere", false)

    fun setMediaEverywhere(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("mediaEverywhere", value).apply()
    }

    fun soundsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("sounds", true)

    fun setSoundsEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("sounds", value).apply()
        UiSounds.enabled = value
    }

    /**
     * «Pulsar deslizando»: el botón por el que pasa el dedo se pulsa; al
     * salir se suelta y se pulsa el siguiente (un dedo que nace en el vacío
     * también pulsa al entrar). Apagado de serie; encendido manda sobre
     * [stickyPress].
     */
    fun slidePress(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("slidePress", false)

    fun setSlidePress(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("slidePress", value).apply()
        PressMode.slide = value
    }

    /**
     * «Mantener al salir del botón»: un botón pulsado sigue pulsado mientras
     * no se levante el dedo, aunque se salga (el «2» de Mario Kart). Encendido
     * de serie: iOS y Linux ya eran así.
     */
    fun stickyPress(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("stickyPress", true)

    fun setStickyPress(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("stickyPress", value).apply()
        PressMode.sticky = value
    }

    const val LANG_SYSTEM = "system"

    /** Idioma: "system", "es" o "en". */
    fun lang(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("lang", LANG_SYSTEM) ?: LANG_SYSTEM

    fun setLang(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("lang", code).apply()
    }

    fun onboarded(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("onboarded", false)

    fun setOnboarded(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("onboarded", true).apply()
    }

    // --- aviso de versión nueva (UpdateCheck / UpdateNotice)

    /** Consultar versiones y novedades en GitHub al inicio y cada hora de uso (Ajustes). */
    fun updateCheckEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("updateCheck", false) // RevolutionMote+: las versiones de PepoMote son otra app

    fun setUpdateCheckEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("updateCheck", value).apply()
    }

    /** Versión anunciada que se ocultó ("" = ninguna). */
    fun updateDismissed(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("updateDismissed", "") ?: ""

    fun setUpdateDismissed(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("updateDismissed", version).apply()
    }

    /** Última consulta (ms UNIX, 0 = nunca) y última versión publicada vista. */
    fun updateLastMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("updateLastMs", 0L)

    fun updateLatest(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("updateLatest", "") ?: ""

    fun setUpdateResult(context: Context, nowMs: Long, latest: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong("updateLastMs", nowMs).putString("updateLatest", latest).apply()
    }
}
