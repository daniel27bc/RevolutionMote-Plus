package dev.pepotech.pepomote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.UpdateCheck
import dev.pepotech.pepomote.ui.components.ChannelCard
import dev.pepotech.pepomote.ui.components.ChannelGlyph
import dev.pepotech.pepomote.ui.components.PulsingDot
import dev.pepotech.pepomote.ui.components.UpdateCard
import dev.pepotech.pepomote.ui.theme.PepoColors
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Estado del enlace en el inicio: el punto (apagado, en marcha, encendido) y su texto. */
enum class HomeTone { Off, Busy, On }

data class HomeStatus(val tone: HomeTone, val text: String)

@Composable
fun HomeScreen(
    status: HomeStatus,
    langLabel: String,
    langSwitchHint: String,
    onToggleLang: () -> Unit,
    onConnect: () -> Unit,
    onController: () -> Unit,
    onDolphin: () -> Unit,
    onWiiU: () -> Unit,
    onSwitch: () -> Unit,
    onNunchuk: () -> Unit,
    onNewPairing: () -> Unit,
    onServer: () -> Unit = {},
    androidReceiver: Boolean = false,
    serverRunning: Boolean = false,
    /** Versión nueva publicada que anunciar (null = ninguna). */
    update: UpdateCheck.Version? = null,
    onOpenUpdate: (UpdateCheck.Version) -> Unit = {},
    onDismissUpdate: (UpdateCheck.Version) -> Unit = {},
    /** Tarjeta RetroArch (receptor de PC y servidor Android). */
    onRetroArch: () -> Unit = {},
    /** Tarjeta «Mando universal» (solo receptor de PC). */
    onGamepad: () -> Unit = {},
    onKbm: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PepoColors.Background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            dev.pepotech.pepomote.ui.components.BrandTitle(Modifier.weight(1f), logo = 38.dp, fontSize = 26.sp)
            // Idioma: ES / EN (toca para cambiar)
            val pill = RoundedCornerShape(14.dp)
            Text(
                langLabel,
                style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.Text),
                modifier = Modifier
                    .semantics { contentDescription = langSwitchHint }
                    .background(PepoColors.Card, pill)
                    .border(1.5.dp, PepoColors.CardBorder, pill)
                    .clickable(onClick = onToggleLang)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (status.tone) {
                HomeTone.On -> Box(
                    Modifier
                        .size(10.dp)
                        .background(PepoColors.Ok, CircleShape)
                )
                HomeTone.Busy -> PulsingDot(PepoColors.Warn)
                HomeTone.Off -> Box(
                    Modifier
                        .size(10.dp)
                        .background(PepoColors.TextDim, CircleShape)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(status.text, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        // Aviso de versión nueva (enlace a la release de GitHub)
        update?.let { v ->
            UpdateCard(version = v, onOpen = { onOpenUpdate(v) }, onDismiss = { onDismissUpdate(v) })
            Spacer(Modifier.height(16.dp))
        }
        // Tres filas de pares: Conectar | Mando, Dolphin | Wii U, Nunchuk | Ajustes
        // En vertical, dos columnas; apaisado (o tablet) caben más y las
        // tarjetas no se estiran hasta ser cajas enormes
        // El mando y el Nunchuk de Wii: blancos en el tema claro, negros en el oscuro
        val wiiBody = if (dev.pepotech.pepomote.ui.theme.LocalPepoColors.current.isDark) Color(0xFF1B1E21) else Color.White
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(if (androidReceiver) maxLineSpan else 1) }) {
                ChannelCard(
                    title = stringResource(R.string.channel_connect),
                    subtitle = stringResource(R.string.channel_connect_sub),
                    glyph = ChannelGlyph.Qr,
                    accent = PepoColors.Blue,
                    wide = androidReceiver,
                    onClick = onConnect
                )
            }
            if (!androidReceiver) item {
                ChannelCard(
                    title = stringResource(R.string.channel_server),
                    subtitle = stringResource(if (serverRunning) R.string.server_running_short else R.string.channel_server_sub),
                    glyph = ChannelGlyph.Server,
                    accent = Color(0xFF43A047),
                    onClick = onServer
                )
            }
            if (!androidReceiver) item {
                ChannelCard(
                    title = stringResource(R.string.channel_controller),
                    subtitle = stringResource(R.string.channel_controller_sub),
                    glyph = ChannelGlyph.Wiimote,
                    accent = wiiBody,
                    onClick = onController
                )
            }
            item {
                ChannelCard(
                    title = stringResource(R.string.channel_dolphin),
                    subtitle = stringResource(R.string.channel_dolphin_sub),
                    glyph = ChannelGlyph.Dolphin,
                    logoName = "logo_dolphin",
                    accent = Color(0xFF1E88E5),
                    onClick = onDolphin
                )
            }
            // Wii U: el móvil como GamePad (o Pro Controller) para Cemu
            if (!androidReceiver) item {
                ChannelCard(
                    title = stringResource(R.string.channel_wiiu),
                    subtitle = stringResource(R.string.channel_wiiu_sub),
                    glyph = ChannelGlyph.GamePad,
                    accent = Color(0xFF009AC7),
                    onClick = onWiiU
                )
            }
            item {
                ChannelCard(
                    title = stringResource(if (androidReceiver) R.string.mode_eden else R.string.channel_switch),
                    subtitle = stringResource(R.string.channel_switch_sub),
                    glyph = ChannelGlyph.Switch,
                    accent = PepoColors.Error,
                    onClick = onSwitch
                )
            }
            // RetroArch: la pistola necesita el ratón del receptor de PC.
            item {
                ChannelCard(
                    title = stringResource(R.string.channel_retroarch),
                    subtitle = stringResource(if (androidReceiver) R.string.channel_retroarch_android_sub else R.string.channel_retroarch_sub),
                    glyph = ChannelGlyph.Invader,
                    logoName = "logo_retroarch",
                    accent = Color(0xFF8E5BE8),
                    onClick = onRetroArch
                )
            }
            // Mando universal: solo el receptor de PC puede crear el mando
            // de Xbox virtual, así que con servidor Android no se ofrece
            if (!androidReceiver) item {
                ChannelCard(
                    title = stringResource(R.string.channel_gamepad),
                    subtitle = stringResource(R.string.channel_gamepad_sub),
                    glyph = ChannelGlyph.Xbox,
                    accent = PepoColors.Blue,
                    onClick = onGamepad
                )
            }
            // Teclado y ratón: por la app (receptor de RevolutionMote+) o por
            // Bluetooth (cualquier equipo, sin instalar nada)
            item {
                ChannelCard(
                    title = stringResource(R.string.channel_kbm),
                    subtitle = stringResource(R.string.channel_kbm_sub),
                    glyph = ChannelGlyph.Keyboard,
                    accent = Color(0xFFF2B705),
                    onClick = onKbm
                )
            }
            // Con Dolphin: el segundo móvil, en la otra mano
            if (!androidReceiver) item {
                ChannelCard(
                    title = stringResource(R.string.channel_nunchuk),
                    subtitle = stringResource(R.string.channel_nunchuk_sub),
                    glyph = ChannelGlyph.Nunchuk,
                    accent = wiiBody,
                    onClick = onNunchuk
                )
            }
            if (androidReceiver) item {
                ChannelCard(
                    title = stringResource(R.string.channel_server),
                    subtitle = stringResource(if (serverRunning) R.string.server_running_short else R.string.channel_server_sub),
                    glyph = ChannelGlyph.Server,
                    accent = Color(0xFF43A047),
                    onClick = onServer
                )
            }
            item {
                ChannelCard(
                    title = stringResource(R.string.channel_settings),
                    subtitle = stringResource(R.string.channel_settings_sub),
                    glyph = ChannelGlyph.Gear,
                    accent = PepoColors.TextDim,
                    icon = R.drawable.ms_settings,
                    onClick = onNewPairing
                )
            }
        }
    }
}
