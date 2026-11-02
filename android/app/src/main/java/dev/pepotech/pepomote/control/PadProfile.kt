package dev.pepotech.pepomote.control

import org.json.JSONArray
import org.json.JSONObject

/** Qué es cada control del diseño personalizado. */
enum class ElementKind {
    BUTTON, STICK_L, STICK_R, DPAD, FACE, GYRO,

    /** Touchpad: en modo ratón mueve el cursor (toque = clic, dos dedos = clic derecho y rueda); en modo mando, la cámara. */
    MOUSE_PAD,

    /** Clic izquierdo / derecho (solo en modo ratón). */
    MOUSE_LEFT, MOUSE_RIGHT,

    /** Abre el teclado para escribir en el PC. */
    KEYBOARD,

    /** Cambia entre mando y ratón (como el botón de los juegos). */
    MODE_TOGGLE,

    /** Una tecla del teclado ([PadElement.key]: «KeyW», «Escape»…). Varias a la vez. */
    KEY,

    /** Mantener: giroscopio, trackpads y ratón más lentos para apuntar fino. */
    PRECISION,

    /** Tocar: el cursor al centro (modo ratón) o la cámara sin arrastre del giro. */
    RECENTER
}

/**
 * Modo ratón del mando universal: el cursor se mueve de forma RELATIVA
 * (como un ratón), con el giro del móvil y/o los touchpads, calculado en el
 * móvil. Así el puntero no se descentra con el uso.
 */
data class MouseConfig(
    val gyro: Boolean = true,
    val gyroSens: Float = 1f,
    val padSens: Float = 1f,
    val inertia: Float = 0f,
    val smoothing: Float = 0.5f,
    val invertY: Boolean = false
)

/** Dónde gira la cámara un dedo que cae fuera de cualquier control (como en CoD Mobile). */
enum class LookZone { OFF, RIGHT, ALL }

/**
 * Cámara en el espacio libre: cualquier hueco sin controles es un trackpad
 * que mueve el stick derecho (velocidad del dedo = giro).
 */
data class LookConfig(
    val zone: LookZone = LookZone.ALL,
    val sensitivity: Float = 1f,
    val antiDeadzone: Float = 0.15f,
    val invertY: Boolean = false,
    val inertia: Float = 0f
)

/** Cuándo apunta el giroscopio (si está encendido). */
enum class GyroActivation {
    /** Siempre que el mando está abierto. */
    ALWAYS,

    /** Mientras se mantiene ZL / LT (táctil o mando físico). */
    HOLD_LT,

    /** Mientras se mantiene el botón 🎯 de la pantalla. */
    HOLD_BUTTON,

    /** Un toque al botón 🎯 lo enciende y otro lo apaga. */
    TOGGLE_BUTTON,

    /** Cada pulsación de ZL / LT lo enciende o lo apaga. */
    TOGGLE_LT
}

/**
 * Apuntar con el giroscopio (mando universal): el giro del móvil se convierte
 * en el MÓVIL en stick derecho, con giro horizontal de verdad.
 */
data class GyroConfig(
    val enabled: Boolean = false,
    val activation: GyroActivation = GyroActivation.ALWAYS,
    /** Sensibilidad con la pantalla en horizontal (apaisado). */
    val sensX: Float = 2.1f,
    val sensY: Float = 1.9f,
    /** Sensibilidad con la pantalla en vertical. */
    val sensXPortrait: Float = 2.1f,
    val sensYPortrait: Float = 1.9f,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val antiDeadzone: Float = 0.12f,
    val smoothing: Float = 0.9f,
    /** Por si en algún móvil el giro horizontal y el vertical salen cruzados. */
    val swapAxes: Boolean = false
) {
    val needsButton: Boolean
        get() = enabled && (activation == GyroActivation.HOLD_BUTTON || activation == GyroActivation.TOGGLE_BUTTON)
}

/**
 * Un control colocado a mano. [x]/[y]: el CENTRO, en fracción del ancho y
 * del alto de la zona de controles (0..1); así el diseño se adapta a otra
 * pantalla sin salirse. [size]: el lado (o el alto, en las pastillas) en
 * fracción del alto de esa zona.
 *
 * Los sticks llevan su zona muerta, anti-zona muerta, sensibilidad y si van
 * como trackpad; los botones, su función ([bit]), si son pastilla
 * ([pill], como los gatillos) y si llevan turbo.
 */
