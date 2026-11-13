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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.pepotech.pepomote.control.HomeEntry
import dev.pepotech.pepomote.control.HomeGroup
import dev.pepotech.pepomote.control.HomeLayout
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Estado del enlace en el inicio: el punto (apagado, en marcha, encendido) y su texto. */
enum class HomeTone { Off, Busy, On }

/** [detail]: dato corto al final de la píldora (la latencia, con el enlace vivo). */
data class HomeStatus(val tone: HomeTone, val text: String, val detail: String? = null)

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
    onKbm: () -> Unit = {},
    /** Ids de las tarjetas fijadas arriba (AppPrefs.homeFavorites). */
    favorites: List<String> = emptyList(),
    /** Id de lo último que se abrió, para «Continuar» (null = aún nada). */
    last: String? = null,
    /** Se abre una tarjeta de modo: MainActivity lo apunta para «Continuar». */
    onOpened: (HomeEntry) -> Unit = {},
    onFavorites: (List<String>) -> Unit = {},
    /** Encender el PC por la red (null = sin MAC configurada: no se ofrece). */
    onWake: (() -> Unit)? = null,
    /** Con qué pestaña se abre (Conexión si aún no hay nada vinculado). */
    initialTab: HomeTab = HomeTab.Home
) {
    // Qué hace cada tarjeta de modo (las de siempre; aquí solo se reparten)
    val open: (HomeEntry) -> Unit = { e ->
        onOpened(e)
        when (e) {
            HomeEntry.Dolphin -> onDolphin()
            HomeEntry.WiiU -> onWiiU()
            HomeEntry.Switch -> onSwitch()
            HomeEntry.RetroArch -> onRetroArch()
            HomeEntry.Nunchuk -> onNunchuk()
            HomeEntry.Gamepad -> onGamepad()
            HomeEntry.Kbm -> onKbm()
            HomeEntry.Pointer -> onController()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PepoColors.Background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 20.dp)
    ) {
        var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialTab) }
        // La cabecera va DENTRO de la rejilla: en apaisado se desplaza con las
        // tarjetas en vez de comerse media pantalla
        val header: @Composable () -> Unit = {
            Column {
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
                // Estado del enlace, en una píldora que se toca para cambiar de equipo
                val statusShape = RoundedCornerShape(50)
                val statusHint = stringResource(R.string.home_status_change)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .background(PepoColors.Card, statusShape)
                            .border(1.5.dp, PepoColors.CardBorder, statusShape)
                            .clip(statusShape)
                            .clickable(onClickLabel = statusHint) { tab = HomeTab.Connection }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
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
                        Text(
                            status.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        status.detail?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.TextDim), maxLines = 1)
                        }
                    }
                    // Sin conexión y con la MAC del PC guardada: encenderlo desde aquí
                    if (onWake != null && status.tone == HomeTone.Off) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.wol_wake_short),
                            style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.Blue),
                            maxLines = 1,
                            modifier = Modifier
                                .background(PepoColors.Card, statusShape)
                                .border(1.5.dp, PepoColors.CardBorder, statusShape)
                                .clip(statusShape)
                                .clickable(onClick = onWake)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Aviso de versión nueva (enlace a la release de GitHub)
                update?.let { v ->
                    UpdateCard(version = v, onOpen = { onOpenUpdate(v) }, onDismiss = { onDismissUpdate(v) })
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        // Arriba, la conexión; debajo, lo último que se usó, los favoritos y
        // los bloques por intención (consolas, PC y escritorio, lo demás).
        // En vertical, dos columnas; apaisado (o tablet) caben más y las
        // tarjetas no se estiran hasta ser cajas enormes
        // Siluetas en negro con el tema claro y en blanco con el oscuro (el negativo)
        val wiiBody = if (dev.pepotech.pepomote.ui.theme.LocalPepoColors.current.isDark) Color.White else Color(0xFF1B1E21)
        val favs = HomeLayout.favorites(favorites, androidReceiver)
        val lastEntry = HomeLayout.last(last, androidReceiver)
        val serverCard: @Composable () -> Unit = {
            ChannelCard(
                title = stringResource(R.string.channel_server),
                subtitle = stringResource(if (serverRunning) R.string.server_running_short else R.string.channel_server_sub),
                glyph = ChannelGlyph.Server,
                accent = Color(0xFF43A047),
                onClick = onServer
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            if (tab == HomeTab.Home) {
                // Continuar: un toque y a jugar con lo de la última vez
                lastEntry?.let { e ->
                    item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                        EntryCard(
                            e, androidReceiver, wiiBody, wide = true,
                            title = stringResource(R.string.home_continue, entryTitle(e, androidReceiver)),
                            subtitle = stringResource(R.string.home_continue_sub),
                            onClick = { open(e) }
                        )
                    }
                }
                val section: (String, Int, List<HomeEntry>, Boolean) -> Unit = { key, label, list, pinned ->
                    if (list.isNotEmpty()) {
                        item(key = "h-$key", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(label)) }
                        items(list, key = { "$key-${it.id}" }) { e ->
                            PinnableCard(
                                e, androidReceiver, wiiBody, pinned = pinned, first = favs.firstOrNull() == e,
                                onClick = { open(e) },
                                onToggle = { onFavorites(HomeLayout.toggle(favorites, e)) },
                                onFirst = { onFavorites(HomeLayout.moveFirst(favorites, e)) }
                            )
                        }
                    }
                }
                section("fav", R.string.home_favorites, favs, true)
                section("pads", R.string.home_group_consoles, HomeLayout.group(HomeGroup.Controllers, favorites, androidReceiver), false)
                section("tools", R.string.home_group_pc, HomeLayout.group(HomeGroup.Tools, favorites, androidReceiver), false)
                if (favs.isEmpty()) item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.home_pin_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.TextDim),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            } else {
                // Conexión: vincular con un equipo o alojar aquí. Se hace una
                // vez y se olvida, así que no ocupa el inicio
                item(key = "h-conn", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.nav_connection)) }
                item(key = "connect") {
                    ChannelCard(
                        title = stringResource(R.string.channel_connect),
                        subtitle = stringResource(R.string.channel_connect_sub),
                        glyph = ChannelGlyph.Qr,
                        accent = PepoColors.Blue,
                        onClick = onConnect
                    )
                }
                item(key = "server") { serverCard() }
                item(key = "conn-hint", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.conn_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.TextDim),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
        HomeNavBar(tab, onTab = { tab = it }, onSettings = onNewPairing)
    }
}

