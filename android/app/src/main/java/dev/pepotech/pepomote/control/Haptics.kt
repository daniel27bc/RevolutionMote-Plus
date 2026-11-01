package dev.pepotech.pepomote.control

import android.content.Context
import android.os.Build
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
        if (GameRumble.isOn || view == null) return
        val level = AppPrefs.hapticLevel(view.context)
        if (level == OFF) return
        emitted++
        val tick = constant == HapticFeedbackConstants.CLOCK_TICK
        if (!play(view.context, level, if (tick) Kind.TICK else Kind.CLICK)) {
            view.performHapticFeedback(constant)
        }
    }

    /** Tic al soltar (solo con Nítida o Fuerte). */
    fun release(view: View?) {
        if (GameRumble.isOn || view == null) return
        val level = AppPrefs.hapticLevel(view.context)
        if (level < CRISP) return
        play(view.context, level, Kind.RELEASE)
    }

    /** Para la vista previa de Ajustes. */
    fun preview(context: Context, level: Int) {
        if (level == OFF) return
        play(context, level, Kind.CLICK)
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
            v.vibrate(effect(v, level, kind))
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