data class PadElement(
    val kind: ElementKind,
    val x: Float,
    val y: Float,
    val size: Float,
    val bit: Int = 0,
    val pill: Boolean = false,
    val deadzone: Float = StickMap.DEAD_ZONE,
    val antiDeadzone: Float = 0f,
    val sensitivity: Float = 1f,
    val trackpad: Boolean = false,
    val turbo: Boolean = false,
    /** Trackpad: cuánto sigue girando al soltar (0 = sin inercia, 1 = casi sin frenar). */
    val inertia: Float = 0f,
    /** Tecla del teclado (solo [ElementKind.KEY]). */
    val key: String = "",
    /** Stick flotante: el centro aparece donde cae el pulgar. */
    val floating: Boolean = true
) {
    /** Único dentro de un perfil: un tipo de control, un bit o una tecla no se repiten. */
    val id: String get() = when (kind) {
        ElementKind.BUTTON -> "btn_$bit"
        ElementKind.KEY -> "key_$key"
        else -> kind.name
    }

    val isStick: Boolean get() = kind == ElementKind.STICK_L || kind == ElementKind.STICK_R
}

data class PadProfile(
    val name: String,
    val elements: List<PadElement>,
    /** Opacidad de todos los controles (0.15..1). */
    val opacity: Float = 1f,
    val look: LookConfig = LookConfig(),
    val gyro: GyroConfig = GyroConfig(),
    val mouse: MouseConfig = MouseConfig(),
    /** Rótulos del rombo: «xbox», «nintendo» o «playstation» (solo cambian los nombres). */
    val faceStyle: String = FaceStyle.XBOX,
    /** Al usar este perfil, el mando arranca en modo ratón (el perfil Touchpad). */
    val startMouse: Boolean = false
) {
    fun replace(old: PadElement, new: PadElement) =
        copy(elements = elements.map { if (it.id == old.id) new else it })

    fun remove(element: PadElement) = copy(elements = elements.filter { it.id != element.id })

    fun add(element: PadElement) = copy(elements = elements.filter { it.id != element.id } + element)
}

/** Lo que se puede añadir al diseño: un tipo de control o un botón suelto. */
data class AddOption(val kind: ElementKind, val bit: Int = 0)

/** Teclas que se pueden poner en el mando (nombre `KeyboardEvent.code` → rótulo). */
/** Rótulos del rombo del mando universal. Solo cambian los NOMBRES: cada botón sigue enviando el de su sitio. */
object FaceStyle {
    const val XBOX = "xbox"
    const val NINTENDO = "nintendo"
    const val PLAYSTATION = "playstation"
    val ALL = listOf(XBOX, NINTENDO, PLAYSTATION)

    /** Rótulo del botón [bit] con el estilo [style] (null = el de siempre). */
    fun label(style: String, bit: Int): String? = when (style) {
        NINTENDO -> when (bit) {
            ButtonState.A -> "B"
            ButtonState.B -> "A"
            ButtonState.X -> "Y"
            ButtonState.Y -> "X"
            else -> null
        }
        PLAYSTATION -> when (bit) {
            ButtonState.A -> "✕"
            ButtonState.B -> "○"
            ButtonState.X -> "□"
            ButtonState.Y -> "△"
            else -> null
        }
        else -> null
    }
}

/** Diseños de partida para un perfil nuevo. */
enum class PadPreset { XBOX, NINTENDO, PLAYSTATION, TOUCHPAD }

object PadKeys {
    val ALL: List<Pair<String, String>> = listOf(
        "Escape" to "Esc", "Tab" to "Tab", "Enter" to "Intro", "Space" to "Espacio", "Backspace" to "Borrar",
        "ShiftLeft" to "Shift", "ControlLeft" to "Ctrl", "AltLeft" to "Alt", "MetaLeft" to "Win",
        "KeyW" to "W", "KeyA" to "A", "KeyS" to "S", "KeyD" to "D", "KeyE" to "E", "KeyQ" to "Q",
        "KeyR" to "R", "KeyF" to "F", "KeyG" to "G", "KeyC" to "C", "KeyV" to "V", "KeyZ" to "Z",
        "KeyX" to "X", "KeyM" to "M", "KeyI" to "I", "KeyP" to "P",
        "Digit1" to "1", "Digit2" to "2", "Digit3" to "3", "Digit4" to "4", "Digit5" to "5",
        "ArrowUp" to "↑", "ArrowDown" to "↓", "ArrowLeft" to "←", "ArrowRight" to "→",
        "F1" to "F1", "F2" to "F2", "F3" to "F3", "F4" to "F4", "F5" to "F5", "F6" to "F6",
        "F7" to "F7", "F8" to "F8", "F9" to "F9", "F10" to "F10", "F11" to "F11", "F12" to "F12",
        "Delete" to "Supr", "Home" to "Inicio", "End" to "Fin", "PageUp" to "RePág", "PageDown" to "AvPág"
    )