/** Las pestañas del inicio. Ajustes es una pantalla aparte: va en la barra, pero no es pestaña. */
enum class HomeTab { Home, Connection }

/** La barra de abajo: Inicio, Conexión y Ajustes. */
@Composable
private fun HomeNavBar(tab: HomeTab, onTab: (HomeTab) -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(R.drawable.ic_nav_home, stringResource(R.string.nav_home), tab == HomeTab.Home) { onTab(HomeTab.Home) }
        NavItem(R.drawable.ms_wifi, stringResource(R.string.nav_connection), tab == HomeTab.Connection) { onTab(HomeTab.Connection) }
        NavItem(R.drawable.ms_settings, stringResource(R.string.channel_settings), false, onSettings)
    }
}

@Composable
private fun NavItem(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val tint = if (selected) PepoColors.Blue else PepoColors.TextDim
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) PepoColors.Blue.copy(alpha = 0.14f) else Color.Transparent, shape)
            .semantics { this.selected = selected }
            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 6.dp)
    ) {
        dev.pepotech.pepomote.ui.components.Ico(icon, tint, 24.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = tint), maxLines = 1)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(color = PepoColors.TextDim),
        modifier = Modifier
            .padding(top = 6.dp)
            .semantics { heading() }
    )
}

/** El nombre de la tarjeta (el mismo que lleva «Continuar con …»). */
@Composable
private fun entryTitle(e: HomeEntry, androidReceiver: Boolean): String = stringResource(
    when (e) {
        HomeEntry.Dolphin -> R.string.channel_dolphin
        HomeEntry.WiiU -> R.string.channel_wiiu
        HomeEntry.Switch -> if (androidReceiver) R.string.mode_eden else R.string.channel_switch
        HomeEntry.RetroArch -> R.string.channel_retroarch
        HomeEntry.Nunchuk -> R.string.channel_nunchuk
        HomeEntry.Gamepad -> R.string.channel_gamepad
        HomeEntry.Kbm -> R.string.channel_kbm
        HomeEntry.Pointer -> R.string.channel_controller
    }
)

