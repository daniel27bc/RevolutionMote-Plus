package dev.pepotech.pepomote.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.Kbm
import dev.pepotech.pepomote.control.MouseConfig
import dev.pepotech.pepomote.ui.theme.PepoColors

/** Qué ocupa el hueco grande de Teclado y ratón. */
enum class KbmPanel { Touchpad, Media, Numpad, Present }

/** Las pestañas: touchpad, multimedia, teclado numérico y presentar. */
@Composable
internal fun KbmPanelChips(current: KbmPanel, onPick: (KbmPanel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((panel, label) in listOf(
            KbmPanel.Touchpad to R.string.kbm_panel_touchpad,
            KbmPanel.Media to R.string.kbm_panel_media,
            KbmPanel.Numpad to R.string.kbm_panel_numpad,
            KbmPanel.Present to R.string.kbm_panel_present
        )) {
            ModeChip(stringResource(label), selected = current == panel, dense = true) { onPick(panel) }
        }
    }
}

/** Lo que va en el hueco grande según la pestaña. */
@Composable
internal fun KbmSurface(panel: KbmPanel, mouse: MouseConfig, appBackend: Boolean, modifier: Modifier) {
    val touchpad: @Composable (Modifier) -> Unit = { m ->
        BoxWithConstraints(m) {
            MousePad(maxWidth, maxHeight, mouseMode = true, sensitivity = mouse.padSens, antiDeadzone = 0f, inertia = mouse.inertia)
        }
    }
    when (panel) {
        KbmPanel.Touchpad -> touchpad(modifier)
        KbmPanel.Media -> MediaPanel(appBackend, modifier)
        KbmPanel.Numpad -> NumpadPanel(modifier)
        KbmPanel.Present -> PresentPanel(modifier, touchpad)
    }
}

/** Una fila de teclas grandes que se reparten el ancho. */
@Composable
private fun KeyRow(modifier: Modifier, keys: List<PanelKey>) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in keys) {
            HoldKey(
                k.label, Modifier.weight(k.weight).fillMaxHeight(), k.icon
            ) { down -> Kbm.key(k.name, down) }
        }
    }
}

private class PanelKey(val name: String, val label: String, val icon: Int? = null, val weight: Float = 1f)

/** Reproducción y volumen del otro equipo. */
@Composable
private fun MediaPanel(appBackend: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyRow(
            Modifier.weight(1f),
            listOf(
                PanelKey("MediaTrackPrevious", stringResource(R.string.media_prev), R.drawable.ic_media_prev),
                PanelKey("MediaPlayPause", stringResource(R.string.media_play), R.drawable.ic_media_play_pause, weight = 1.4f),
                PanelKey("MediaTrackNext", stringResource(R.string.media_next), R.drawable.ic_media_next)
            )
        )
        KeyRow(
            Modifier.weight(1f),
            listOf(
                PanelKey("AudioVolumeDown", stringResource(R.string.media_vol_down), R.drawable.ic_media_vol_down),
                PanelKey("AudioVolumeMute", stringResource(R.string.media_mute), R.drawable.ic_media_mute),
                PanelKey("AudioVolumeUp", stringResource(R.string.media_vol_up), R.drawable.ic_media_vol_up)
            )
        )
        if (appBackend) Hint(stringResource(R.string.kbm_media_app_hint))
    }
}

/** El teclado numérico de un teclado completo. */
@Composable
private fun NumpadPanel(modifier: Modifier) {
    val lang = appLang()
    val back = dev.pepotech.pepomote.control.PadKeys.label("Backspace", lang)
    val enter = dev.pepotech.pepomote.control.PadKeys.label("Enter", lang)
    val rows = listOf(
        listOf("NumLock" to "Num", "NumpadDivide" to "/", "NumpadMultiply" to "*", "NumpadSubtract" to "-"),
        listOf("Numpad7" to "7", "Numpad8" to "8", "Numpad9" to "9", "NumpadAdd" to "+"),
        listOf("Numpad4" to "4", "Numpad5" to "5", "Numpad6" to "6", "Backspace" to back),
        listOf("Numpad1" to "1", "Numpad2" to "2", "Numpad3" to "3", "NumpadEnter" to enter),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) KeyRow(Modifier.weight(1f), row.map { PanelKey(it.first, it.second) })
        KeyRow(
            Modifier.weight(1f),
            listOf(PanelKey("Numpad0", "0", weight = 2f), PanelKey("NumpadDecimal", "."), PanelKey("Tab", "Tab"))
        )
    }
}

/** Mando de presentaciones: pasar diapositivas y, debajo, el puntero. */
@Composable
private fun PresentPanel(modifier: Modifier, touchpad: @Composable (Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyRow(
            Modifier.height(48.dp),
            listOf(
                PanelKey("F5", stringResource(R.string.present_start)),
                PanelKey("KeyB", stringResource(R.string.present_black)),
                PanelKey("Escape", stringResource(R.string.present_end))
            )
        )
        KeyRow(
            Modifier.weight(1f),
            listOf(
                PanelKey(Kbm.SLIDE_PREV, stringResource(R.string.present_prev), R.drawable.ms_chevron_left),
                PanelKey(Kbm.SLIDE_NEXT, stringResource(R.string.present_next), R.drawable.ms_chevron_right, weight = 1.6f)
            )
        )
        Hint(stringResource(R.string.present_vol_hint))
        touchpad(Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.TextDim))
}