    /** Los rótulos que cambian en inglés (los demás son iguales). */
    private val EN = mapOf(
        "Enter" to "Enter", "Space" to "Space", "Backspace" to "Bksp", "Delete" to "Del",
        "Home" to "Home", "End" to "End", "PageUp" to "PgUp", "PageDown" to "PgDn"
    )

    /** Rótulo de la tecla en el idioma de la app ([lang]: «es», «en»…). */
    fun label(name: String, lang: String = "es"): String =
        (if (lang != "es") EN[name] else null) ?: ALL.firstOrNull { it.first == name }?.second ?: name
}

/**
 * Reglas y valores de fábrica del diseño personalizado. Puro (JSON aparte):
 * testeable sin Android.
 */
object PadProfiles {
    const val MIN_SIZE = 0.06f
    const val MAX_SIZE = 0.8f
    const val MIN_OPACITY = 0.15f
    const val MIN_SENS = 0.1f
    const val MAX_SENS = 10f

    /** Botones sueltos que se pueden poner, en el orden de la lista «Añadir». */
    val BUTTON_BITS = listOf(
        ButtonState.A, ButtonState.B, ButtonState.X, ButtonState.Y,
        ButtonState.L, ButtonState.R, ButtonState.ZL, ButtonState.ZR,
        ButtonState.STICK_L, ButtonState.STICK_R,
        ButtonState.MINUS, ButtonState.PLUS, ButtonState.HOME, ButtonState.SCREEN
    )

    /** Gatillos y hombros van como pastilla; lo demás, redondo. */
    fun isPillBit(bit: Int): Boolean =
        bit == ButtonState.L || bit == ButtonState.R || bit == ButtonState.ZL || bit == ButtonState.ZR

    /** Los bits que ya tiene el diseño (el rombo cuenta con sus botones). */
    fun usedBits(profile: PadProfile, faceBits: List<Int>): Set<Int> {
        val used = HashSet<Int>()
        for (e in profile.elements) {
            if (e.kind == ElementKind.BUTTON) used += e.bit
            if (e.kind == ElementKind.FACE) used += faceBits
        }
        return used
    }

    /**
     * Lo que falta y se puede añadir. Un bit no se pone dos veces (la capa
     * de pulsación de la pantalla va por bit), y el rombo solo si ninguno de
     * sus botones está ya suelto.
     */
    fun addable(profile: PadProfile, faceBits: List<Int>): List<AddOption> {
        val kinds = profile.elements.map { it.kind }.toSet()
        val used = usedBits(profile, faceBits)
        val out = ArrayList<AddOption>()
        for (k in listOf(
            ElementKind.STICK_L, ElementKind.STICK_R, ElementKind.DPAD, ElementKind.GYRO,
            ElementKind.MODE_TOGGLE, ElementKind.MOUSE_PAD, ElementKind.MOUSE_LEFT, ElementKind.MOUSE_RIGHT,
            ElementKind.KEYBOARD, ElementKind.PRECISION, ElementKind.RECENTER
        )) {
            if (k !in kinds) out += AddOption(k)
        }
        // Teclas: siempre se puede poner otra (la primera libre de la lista)
        out += AddOption(ElementKind.KEY)
        if (ElementKind.FACE !in kinds && faceBits.isNotEmpty() && faceBits.none { it in used }) {
            out += AddOption(ElementKind.FACE)
        }
        for (bit in BUTTON_BITS) if (bit !in used) out += AddOption(ElementKind.BUTTON, bit)
        return out
    }

    /** Bits a los que se puede cambiar la función de un botón (el suyo incluido). */
    fun remapTargets(profile: PadProfile, element: PadElement, faceBits: List<Int>): List<Int> {
        val used = usedBits(profile, faceBits) - element.bit
        return BUTTON_BITS.filter { it !in used }
    }