/** La tarjeta de un modo, con su glifo y su color de siempre. */
@Composable
private fun EntryCard(
    e: HomeEntry,
    androidReceiver: Boolean,
    wiiBody: Color,
    wide: Boolean = false,
    title: String = entryTitle(e, androidReceiver),
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    when (e) {
        HomeEntry.Dolphin -> ChannelCard(
            title = title, subtitle = subtitle ?: stringResource(R.string.channel_dolphin_sub),
            glyph = ChannelGlyph.Dolphin, logoName = "logo_dolphin", accent = Color(0xFF1E88E5),
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
        // Wii U: el móvil como GamePad (o Pro Controller) para Cemu
        HomeEntry.WiiU -> ChannelCard(
            title = title, subtitle = subtitle ?: stringResource(R.string.channel_wiiu_sub),
            glyph = ChannelGlyph.WiiU, accent = Color(0xFF009AC7),
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
        HomeEntry.Switch -> ChannelCard(
            title = title, subtitle = subtitle ?: stringResource(R.string.channel_switch_sub),
            glyph = ChannelGlyph.Switch, image = R.drawable.logo_switch, accent = PepoColors.Error,
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
        // RetroArch: la pistola necesita el ratón del receptor de PC.
        HomeEntry.RetroArch -> ChannelCard(
            title = title,
            subtitle = subtitle ?: stringResource(if (androidReceiver) R.string.channel_retroarch_android_sub else R.string.channel_retroarch_sub),
            glyph = ChannelGlyph.Invader, logoName = "logo_retroarch", accent = Color(0xFF8E5BE8),
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
        // Con Dolphin: el segundo móvil, en la otra mano
        HomeEntry.Nunchuk -> ChannelCard(
            title = title, subtitle = subtitle ?: stringResource(R.string.channel_nunchuk_sub),
            glyph = ChannelGlyph.Nunchuk, mask = R.drawable.icon_nunchuk, accent = wiiBody,
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
        // Mando universal: solo el receptor de PC puede crear el mando
        // de Xbox virtual, así que con servidor Android no se ofrece
        HomeEntry.Gamepad -> ChannelCard(
            title = title, subtitle = subtitle ?: stringResource(R.string.channel_gamepad_sub),
            glyph = ChannelGlyph.Xbox, accent = PepoColors.Blue,
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
        // Teclado y ratón: por la app (receptor de RevolutionMote+) o por
        // Bluetooth (cualquier equipo, sin instalar nada)
        HomeEntry.Kbm -> ChannelCard(
            title = title, subtitle = subtitle ?: stringResource(R.string.channel_kbm_sub),
            glyph = ChannelGlyph.Kbm, accent = Color(0xFFF2B705),
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
        HomeEntry.Pointer -> ChannelCard(
            title = title, subtitle = subtitle ?: stringResource(R.string.channel_controller_sub),
            glyph = ChannelGlyph.Wiimote, mask = R.drawable.icon_wiimote, accent = wiiBody,
            wide = wide, onLongClick = onLongClick, onClick = onClick
        )
    }
}

/** Tarjeta de modo que, al mantenerla, ofrece fijarla arriba o quitarla. */
@Composable
private fun PinnableCard(
    e: HomeEntry,
    androidReceiver: Boolean,
    wiiBody: Color,
    pinned: Boolean,
    first: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onFirst: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        EntryCard(e, androidReceiver, wiiBody, onLongClick = { menu = true }, onClick = onClick)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (pinned) R.string.home_unpin else R.string.home_pin)) },
                onClick = { menu = false; onToggle() }
            )
            if (pinned && !first) DropdownMenuItem(
                text = { Text(stringResource(R.string.home_pin_first)) },
                onClick = { menu = false; onFirst() }
            )
        }
    }
}
