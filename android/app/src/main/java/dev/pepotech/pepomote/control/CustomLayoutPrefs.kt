package dev.pepotech.pepomote.control

import android.content.Context
import androidx.compose.ui.geometry.Offset

/**
 * Almacena los desplazamientos personalizados (offsets X, Y) definidos por el usuario
 * en el Modo Edición de Layout para el mando universal y los demás modos de juego.
 */
object CustomLayoutPrefs {
    private const val PREFS = "pepomote_layout_custom"

    const val KEY_LEFT_STICK = "left_stick"
    const val KEY_DPAD = "dpad"
    const val KEY_RIGHT_STICK = "right_stick"
    const val KEY_FACE_BUTTONS = "face_buttons"
    const val KEY_LEFT_SHOULDERS = "left_shoulders"
    const val KEY_RIGHT_SHOULDERS = "right_shoulders"

    fun getOffset(context: Context, key: String): Offset {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val x = sp.getFloat("${key}_x", 0f)
        val y = sp.getFloat("${key}_y", 0f)
        return Offset(x, y)
    }

    fun setOffset(context: Context, key: String, offset: Offset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat("${key}_x", offset.x)
            .putFloat("${key}_y", offset.y)
            .apply()
    }

    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun hasCustomLayout(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.all.isNotEmpty()
    }
}
