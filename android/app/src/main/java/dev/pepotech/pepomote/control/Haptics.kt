package dev.pepotech.pepomote.control

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Háptico de un botón, la cruceta o el stick. En vez del zumbido genérico,
 * un clic corto y seco como el de un microswitch:
 *
 * - Android 11+ con motor que lo soporte: la primitiva CLICK (o TICK para
 *   el stick) con la fuerza del ajuste. Es lo más parecido a un botón real.
 * - Android 10: los efectos predefinidos CLICK / TICK / HEAVY_CLICK.
 * - Más antiguo: un pulso de 10–16 ms a la amplitud del ajuste.
 *
 * Con «Nítida» y «Fuerte» también hay un tic suave al SOLTAR, como el
 * rebote de un botón de verdad. Nivel 0 = sin vibración.
 *
 * Mientras vibra el juego no se dispara: con la onda finita de [GameRumble]
 * un toque propio la cortaría hasta la renovación.
 */
object Haptics {
    const val OFF = 0
    const val SUBTLE = 1
    const val CRISP = 2
    const val STRONG = 3

    /** Personalizada: fuerza, tipo de golpe y duración a gusto ([Custom]). */
    const val CUSTOM = 4

    /** Qué control pide la vibración: cada uno se enciende o apaga aparte. */
    enum class Source(val key: String, val default: Boolean) {
        BUTTON("buttons", true),
        DPAD("dpad", true),
        STICK("sticks", false),
        TOUCHPAD("touchpad", false)
    }

    /** Tipo de golpe de la vibración personalizada. */
    enum class Shape { CLICK, TICK, THUD, PULSE }

    /** Vibración personalizada: [strength] 0..1, [shape], [pulseMs] (solo PULSE) y tic al soltar. */
    data class Custom(
        val strength: Float = 0.7f,
        val shape: Shape = Shape.CLICK,
        val pulseMs: Int = 14,
        val releaseTick: Boolean = true
    )

    @Volatile
    private var vibrator: Vibrator? = null

    @Volatile
    private var primitives: Boolean? = null

    /** Clics emitidos (por cualquier camino): para los tests. */
    @Volatile
    internal var emitted = 0

    fun tap(view: View?, constant: Int, source: Source = Source.BUTTON) {
        if (view == null) return Diag.hapticSkipped("sin vista")
        if (GameRumble.isOn) return Diag.hapticSkipped("vibra el juego")
        val level = AppPrefs.hapticLevel(view.context)
        if (level == OFF) return Diag.hapticSkipped("apagada en Ajustes")
        if (!AppPrefs.hapticSource(view.context, source)) return Diag.hapticSkipped("apagada para ${source.key}")
        emitted++
        // Modo compatible: la vibración táctil del sistema, la de toda la vida
        if (AppPrefs.hapticSystem(view.context)) {
            val ok = view.performHapticFeedback(
                if (level == STRONG && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else constant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
            if (ok) Diag.hapticDone("sistema") else Diag.hapticSkipped("el sistema la rechazó")
            return
        }
        val tick = constant == HapticFeedbackConstants.CLOCK_TICK
        if (play(view.context, level, if (tick) Kind.TICK else Kind.CLICK)) {
            Diag.hapticDone(lastPath)
        } else if (view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)) {
            Diag.hapticDone("respaldo del sistema")
        } else {
            Diag.hapticSkipped("sin motor o rechazada")
        }
    }

    @Volatile
    private var lastPath = ""

    /** Tic al soltar (Media y Fuerte; en la personalizada, si se quiere). */
    fun release(view: View?, source: Source = Source.BUTTON) {
        if (GameRumble.isOn || view == null) return
        val level = AppPrefs.hapticLevel(view.context)
        if (level < CRISP || AppPrefs.hapticSystem(view.context)) return
        if (level == CUSTOM && !AppPrefs.hapticCustom(view.context).releaseTick) return
        if (!AppPrefs.hapticSource(view.context, source)) return
        play(view.context, level, Kind.RELEASE)
    }

    /** Para la vista previa de Ajustes. */
    fun preview(view: View, level: Int) {
        if (level == OFF) return
        if (AppPrefs.hapticSystem(view.context)) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            return
        }
        play(view.context, level, Kind.CLICK)
    }

