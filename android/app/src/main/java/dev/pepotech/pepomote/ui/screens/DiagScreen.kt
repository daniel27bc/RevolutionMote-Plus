package dev.pepotech.pepomote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.AppPrefs
import dev.pepotech.pepomote.control.ButtonState
import dev.pepotech.pepomote.control.Diag
import dev.pepotech.pepomote.control.GameRumble
import dev.pepotech.pepomote.control.Haptics
import dev.pepotech.pepomote.control.Kbm
import dev.pepotech.pepomote.control.PointerTrackpad
import dev.pepotech.pepomote.control.Precision
import dev.pepotech.pepomote.service.LinkState
import dev.pepotech.pepomote.service.UiLink
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Diagnóstico: lo que el móvil está leyendo y enviando AHORA, para saber
 * dónde se rompe algo sin adivinar (¿llega el giro? ¿salen paquetes? ¿sale
 * el ratón? ¿se pide la vibración y el sistema la rechaza?). Se refresca
 * diez veces por segundo; abrir un mando y volver aquí sigue contando.
 */
@Composable
fun DiagScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val link by LinkState.flow.collectAsState()
    var tick by remember { mutableLongStateOf(0L) }
    // Ritmos: diferencias por segundo de los contadores
    var pps by remember { mutableStateOf(0L) }
    var pxs by remember { mutableStateOf(0L) }
    var kbs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var lastP = Diag.packets.get()
        var lastPx = Diag.mousePixels.get()
        var lastK = Diag.kbmBatches.get()
        var acc = 0
        while (true) {
            delay(100)
            tick++
            if (++acc == 10) {
                acc = 0
                val p = Diag.packets.get()
                val px = Diag.mousePixels.get()
                val k = Diag.kbmBatches.get()
                pps = p - lastP
                pxs = px - lastPx
                kbs = k - lastK
                lastP = p; lastPx = px; lastK = k
            }
        }
    }
    if (tick < 0) return // relee todo en cada tic

    Column(
        Modifier
            .fillMaxSize()
            .background(PepoColors.Background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.diag_sub), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        DiagCard(stringResource(R.string.diag_link)) {
            val c = link as? UiLink.Connected
            Line(stringResource(R.string.diag_state), when (link) {
                is UiLink.Connected -> stringResource(R.string.diag_connected)
                is UiLink.Connecting -> stringResource(R.string.status_connecting)
                is UiLink.Reconnecting -> stringResource(R.string.diag_reconnecting)
                else -> stringResource(R.string.status_disconnected)
            }, ok = c != null)
            if (c != null) {
                Line("PC", c.pcName)
                Line(stringResource(R.string.diag_mode), c.mode)
                Line(stringResource(R.string.diag_latency), c.rttMs?.let { "%.0f ms".format(it) } ?: "—")
                Line(stringResource(R.string.diag_receiver), stringResource(if (c.supportsKbm) R.string.diag_receiver_rm else R.string.diag_receiver_orig))
            }
            Line(stringResource(R.string.diag_packets), "$pps/s", ok = pps > 0)
        }

        DiagCard(stringResource(R.string.diag_sticks)) {
            Line(stringResource(R.string.diag_left), "${ButtonState.stickX()}, ${ButtonState.stickY()}")
            Line(stringResource(R.string.diag_right), "${ButtonState.stickRX()}, ${ButtonState.stickRY()}")
            Line(stringResource(R.string.diag_buttons), "0x" + Integer.toHexString(ButtonState.current()).uppercase())
            Bar(ButtonState.stickRX() / 127f, ButtonState.stickRY() / 127f)
        }

        DiagCard(stringResource(R.string.diag_gyro)) {
            val deg = 57.29578f
            Line("X", "%+.0f °/s".format(Diag.gyroX * deg))
            Line("Y", "%+.0f °/s".format(Diag.gyroY * deg))
            Line("Z", "%+.0f °/s".format(Diag.gyroZ * deg))
            val alive = abs(Diag.gyroX) + abs(Diag.gyroY) + abs(Diag.gyroZ) > 0f
            Text(
                stringResource(if (alive) R.string.diag_gyro_alive else R.string.diag_gyro_idle),
                style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim
            )
        }

        DiagCard(stringResource(R.string.diag_mouse)) {
            Line(stringResource(R.string.diag_backend), when (Kbm.backend) {
                Kbm.Backend.APP -> stringResource(R.string.diag_backend_app)
                Kbm.Backend.BT -> "Bluetooth"
                Kbm.Backend.POINTER -> stringResource(R.string.diag_backend_pointer)
                Kbm.Backend.NONE -> stringResource(R.string.diag_backend_none)
            })
            Line(stringResource(R.string.diag_mouse_px), "$pxs px/s", ok = pxs > 0)
            Line(stringResource(R.string.diag_kbm_batches), "$kbs/s")
            Line(stringResource(R.string.diag_kbm_events), Diag.kbmEvents.get().toString())
            Line(stringResource(R.string.diag_ticker), stringResource(if (Kbm.ticking) R.string.diag_on else R.string.diag_sleeping))
            Line(stringResource(R.string.diag_pointer_pad), when (PointerTrackpad.mode.value) {
                PointerTrackpad.ON -> stringResource(R.string.pointer_mode_touch)
                PointerTrackpad.BOTH -> stringResource(R.string.pointer_mode_both)
                else -> stringResource(R.string.pointer_mode_aim)
            })
            Line(stringResource(R.string.precision), stringResource(if (Precision.held) R.string.diag_on else R.string.diag_off))
        }

        DiagCard(stringResource(R.string.diag_haptics)) {
            Line(stringResource(R.string.diag_haptic_level), AppPrefs.hapticLevel(context).toString())
            Line(stringResource(R.string.diag_haptic_count), "${Diag.hapticPlayed.get()} / ${Diag.hapticAsked.get()}")
            Line(stringResource(R.string.diag_haptic_path), Diag.hapticPath.ifEmpty { "—" })
            if (Diag.hapticSkip.isNotEmpty()) Line(stringResource(R.string.diag_haptic_skip), Diag.hapticSkip, ok = false)
            Line(stringResource(R.string.diag_rumble), stringResource(if (GameRumble.isOn) R.string.diag_on else R.string.diag_off))
            Text(Haptics.describe(context), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
            TextButton(onClick = { Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP) }) {
                Text(stringResource(R.string.diag_haptic_try), color = PepoColors.Blue)
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back), color = PepoColors.TextDim)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DiagCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String, ok: Boolean? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (ok != null) {
            Box(
                Modifier
                    .padding(top = 6.dp, end = 6.dp)
                    .size(8.dp)
                    .background(if (ok) PepoColors.Ok else PepoColors.Warn, CircleShape)
            )
        }
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.Text, fontFamily = FontFamily.Monospace))
    }
}

/** El stick derecho dibujado: dónde está la deflexión ahora mismo. */
@Composable
private fun Bar(x: Float, y: Float) {
    val dot = PepoColors.Blue
    val ring = PepoColors.CardBorder
    androidx.compose.foundation.Canvas(Modifier.padding(top = 6.dp).size(96.dp)) {
        val r = size.minDimension / 2f
        drawCircle(ring, r, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawCircle(dot, r * 0.12f, center.copy(x = center.x + x * r * 0.9f, y = center.y - y * r * 0.9f))
    }
}

