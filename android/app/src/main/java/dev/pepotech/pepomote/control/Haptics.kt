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

    @Volatile
    private var vibrator: Vibrator? = null

    @Volatile
    private var primitives: Boolean? = null

    /** Clics emitidos (por cualquier camino): para los tests. */
    @Volatile
    internal var emitted = 0

    fun tap(view: View?, constant: Int) {
        if (view == null) return Diag.hapticSkipped("sin vista")
        if (GameRumble.isOn) return Diag.hapticSkipped("vibra el juego")
        val level = AppPrefs.hapticLevel(view.context)
        if (level == OFF) return Diag.hapticSkipped("apagada en Ajustes")
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

    /** Tic al soltar (solo con Nítida o Fuerte). */
    fun release(view: View?) {
        if (GameRumble.isOn || view == null) return
        val level = AppPrefs.hapticLevel(view.context)
        if (level < CRISP || AppPrefs.hapticSystem(view.context)) return
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
        val v = vib(context) ?: return false
        if (!v.hasVibrator()) return false
        return try {
            val e = effect(v, level, kind)
            // Como vibración TÁCTIL: con el móvil en silencio o la vibración
            // de notificaciones apagada sigue sonando (manda el ajuste
            // «Respuesta táctil» del sistema)
            lastPath = when {
                Build.VERSION.SDK_INT >= 30 && supportsPrimitives(v) -> "primitivas"
                Build.VERSION.SDK_INT >= 29 -> "efecto predefinido"
                else -> "pulso"
            }
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    e,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun effect(v: Vibrator, level: Int, kind: Kind): VibrationEffect {
        val strength = when (level) {
            SUBTLE -> 0.35f
            CRISP -> 0.7f
            else -> 1f
        }
        if (Build.VERSION.SDK_INT >= 30 && supportsPrimitives(v)) {
            val (prim, scale) = when (kind) {
                Kind.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK to strength
                Kind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK to strength
                Kind.RELEASE -> VibrationEffect.Composition.PRIMITIVE_TICK to strength * 0.5f
            }
            return VibrationEffect.startComposition().addPrimitive(prim, scale.coerceIn(0f, 1f)).compose()
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val id = when {
                kind == Kind.CLICK && level == STRONG -> VibrationEffect.EFFECT_HEAVY_CLICK
                kind == Kind.CLICK && level == CRISP -> VibrationEffect.EFFECT_CLICK
                else -> VibrationEffect.EFFECT_TICK
            }
            return VibrationEffect.createPredefined(id)
        }
        val ms = when (kind) {
            Kind.CLICK -> if (level == STRONG) 16L else 12L
            Kind.TICK -> 8L
            Kind.RELEASE -> 6L
        }
        val amp = if (v.hasAmplitudeControl()) (255 * strength * (if (kind == Kind.CLICK) 1f else 0.5f)).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        return VibrationEffect.createOneShot(ms, amp)
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