    /** Un control nuevo, en el centro y con su tamaño típico. */
    /** La primera tecla de la lista que aún no está en el diseño. */
    fun freeKey(profile: PadProfile): String {
        val used = profile.elements.filter { it.kind == ElementKind.KEY }.map { it.key }.toSet()
        return PadKeys.ALL.firstOrNull { it.first !in used }?.first ?: "Escape"
    }

    /**
     * El perfil «Touchpad»: un touchpad grande en el centro, los dos clics a
     * los lados, teclado, el interruptor mando / ratón, el giro y las teclas
     * más usadas. Arranca en modo ratón.
     */
    fun touchpad(name: String, look: LookConfig = LookConfig(), gyro: GyroConfig = GyroConfig()): PadProfile = PadProfile(
        name = name,
        elements = listOf(
            PadElement(ElementKind.MOUSE_PAD, 0.5f, 0.55f, 0.62f, pill = true),
            PadElement(ElementKind.MOUSE_LEFT, 0.11f, 0.72f, 0.2f),
            PadElement(ElementKind.MOUSE_RIGHT, 0.89f, 0.72f, 0.2f),
            PadElement(ElementKind.KEYBOARD, 0.09f, 0.3f, 0.15f),
            PadElement(ElementKind.MODE_TOGGLE, 0.91f, 0.3f, 0.15f),
            PadElement(ElementKind.KEY, 0.25f, 0.12f, 0.12f, key = "Escape"),
            PadElement(ElementKind.KEY, 0.75f, 0.12f, 0.12f, key = "Enter"),
            PadElement(ElementKind.PRECISION, 0.23f, 0.92f, 0.12f),
            PadElement(ElementKind.RECENTER, 0.77f, 0.92f, 0.12f)
        ),
        look = look,
        gyro = gyro,
        startMouse = true
    )

    fun newElement(option: AddOption): PadElement = when (option.kind) {
        ElementKind.STICK_L, ElementKind.STICK_R -> PadElement(option.kind, 0.5f, 0.5f, 0.42f)
        ElementKind.DPAD -> PadElement(option.kind, 0.5f, 0.5f, 0.36f)
        ElementKind.FACE -> PadElement(option.kind, 0.5f, 0.5f, 0.42f)
        ElementKind.GYRO, ElementKind.MODE_TOGGLE, ElementKind.KEYBOARD, ElementKind.PRECISION, ElementKind.RECENTER,
        ElementKind.MOUSE_LEFT, ElementKind.MOUSE_RIGHT -> PadElement(option.kind, 0.5f, 0.5f, 0.15f)
        ElementKind.MOUSE_PAD -> PadElement(option.kind, 0.5f, 0.5f, 0.45f, pill = true)
        ElementKind.KEY -> PadElement(option.kind, 0.5f, 0.5f, 0.13f, key = "Escape")
        ElementKind.BUTTON -> PadElement(
            ElementKind.BUTTON, 0.5f, 0.5f,
            0.13f,
            bit = option.bit,
            pill = isPillBit(option.bit)
        )
    }

    /**
     * El diseño de fábrica, a partir de la plantilla del mando que se ve
     * ahora: los mismos controles, en el sitio de un mando de verdad.
     */
    fun factory(
        name: String,
        hasLeftStick: Boolean,
        hasRightStick: Boolean,
        stickClicks: Boolean,
        shoulderBits: List<Int>,
        centerBits: List<Int>,
        hasCapture: Boolean
    ): PadProfile {
        val e = ArrayList<PadElement>()
        fun btn(bit: Int, x: Float, y: Float, size: Float = 0.13f) {
            e += PadElement(ElementKind.BUTTON, x, y, size, bit = bit, pill = isPillBit(bit))
        }
        if (ButtonState.ZL in shoulderBits) btn(ButtonState.ZL, 0.08f, 0.09f)
        if (ButtonState.L in shoulderBits) btn(ButtonState.L, 0.08f, 0.26f)
        if (ButtonState.ZR in shoulderBits) btn(ButtonState.ZR, 0.92f, 0.09f)
        if (ButtonState.R in shoulderBits) btn(ButtonState.R, 0.92f, 0.26f)
        if (hasLeftStick) e += PadElement(ElementKind.STICK_L, 0.15f, 0.58f, 0.42f)
        e += PadElement(ElementKind.DPAD, 0.32f, 0.80f, 0.34f)
        e += PadElement(ElementKind.FACE, 0.85f, 0.58f, 0.42f)
        if (hasRightStick) e += PadElement(ElementKind.STICK_R, 0.68f, 0.80f, 0.40f)
        if (stickClicks && hasLeftStick) btn(ButtonState.STICK_L, 0.28f, 0.42f, 0.11f)
        if (stickClicks && hasRightStick) btn(ButtonState.STICK_R, 0.72f, 0.42f, 0.11f)
        // Back a la izquierda, Home en medio y Start a la derecha
        val own = centerBits.filter { it != ButtonState.HOME }
        val center = (own.take(own.size / 2) + ButtonState.HOME + own.drop(own.size / 2)).distinct()
        val step = 0.085f
        val first = 0.5f - step * (center.size - 1) / 2f
        for ((i, bit) in center.withIndex()) btn(bit, first + step * i, 0.30f, 0.11f)
        if (hasCapture) btn(ButtonState.SCREEN, 0.5f, 0.52f, 0.11f)
        return PadProfile(name, e)
    }