    /** ¿Tiene el sistema la «Respuesta táctil» encendida? (solo afecta al modo compatible) */
    fun systemTouchFeedback(context: Context): Boolean = try {
        android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    } catch (_: Exception) {
        true
    }

    /** Diagnóstico para Ajustes: qué motor hay y qué camino se usa. */
    fun describe(context: Context): String {
        val v = vib(context) ?: return "no vibrator"
        val path = when {
            !v.hasVibrator() -> "no vibrator"
            Build.VERSION.SDK_INT >= 30 && supportsPrimitives(v) -> "primitives"
            Build.VERSION.SDK_INT >= 29 -> "predefined"
            else -> "one-shot"
        }
        return "API ${Build.VERSION.SDK_INT} · $path · amp=${v.hasAmplitudeControl()}"
    }

    private enum class Kind { CLICK, TICK, RELEASE }

    private fun vib(context: Context): Vibrator? {
        vibrator?.let { return it }
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = v
        return v
    }

    private fun play(context: Context, level: Int, kind: Kind): Boolean {
        appContext = context.applicationContext
        val v = vib(context) ?: return false
        if (!v.hasVibrator()) return false
        return try {
            val e = effect(v, level, kind)
            // Como vibración de JUEGO/multimedia, no «táctil»: la táctil la
            // apaga el ajuste «Respuesta táctil» del sistema (muchos móviles
            // lo traen apagado o el usuario lo quita) y el mando se quedaba
            // mudo. La multimedia solo la apaga «Vibración multimedia»
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    e,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    @Volatile
    private var appContext: Context? = null

    private fun effect(v: Vibrator, level: Int, kind: Kind): VibrationEffect {
        // Fuerza claramente distinta en cada nivel (antes Sutil y Nítida se
        // parecían mucho en muchos motores)
        val custom = if (level == CUSTOM) appContext?.let { AppPrefs.hapticCustom(it) } ?: Custom() else null
        val strength = when (level) {
            SUBTLE -> 0.3f
            CRISP -> 0.62f
            CUSTOM -> custom!!.strength.coerceIn(0.05f, 1f)
            else -> 1f
        }
        val shape = custom?.shape ?: Shape.CLICK
        val release = kind == Kind.RELEASE
        val scale = (if (release) strength * 0.5f else strength).coerceIn(0.02f, 1f)
        // Pulso a medida: siempre un golpe con su duración y fuerza
        if (shape == Shape.PULSE && v.hasAmplitudeControl()) {
            val ms = if (release) (custom!!.pulseMs / 2).coerceAtLeast(4) else custom!!.pulseMs.coerceIn(4, 60)
            return VibrationEffect.createOneShot(ms.toLong(), (255 * scale).toInt().coerceIn(1, 255))
        }
        if (Build.VERSION.SDK_INT >= 30 && supportsPrimitives(v)) {
            val prim = when {
                release || kind == Kind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
                shape == Shape.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
                shape == Shape.THUD && Build.VERSION.SDK_INT >= 31 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) ->
                    VibrationEffect.Composition.PRIMITIVE_THUD
                else -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }
            return VibrationEffect.startComposition().addPrimitive(prim, scale).compose()
        }
        // Sin primitivas pero con control de fuerza: un golpe corto a esa fuerza
        if (v.hasAmplitudeControl()) {
            val ms = when {
                release -> 6L
                kind == Kind.TICK || shape == Shape.TICK -> 8L
                shape == Shape.THUD -> 22L
                else -> 13L
            }
            return VibrationEffect.createOneShot(ms, (255 * scale).toInt().coerceIn(1, 255))
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val id = when {
                release || kind == Kind.TICK -> VibrationEffect.EFFECT_TICK
                strength >= 0.9f -> VibrationEffect.EFFECT_HEAVY_CLICK
                strength >= 0.5f -> VibrationEffect.EFFECT_CLICK
                else -> VibrationEffect.EFFECT_TICK
            }
            return VibrationEffect.createPredefined(id)
        }
        val ms = when {
            release -> 6L
            kind == Kind.TICK -> 8L
            else -> (8 + 10 * strength).toLong()
        }
        return VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private fun supportsPrimitives(v: Vibrator): Boolean {
        primitives?.let { return it }
        val ok = Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK
        )
        primitives = ok
        return ok
    }
}
