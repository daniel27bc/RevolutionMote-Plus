package dev.pepotech.pepomote.control

import org.json.JSONArray
import org.json.JSONObject

/** Qué es cada control del diseño personalizado. */
enum class ElementKind { BUTTON, STICK_L, STICK_R, DPAD, FACE }

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
    val turbo: Boolean = false
) {
    /** Único dentro de un perfil: un tipo de control o un bit no se repiten. */
    val id: String get() = if (kind == ElementKind.BUTTON) "btn_$bit" else kind.name

    val isStick: Boolean get() = kind == ElementKind.STICK_L || kind == ElementKind.STICK_R
}

data class PadProfile(
    val name: String,
    val elements: List<PadElement>,
    /** Opacidad de todos los controles (0.15..1). */
    val opacity: Float = 1f
) {
    fun replace(old: PadElement, new: PadElement) =
        copy(elements = elements.map { if (it.id == old.id) new else it })

    fun remove(element: PadElement) = copy(elements = elements.filter { it.id != element.id })

    fun add(element: PadElement) = copy(elements = elements.filter { it.id != element.id } + element)
}

/** Lo que se puede añadir al diseño: un tipo de control o un botón suelto. */
data class AddOption(val kind: ElementKind, val bit: Int = 0)

/**
 * Reglas y valores de fábrica del diseño personalizado. Puro (JSON aparte):
 * testeable sin Android.
 */
object PadProfiles {
    const val MIN_SIZE = 0.06f
    const val MAX_SIZE = 0.8f
    const val MIN_OPACITY = 0.15f

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
        for (k in listOf(ElementKind.STICK_L, ElementKind.STICK_R, ElementKind.DPAD)) {
            if (k !in kinds) out += AddOption(k)
        }
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
    fun newElement(option: AddOption): PadElement = when (option.kind) {
        ElementKind.STICK_L, ElementKind.STICK_R -> PadElement(option.kind, 0.5f, 0.5f, 0.42f)
        ElementKind.DPAD -> PadElement(option.kind, 0.5f, 0.5f, 0.36f)
        ElementKind.FACE -> PadElement(option.kind, 0.5f, 0.5f, 0.42f)
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
        val center = (centerBits + ButtonState.HOME).distinct()
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
        antiDeadzone = e.antiDeadzone.coerceIn(0f, 0.6f),
        sensitivity = e.sensitivity.coerceIn(0.2f, 3f)
    )
}

/** Perfiles ⇄ JSON (org.json, el de Android). */
object PadProfileJson {
    fun toJson(p: PadProfile): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("opacity", p.opacity.toDouble())
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
                    turbo = j.optBoolean("turbo", false)
                )
            )
        }
        PadProfile(
            name = o.getString("name"),
            elements = elements.distinctBy { it.id },
            opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(PadProfiles.MIN_OPACITY, 1f)
        )
    } catch (_: Exception) {
        null
    }

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