    /** «Mi diseño», «Mi diseño 2»… sin chocar con los que ya hay. */
    fun uniqueName(base: String, taken: Collection<String>): String {
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    fun clampElement(e: PadElement): PadElement = e.copy(
        x = e.x.coerceIn(0f, 1f),
        y = e.y.coerceIn(0f, 1f),
        size = e.size.coerceIn(MIN_SIZE, MAX_SIZE),
        deadzone = e.deadzone.coerceIn(0f, 0.5f),
        antiDeadzone = e.antiDeadzone.coerceIn(0f, 0.9f),
        sensitivity = e.sensitivity.coerceIn(MIN_SENS, MAX_SENS),
        inertia = e.inertia.coerceIn(0f, 1f)
    )
}

/** Perfiles ⇄ JSON (org.json, el de Android). */
object PadProfileJson {
    fun toJson(p: PadProfile): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("opacity", p.opacity.toDouble())
        put("look", lookToJson(p.look))
        put("gyro", gyroToJson(p.gyro))
        put("mouse", mouseToJson(p.mouse))
        put("face", p.faceStyle)
        if (p.startMouse) put("startMouse", true)
        put("elements", JSONArray().apply {
            for (e in p.elements) put(JSONObject().apply {
                put("kind", e.kind.name)
                put("x", e.x.toDouble())
                put("y", e.y.toDouble())
                put("size", e.size.toDouble())
                put("bit", e.bit)
                put("pill", e.pill)
                put("dz", e.deadzone.toDouble())
                put("adz", e.antiDeadzone.toDouble())
                put("sens", e.sensitivity.toDouble())
                put("trackpad", e.trackpad)
                put("turbo", e.turbo)
                put("inertia", e.inertia.toDouble())
                if (e.key.isNotEmpty()) put("key", e.key)
                if (!e.floating) put("float", false)
            })
        })
    }

