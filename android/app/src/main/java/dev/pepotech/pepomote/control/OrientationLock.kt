package dev.pepotech.pepomote.control

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Candado de orientación del mando: con un toque la pantalla se queda como
 * está ahora (vertical, apaisado o apaisado al revés) y no gira sola por
 * mucho que muevas o inclines el móvil; otro toque la suelta. Se recuerda.
 * Los ejes del giroscopio siguen a la pantalla, así que también quedan fijos.
 */
object OrientationLock {
    private val _state = MutableStateFlow(AppPrefs.ORIENTATION_FREE)
    val state: StateFlow<Int> = _state.asStateFlow()

    fun load(context: Context) {
        _state.value = AppPrefs.orientationLock(context)
    }

    val locked: Boolean get() = _state.value != AppPrefs.ORIENTATION_FREE

    /** Fija la orientación de [displayRotation] (`Surface.ROTATION_*`), o la suelta si ya estaba fijada. */
    fun toggle(context: Context, displayRotation: Int) {
        val next = if (locked) AppPrefs.ORIENTATION_FREE else fromRotation(displayRotation)
        _state.value = next
        AppPrefs.setOrientationLock(context, next)
    }

    fun fromRotation(rotation: Int): Int = when (rotation) {
        Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun isLandscape(orientation: Int): Boolean =
        orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
}
