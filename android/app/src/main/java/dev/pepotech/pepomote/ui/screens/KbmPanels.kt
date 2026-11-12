package dev.pepotech.pepomote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.Kbm
import dev.pepotech.pepomote.control.MouseConfig
import dev.pepotech.pepomote.ui.theme.PepoColors

/** Qué ocupa el hueco grande de Teclado y ratón. */
enum class KbmPanel { Touchpad, Shortcuts, Media, Numpad, Present }

/** Las pestañas: touchpad, multimedia, teclado numérico y presentar. */
@Composable
internal fun KbmPanelChips(current: KbmPanel, onPick: (KbmPanel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((panel, label) in listOf(
            KbmPanel.Touchpad to R.string.kbm_panel_touchpad,
            KbmPanel.Shortcuts to R.string.kbm_panel_shortcuts,
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
        KbmPanel.Shortcuts -> ShortcutsPanel(modifier)
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

/** Doce atajos a gusto de cada uno: tocar lo lanza, mantener lo edita. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShortcutsPanel(modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lang = appLang()
    val deck = dev.pepotech.pepomote.control.ShortcutDeck
    var list by androidx.compose.runtime.remember(lang) {
        androidx.compose.runtime.mutableStateOf(deck.fromJson(dev.pepotech.pepomote.control.AppPrefs.shortcutDeck(context), lang))
    }
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    fun save(next: List<dev.pepotech.pepomote.control.Shortcut>?) {
        dev.pepotech.pepomote.control.AppPrefs.setShortcutDeck(context, next?.let { deck.toJson(it) })
        list = next ?: deck.defaults(lang)
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until deck.SIZE / 3) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    val s = list[i]
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(PepoColors.Card, shape)
                            .border(1.5.dp, PepoColors.CardBorder, shape)
                            .clip(shape)
                            .combinedClickable(
                                onClick = { if (s.isEmpty) editing = i else deck.fire(s) },
                                onLongClick = { editing = i }
                            )
                            .padding(6.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(
                                s.label.ifEmpty { "+" },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (!s.isEmpty) Text(
                                deck.describe(s, lang),
                                style = MaterialTheme.typography.bodySmall.copy(color = PepoColors.TextDim),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Hint(stringResource(R.string.shortcut_hint))
    }
    editing?.let { i ->
        ShortcutEditor(
            list[i],
            onSave = { s -> save(list.toMutableList().also { it[i] = s }); editing = null },
            onResetAll = { save(null); editing = null },
            onDismiss = { editing = null }
        )
    }
}

/** Editar un atajo: rótulo y, o bien teclas a la vez, o bien un texto que teclear. */
@Composable
private fun ShortcutEditor(
    initial: dev.pepotech.pepomote.control.Shortcut,
    onSave: (dev.pepotech.pepomote.control.Shortcut) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val deck = dev.pepotech.pepomote.control.ShortcutDeck
    val lang = appLang()
    var label by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial.label) }
    var keys by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial.keys) }
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial.text) }
    val mods = deck.MODIFIERS.map { it.first }
    EditorDialog(onDismiss) {
        Text(stringResource(R.string.shortcut_edit), style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(deck.MAX_LABEL) },
            singleLine = true,
            label = { Text(stringResource(R.string.shortcut_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.shortcut_keys), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        ChipFlow {
            for ((name, short) in deck.MODIFIERS) {
                ModeChip(short, selected = name in keys, dense = true, toggle = true) {
                    keys = if (name in keys) keys - name else keys + name
                    text = ""
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        ChipFlow {
            for ((name, _) in dev.pepotech.pepomote.control.PadKeys.ALL) {
                if (name in mods) continue
                ModeChip(dev.pepotech.pepomote.control.PadKeys.label(name, lang), selected = name in keys, dense = true) {
                    // Una sola tecla además de los modificadores
                    keys = keys.filter { it in mods } + (if (name in keys) emptyList() else listOf(name))
                    text = ""
                }
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.take(deck.MAX_TEXT)
                if (it.isNotEmpty()) keys = emptyList()
            },
            singleLine = true,
            label = { Text(stringResource(R.string.shortcut_text)) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.material3.TextButton(onClick = onResetAll) {
                Text(stringResource(R.string.shortcut_reset), color = PepoColors.Error)
            }
            androidx.compose.material3.TextButton(onClick = {
                onSave(dev.pepotech.pepomote.control.Shortcut(label.trim(), keys, text))
            }) {
                Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}