    fun fromJson(o: JSONObject): PadProfile? = try {
        val arr = o.getJSONArray("elements")
        val elements = ArrayList<PadElement>()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            val kind = runCatching { ElementKind.valueOf(j.getString("kind")) }.getOrNull() ?: continue
            elements += PadProfiles.clampElement(
                PadElement(
                    kind = kind,
                    x = j.optDouble("x", 0.5).toFloat(),
                    y = j.optDouble("y", 0.5).toFloat(),
                    size = j.optDouble("size", 0.2).toFloat(),
                    bit = j.optInt("bit", 0),
                    pill = j.optBoolean("pill", false),
                    deadzone = j.optDouble("dz", StickMap.DEAD_ZONE.toDouble()).toFloat(),
                    antiDeadzone = j.optDouble("adz", 0.0).toFloat(),
                    sensitivity = j.optDouble("sens", 1.0).toFloat(),
                    trackpad = j.optBoolean("trackpad", false),
                    turbo = j.optBoolean("turbo", false),
                    inertia = j.optDouble("inertia", 0.0).toFloat(),
                    key = j.optString("key", ""),
                    floating = j.optBoolean("float", true)
                )
            )
        }
        PadProfile(
            name = o.getString("name"),
            elements = elements.distinctBy { it.id },
            opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(PadProfiles.MIN_OPACITY, 1f),
            look = lookFromJson(o.optJSONObject("look")),
            gyro = gyroFromJson(o.optJSONObject("gyro")),
            mouse = o.optJSONObject("mouse")?.let { mouseFromJson(it) } ?: MouseConfig(),
            faceStyle = o.optString("face", FaceStyle.XBOX).takeIf { it in FaceStyle.ALL } ?: FaceStyle.XBOX,
            startMouse = o.optBoolean("startMouse", false)
        )
    } catch (_: Exception) {
        null
    }

    fun gyroToJson(g: GyroConfig): JSONObject = JSONObject().apply {
        put("on", g.enabled)
        put("act", g.activation.name)
        put("sx", g.sensX.toDouble())
        put("sy", g.sensY.toDouble())
        put("ix", g.invertX)
        put("iy", g.invertY)
        put("adz", g.antiDeadzone.toDouble())
        put("smooth", g.smoothing.toDouble())
        put("swap", g.swapAxes)
        put("sxp", g.sensXPortrait.toDouble())
        put("syp", g.sensYPortrait.toDouble())
    }

    fun gyroFromJson(j: JSONObject?): GyroConfig = if (j == null) GyroConfig() else GyroConfig(
        enabled = j.optBoolean("on", false),
        activation = runCatching { GyroActivation.valueOf(j.getString("act")) }.getOrDefault(GyroActivation.ALWAYS),
        sensX = j.optDouble("sx", 2.1).toFloat().coerceIn(PadProfiles.MIN_SENS, PadProfiles.MAX_SENS),
        sensY = j.optDouble("sy", 1.9).toFloat().coerceIn(PadProfiles.MIN_SENS, PadProfiles.MAX_SENS),
        invertX = j.optBoolean("ix", false),
        invertY = j.optBoolean("iy", false),
        antiDeadzone = j.optDouble("adz", 0.12).toFloat().coerceIn(0f, 0.9f),
        smoothing = j.optDouble("smooth", 0.9).toFloat().coerceIn(0f, 0.97f),
        swapAxes = j.optBoolean("swap", false),
        sensXPortrait = j.optDouble("sxp", 2.1).toFloat().coerceIn(PadProfiles.MIN_SENS, PadProfiles.MAX_SENS),
        sensYPortrait = j.optDouble("syp", 1.9).toFloat().coerceIn(PadProfiles.MIN_SENS, PadProfiles.MAX_SENS)
    )

    fun mouseToJson(m: MouseConfig): JSONObject = JSONObject().apply {
        put("gyro", m.gyro)
        put("gs", m.gyroSens.toDouble())
        put("ps", m.padSens.toDouble())
        put("inertia", m.inertia.toDouble())
        put("smooth", m.smoothing.toDouble())
        put("iy", m.invertY)
    }

    fun mouseFromJson(m: JSONObject): MouseConfig = MouseConfig(
        gyro = m.optBoolean("gyro", true),
        gyroSens = m.optDouble("gs", 1.0).toFloat().coerceIn(PadProfiles.MIN_SENS, PadProfiles.MAX_SENS),
        padSens = m.optDouble("ps", 1.0).toFloat().coerceIn(PadProfiles.MIN_SENS, PadProfiles.MAX_SENS),
        inertia = m.optDouble("inertia", 0.0).toFloat().coerceIn(0f, 1f),
        smoothing = m.optDouble("smooth", 0.5).toFloat().coerceIn(0f, 0.97f),
        invertY = m.optBoolean("iy", false)
    )

    fun lookToJson(l: LookConfig): JSONObject = JSONObject().apply {
        put("zone", l.zone.name)
        put("sens", l.sensitivity.toDouble())
        put("adz", l.antiDeadzone.toDouble())
        put("invY", l.invertY)
        put("inertia", l.inertia.toDouble())
    }

    fun lookFromJson(j: JSONObject?): LookConfig = if (j == null) LookConfig() else LookConfig(
        zone = runCatching { LookZone.valueOf(j.getString("zone")) }.getOrDefault(LookZone.ALL),
        sensitivity = j.optDouble("sens", 1.0).toFloat().coerceIn(PadProfiles.MIN_SENS, PadProfiles.MAX_SENS),
        antiDeadzone = j.optDouble("adz", 0.15).toFloat().coerceIn(0f, 0.9f),
        invertY = j.optBoolean("invY", false),
        inertia = j.optDouble("inertia", 0.0).toFloat().coerceIn(0f, 1f)
    )

    fun listToString(list: List<PadProfile>): String =
        JSONArray().apply { for (p in list) put(toJson(p)) }.toString()

    fun listFromString(s: String?): List<PadProfile> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
