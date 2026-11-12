package dev.pepotech.pepomote.ui.screens

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.horizontalScroll
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.control.AppPrefs
import dev.pepotech.pepomote.control.Haptics
import dev.pepotech.pepomote.ui.theme.ThemeMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import dev.pepotech.pepomote.control.PadProfileStore
import dev.pepotech.pepomote.control.PadProfiles
import dev.pepotech.pepomote.control.PhysicalPad
import kotlin.math.roundToInt
import dev.pepotech.pepomote.control.GameRumble
import dev.pepotech.pepomote.control.RumblePref
import dev.pepotech.pepomote.service.GamePadSide
import dev.pepotech.pepomote.service.LandscapeSide
import dev.pepotech.pepomote.service.LinkState
import dev.pepotech.pepomote.service.UiLink
import dev.pepotech.pepomote.service.NunchukSide
import dev.pepotech.pepomote.sensor.GyroClass
import dev.pepotech.pepomote.sensor.GyroDetect
import dev.pepotech.pepomote.sensor.MotionSource
import dev.pepotech.pepomote.ui.theme.PepoColors
import androidx.compose.ui.res.stringResource
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.service.UpdateNotice
import dev.pepotech.pepomote.ui.components.UpdateSettingsCard

/** Tarjeta del lado de un mando apaisado fijo: Izquierda / Derecha / Según el sensor. */
@Composable
private fun SideCard(@StringRes title: Int, @StringRes sub: Int, side: LandscapeSide, onPick: (LandscapeSide) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, s) in listOf(
                    R.string.side_left to LandscapeSide.Left,
                    R.string.side_right to LandscapeSide.Right,
                    R.string.side_sensor to LandscapeSide.Sensor
                )) {
                    ModeChip(stringResource(label), selected = side == s) { onPick(s) }
                }
            }
        }
    }
}

/**
 * Sensor del puntero: giroscopio (lo de siempre, recomendado si es real) o
 * acelerómetro (apuntado por inclinación, para móviles sin giroscopio real).
 * Debajo, lo que tiene este móvil, con el nombre del sensor para poder
 * diagnosticar. Sin giroscopio ni rotation vector solo cabe el acelerómetro.
 */
@Composable
private fun MotionSourceCard() {
    val context = LocalContext.current
    val gyroClass = remember { GyroDetect.classify(context) }
    val sensorName = remember { GyroDetect.describe(context) ?: "—" }
    val tilt by MotionSource.tilt.collectAsState()
    LaunchedEffect(Unit) { MotionSource.refresh(context) }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.motion_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.motion_sub), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    stringResource(R.string.motion_gyro),
                    selected = !tilt,
                    enabled = gyroClass != GyroClass.NONE
                ) { MotionSource.choose(context, accel = false) }
                ModeChip(stringResource(R.string.motion_accel), selected = tilt) {
                    MotionSource.choose(context, accel = true)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when (gyroClass) {
                    GyroClass.REAL -> stringResource(R.string.motion_status_real, sensorName)
                    GyroClass.VIRTUAL -> stringResource(R.string.motion_status_virtual, sensorName)
                    GyroClass.NONE -> stringResource(R.string.motion_status_none)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (gyroClass == GyroClass.REAL) PepoColors.TextDim else PepoColors.Warn
            )
        }
    }
}

/**
 * Tarjeta «Vibración en los juegos»: Alta / Normal / Baja / Apagada, «Probar»
 * y, con enlace, lo que el receptor puede hacer (`ok.rumble`). Sin motor se
 * dice y los chips se apagan. La escala se lee en cada orden al motor, así que
 * elegir un chip vale al instante.
 */
@Composable
private fun GameRumbleCard() {
    val context = LocalContext.current
    var pref by remember { mutableStateOf(AppPrefs.gameRumble(context)) }
    val hasMotor = remember { GameRumble.hasMotor }
    val link by LinkState.flow.collectAsState()
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.rumble_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.rumble_sub), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in RumblePref.ALL) {
                    ModeChip(stringResource(rumbleLabel(p)), selected = pref == p.key, enabled = hasMotor) {
                        pref = p.key
                        AppPrefs.setGameRumble(context, p.key)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (hasMotor) {
                // Un pulso de 300 ms con la escala elegida (con Apagada no hace nada)
                TextButton(onClick = { GameRumble.pulse(RumblePref.scale(pref)) }) {
                    Text(stringResource(R.string.rumble_try), color = PepoColors.Blue)
                }
                Text(
                    stringResource(rumbleStatus((link as? UiLink.Connected)?.rumble)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PepoColors.TextDim
                )
            } else {
                Text(
                    stringResource(R.string.rumble_no_motor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PepoColors.Warn
                )
            }
        }
    }
}

private fun rumbleLabel(p: RumblePref): Int = when (p) {
    RumblePref.HIGH -> R.string.rumble_high
    RumblePref.NORMAL -> R.string.rumble_normal
    RumblePref.LOW -> R.string.rumble_low
    RumblePref.OFF -> R.string.rumble_off
}

/**
 * Texto de estado según `ok.rumble`; null = receptor sin vibración. Un valor
 * que no se conoce (receptor más nuevo) se cuenta como «no puede», que es lo
 * que no promete nada.
 */
private fun rumbleStatus(rumble: String?): Int = when (rumble) {
    null -> R.string.rumble_pc_old
    "ready" -> R.string.rumble_pc_ready
    "driver" -> R.string.rumble_pc_driver
    "denied" -> R.string.rumble_pc_denied
    else -> R.string.rumble_pc_unsupported
}

/** Cuántos ajustes coinciden con la búsqueda en esta pasada (para «Sin resultados»). */
internal class SettingsMatchCounter {
    var count = 0
}

/** Lo que va dentro de una sección: cada ajuste con los textos por los que se le busca. */
internal class SettingsSectionBuilder {
    val items = ArrayList<Pair<List<Int>, @Composable () -> Unit>>()

    fun SettingItem(keywords: List<Int>, content: @Composable () -> Unit) {
        items += keywords to content
    }
}

/** Minúsculas y sin tildes: «vibración» encuentra «Vibracion». */
internal fun searchNorm(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Una sección de Ajustes: su título y sus tarjetas (solo las que coinciden con [query]). */
@Composable
internal fun SettingsSection(
    @StringRes title: Int,
    query: String,
    counter: SettingsMatchCounter,
    open: Int?,
    build: SettingsSectionBuilder.() -> Unit
) {
    // Sin búsqueda solo se pinta la sección abierta (la lista de secciones
    // la pinta la pantalla, como el menú de Ajustes de un iPhone)
    if (query.isBlank() && open != title) return
    val context = LocalContext.current
    val items = SettingsSectionBuilder().apply(build).items
    val q = searchNorm(query.trim())
    val sectionName = stringResource(title)
    val shown = if (q.isEmpty()) items else items.filter { (ids, _) ->
        val words = q.split(' ').filter { it.isNotEmpty() }
        val text = searchNorm(sectionName + " " + ids.joinToString(" ") { runCatching { context.getString(it) }.getOrDefault("") })
        words.all { it in text }
    }
    if (shown.isEmpty()) return
    counter.count += shown.size
    if (q.isNotEmpty()) Text(
        sectionName.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(color = PepoColors.Blue, letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)),
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((ids, content) in shown) androidx.compose.runtime.key(ids.joinToString()) { content() }
    }
}

/** Vibración personalizada: fuerza, tipo de golpe, duración y tic al soltar (se nota al moverlos). */
@Composable
internal fun HapticCustomEditor() {
    val context = LocalContext.current
    val view = LocalView.current
    var c by remember { mutableStateOf(AppPrefs.hapticCustom(context)) }
    fun set(n: Haptics.Custom, feel: Boolean = true) {
        c = n
        AppPrefs.setHapticCustom(context, n)
        if (feel) Haptics.preview(view, Haptics.CUSTOM)
    }
    Spacer(Modifier.height(10.dp))
    LabeledSlider(stringResource(R.string.haptic_strength), c.strength, 0.05f..1f, "${(c.strength * 100).toInt()}%") {
        set(c.copy(strength = it), feel = false)
    }
    TextButton(onClick = { Haptics.preview(view, Haptics.CUSTOM) }) {
        IconLabel(R.drawable.ms_vibration, stringResource(R.string.haptic_test), PepoColors.Blue)
    }
    Text(stringResource(R.string.haptic_shape), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.haptic_shape_sub), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        for ((shape, label) in listOf(
            Haptics.Shape.CLICK to R.string.haptic_shape_click,
            Haptics.Shape.TICK to R.string.haptic_shape_tick,
            Haptics.Shape.THUD to R.string.haptic_shape_thud,
            Haptics.Shape.PULSE to R.string.haptic_shape_pulse
        )) {
            ModeChip(stringResource(label), selected = c.shape == shape, dense = true) { set(c.copy(shape = shape)) }
        }
    }
    if (c.shape == Haptics.Shape.PULSE) {
        LabeledSlider(stringResource(R.string.haptic_duration), c.pulseMs.toFloat(), 4f..60f, "${c.pulseMs} ms") {
            set(c.copy(pulseMs = it.toInt()), feel = false)
        }
    }
    SwitchRow(stringResource(R.string.haptic_release), c.releaseTick, stringResource(R.string.haptic_release_sub)) {
        set(c.copy(releaseTick = it))
    }
}

/** Qué controles vibran: de serie, botones y cruceta; sticks y touchpad, no. */
@Composable
internal fun HapticSourcesEditor() {
    val context = LocalContext.current
    Text(stringResource(R.string.haptic_sources), style = MaterialTheme.typography.titleSmall)
    for ((src, label) in listOf(
        Haptics.Source.BUTTON to R.string.haptic_src_buttons,
        Haptics.Source.DPAD to R.string.haptic_src_dpad,
        Haptics.Source.STICK to R.string.haptic_src_sticks,
        Haptics.Source.TOUCHPAD to R.string.haptic_src_touchpad
    )) {
        var on by remember(src) { mutableStateOf(AppPrefs.hapticSource(context, src)) }
        SwitchRow(stringResource(label), on) {
            on = it
            AppPrefs.setHapticSource(context, src, it)
        }
    }
}

/** Las secciones de Ajustes, en orden: título, icono y de qué va. */
internal val SETTINGS_SECTIONS = listOf(
    Triple(R.string.settings_sec_appearance, R.drawable.ms_palette, R.string.settings_sec_appearance_sub),
    Triple(R.string.settings_sec_controls, R.drawable.ms_sports_esports, R.string.settings_sec_controls_sub),
    Triple(R.string.settings_sec_pointer, R.drawable.ms_my_location, R.string.settings_sec_pointer_sub),
    Triple(R.string.settings_sec_vibration, R.drawable.ms_vibration, R.string.settings_sec_vibration_sub),
    Triple(R.string.settings_sec_wii, R.drawable.ms_videogame_asset, R.string.settings_sec_wii_sub),
    Triple(R.string.settings_sec_app, R.drawable.ms_wifi, R.string.settings_sec_app_sub)
)

/**
 * El menú de Ajustes, como el de un iPhone: una tarjeta con una fila por
 * sección (icono en su cuadradito de color, título, de qué va y «›»).
 */
@Composable
internal fun SettingsRootList(onOpen: (Int) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        SETTINGS_SECTIONS.forEachIndexed { i, (title, icon, sub) ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 64.dp), color = PepoColors.CardBorder)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(title) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(36.dp).background(PepoColors.Blue, androidx.compose.foundation.shape.RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) { dev.pepotech.pepomote.ui.components.Ico(icon, PepoColors.OnAccent, 22.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                dev.pepotech.pepomote.ui.components.Ico(R.drawable.ms_chevron_right, PepoColors.TextDim, 22.dp)
            }
        }
    }
}

/** El buscador de Ajustes: escribe y se quedan los ajustes relacionados. */
@Composable
internal fun SettingsSearchField(query: String, onChange: (String) -> Unit) {
    val clear = stringResource(R.string.settings_search_clear)
    val trailing: (@Composable () -> Unit)? = if (query.isEmpty()) null else {
        {
            androidx.compose.material3.IconButton(onClick = { onChange("") }, modifier = Modifier.semantics { contentDescription = clear }) {
                dev.pepotech.pepomote.ui.components.Ico(R.drawable.ms_close, PepoColors.TextDim, 20.dp)
            }
        }
    }
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.settings_search_hint), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { dev.pepotech.pepomote.ui.components.Ico(R.drawable.ms_search, PepoColors.TextDim, 22.dp) },
        trailingIcon = trailing,
        shape = MaterialTheme.shapes.small,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PepoColors.Blue,
            unfocusedBorderColor = PepoColors.CardBorder,
            focusedContainerColor = PepoColors.Card,
            unfocusedContainerColor = PepoColors.Card,
            cursorColor = PepoColors.Blue
        )
    )
}

/** Color de acento: un círculo por color; el elegido, con anillo y check. */
@Composable
internal fun AccentPicker() {
    val context = LocalContext.current
    val current by dev.pepotech.pepomote.ui.theme.AccentColor.state.collectAsState()
    ChipFlow {
        for (opt in dev.pepotech.pepomote.ui.theme.AccentColor.ALL) {
            val selected = opt.id == current
            val name = stringResource(accentName(opt.id))
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(2.dp)
                    .size(40.dp)
                    .border(if (selected) 3.dp else 0.dp, if (selected) PepoColors.Text else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                    .padding(4.dp)
                    .background(opt.color, androidx.compose.foundation.shape.CircleShape)
                    .clickable { dev.pepotech.pepomote.ui.theme.AccentColor.set(context, opt.id) }
                    .semantics {
                        contentDescription = name
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selected) dev.pepotech.pepomote.ui.components.Ico(R.drawable.ms_check, androidx.compose.ui.graphics.Color.White, 20.dp)
            }
        }
    }
}

private fun accentName(id: String): Int = when (id) {
    "blue" -> R.string.accent_blue
    "green" -> R.string.accent_green
    "purple" -> R.string.accent_purple
    "orange" -> R.string.accent_orange
    "pink" -> R.string.accent_pink
    "teal" -> R.string.accent_teal
    else -> R.string.accent_red
}

/** Copia de seguridad de los diseños del mando: a un archivo y de vuelta. */
@Composable
internal fun ProfileBackupCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val exportOk = stringResource(R.string.backup_export_ok)
    val failed = stringResource(R.string.backup_failed)
    val importOkFmt = stringResource(R.string.backup_import_ok)
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(PadProfileStore.exportJson().toByteArray()) }
            exportOk
        } catch (_: Exception) {
            failed
        }
    }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val n = try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
            PadProfileStore.importJson(context, text)
        } catch (_: Exception) {
            0
        }
        status = if (n > 0) importOkFmt.format(n) else failed
    }
    val profiles by PadProfileStore.profiles.collectAsState()
    // Compartir un perfil de móvil a móvil con un QR
    var sharing by remember { mutableStateOf(false) }
    val scanPrompt = stringResource(R.string.share_qr_scan_prompt)
    val qrScanner = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val n = dev.pepotech.pepomote.control.PadShare.decode(contents)?.let { PadProfileStore.importJson(context, it) } ?: 0
        status = if (n > 0) importOkFmt.format(n) else failed
    }
    if (sharing) ProfileQrDialog(profiles) { sharing = false }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_sub), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { exporter.launch("revolutionmote-perfiles.json") }, enabled = profiles.isNotEmpty()) {
                    IconLabel(R.drawable.ms_upload, stringResource(R.string.backup_export), if (profiles.isNotEmpty()) PepoColors.Blue else PepoColors.TextDim)
                }
                TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    IconLabel(R.drawable.ms_download, stringResource(R.string.backup_import), PepoColors.Blue)
                }
            }
            Row {
                TextButton(onClick = { sharing = true }, enabled = profiles.isNotEmpty()) {
                    IconLabel(R.drawable.ms_ios_share, stringResource(R.string.share_qr), if (profiles.isNotEmpty()) PepoColors.Blue else PepoColors.TextDim)
                }
                TextButton(onClick = {
                    qrScanner.launch(
                        com.journeyapps.barcodescanner.ScanOptions()
                            .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                            .setPrompt(scanPrompt)
                            .setBeepEnabled(false)
                            .setOrientationLocked(true)
                            .setCaptureActivity(dev.pepotech.pepomote.PortraitCaptureActivity::class.java)
                    )
                }) {
                    IconLabel(R.drawable.ms_center_focus_strong, stringResource(R.string.share_qr_scan), PepoColors.Blue)
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim) }
        }
    }
}

/** El QR de un perfil, para que otro móvil lo lea desde esta misma tarjeta. */
@Composable
private fun ProfileQrDialog(profiles: List<dev.pepotech.pepomote.control.PadProfile>, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(PadProfileStore.activeProfile()?.name ?: profiles.firstOrNull()?.name) }
    val profile = profiles.firstOrNull { it.name == picked } ?: profiles.firstOrNull()
    EditorDialog(onDismiss) {
        Text(stringResource(R.string.share_qr), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (profiles.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (p in profiles) ModeChip(p.name, selected = p.name == profile?.name, dense = true) { picked = p.name }
            }
            Spacer(Modifier.height(8.dp))
        }
        val text = remember(profile) { profile?.let { dev.pepotech.pepomote.control.PadShare.encode(it) } }
        if (text == null) {
            Text(stringResource(R.string.share_qr_too_big), style = MaterialTheme.typography.bodyMedium)
        } else {
            val qr = remember(text) {
                val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                    text, com.google.zxing.BarcodeFormat.QR_CODE, 600, 600,
                    mapOf(com.google.zxing.EncodeHintType.MARGIN to 2)
                )
                android.graphics.Bitmap.createBitmap(matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888).apply {
                    setPixels(IntArray(matrix.width * matrix.height) { i ->
                        if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    }, 0, matrix.width, 0, 0, matrix.width, matrix.height)
                }.asImageBitmap()
            }
            androidx.compose.foundation.Image(
                qr, stringResource(R.string.share_qr_desc),
                Modifier.size(260.dp).align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.share_qr_hint), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold)
        }
    }
}

/** Encender el PC por la red: su dirección MAC y un botón. */
@Composable
internal fun WakeOnLanCard() {
    val context = LocalContext.current
    var mac by remember { mutableStateOf(dev.pepotech.pepomote.control.AppPrefs.wolMac(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    val sent = stringResource(R.string.wol_sent)
    val failed = stringResource(R.string.wol_failed)
    val parsed = dev.pepotech.pepomote.control.WakeOnLan.parseMac(mac)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.wol_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wol_sub), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = mac,
                onValueChange = {
                    mac = it.take(17)
                    dev.pepotech.pepomote.control.AppPrefs.setWolMac(context, mac.trim())
                    status = null
                },
                singleLine = true,
                isError = mac.isNotBlank() && parsed == null,
                label = { Text(stringResource(R.string.wol_mac)) },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                enabled = parsed != null,
                onClick = {
                    val host = dev.pepotech.pepomote.net.PairStore.load(context)?.host
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    dev.pepotech.pepomote.control.WakeOnLan.send(parsed!!, host) { ok ->
                        handler.post { status = if (ok) sent else failed }
                    }
                }
            ) {
                IconLabel(R.drawable.ms_bolt, stringResource(R.string.wol_wake), if (parsed != null) PepoColors.Blue else PepoColors.TextDim)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim) }
        }
    }
}

/** Entrada a la pantalla de diagnóstico. */
@Composable
internal fun DiagEntryCard(onDiag: () -> Unit) {
    Card(
        onClick = onDiag,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            dev.pepotech.pepomote.ui.components.Ico(R.drawable.ms_monitoring, PepoColors.Blue, 26.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.diag_entry_sub), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SettingsScreen(onNewPairing: () -> Unit, onBack: () -> Unit, onDiag: () -> Unit = {}) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Sección abierta (null = el menú con todas)
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    var kbClick by remember { mutableStateOf(AppPrefs.keyboardClickFirst(context)) }
    var volB by remember { mutableStateOf(AppPrefs.volDownIsB(context)) }
    var volUpAction by remember { mutableStateOf(AppPrefs.volUpAction(context)) }
    var volDownAction by remember { mutableStateOf(AppPrefs.volDownAction(context)) }
    var rightStickMode by remember { mutableStateOf(AppPrefs.rightStickMode(context)) }
    var antiDeadzone by remember { mutableStateOf(AppPrefs.rightStickAntiDeadzone(context)) }
    var trackpadSens by remember { mutableStateOf(AppPrefs.trackpadSensitivity(context)) }
    var trackpadInertia by remember { mutableStateOf(AppPrefs.trackpadInertia(context)) }
    var rightDeadzone by remember { mutableStateOf(AppPrefs.rightStickDeadzone(context)) }
    var hapticLevel by remember { mutableStateOf(AppPrefs.hapticLevel(context)) }
    var physicalPad by remember { mutableStateOf(AppPrefs.physicalPad(context)) }
    var globalLook by remember { mutableStateOf(AppPrefs.globalLook(context)) }
    var globalGyro by remember { mutableStateOf(AppPrefs.globalGyro(context)) }
    var hapticSystem by remember { mutableStateOf(AppPrefs.hapticSystem(context)) }
    val hapticInfo = remember { Haptics.describe(context) }
    val view = LocalView.current
    val padNames = remember { PhysicalPad.connectedNames(context) }
    var sounds by remember { mutableStateOf(AppPrefs.soundsEnabled(context)) }
    var slidePress by remember { mutableStateOf(AppPrefs.slidePress(context)) }
    var stickyPress by remember { mutableStateOf(AppPrefs.stickyPress(context)) }
    var dolphinChips by remember { mutableStateOf(AppPrefs.showDolphinChips(context)) }
    var mediaEverywhere by remember { mutableStateOf(AppPrefs.mediaEverywhere(context)) }
    var noScreen by remember { mutableStateOf(AppPrefs.gamePadNoScreen(context)) }
    var fullScreen by remember { mutableStateOf(AppPrefs.gamePadFullScreen(context)) }
    var fullScreenKb by remember { mutableStateOf(AppPrefs.gamePadFullScreenKeyboard(context)) }
    var ownNunchuk by remember { mutableStateOf(AppPrefs.ownNunchuk(context)) }
    val nunchukSide by NunchukSide.saved.collectAsState()
    val gamePadSide by GamePadSide.saved.collectAsState()
    var notices by remember { mutableStateOf(AppPrefs.receiverNotices(context)) }
    var updateCheck by remember { mutableStateOf(AppPrefs.updateCheckEnabled(context)) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PepoColors.Background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))
        if (open != null && query.isBlank()) {
            // Dentro de una sección: «‹ Ajustes» para volver y su título
            TextButton(onClick = { open = null }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                dev.pepotech.pepomote.ui.components.Ico(R.drawable.ms_chevron_left, PepoColors.Blue, 22.dp)
                Text(stringResource(R.string.channel_settings), color = PepoColors.Blue, style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(open!!), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
        } else {
            Text(stringResource(R.string.channel_settings), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            SettingsSearchField(query) { query = it }
            Spacer(Modifier.height(8.dp))
            if (query.isBlank()) {
                SettingsRootList(onOpen = { open = it })
            }
        }
        val counter = remember { SettingsMatchCounter() }
        counter.count = 0
        // Las secciones, en orden; con algo en el buscador solo quedan los
        // ajustes cuyo título o explicación lo contienen
        SettingsSection(R.string.settings_sec_appearance, query, counter, open) {
            SettingItem(listOf(R.string.accent_title, R.string.accent_sub, R.string.theme_title, R.string.theme_sub, R.string.theme_light, R.string.theme_dark, R.string.theme_oled)) {
            // Tema: sistema, claro, oscuro u OLED (negro puro)
            val themeMode by ThemeMode.state.collectAsState()
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.theme_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    ChipFlow {
                        for ((label, mode) in listOf(
                            R.string.theme_system to ThemeMode.SYSTEM,
                            R.string.theme_light to ThemeMode.LIGHT,
                            R.string.theme_dark to ThemeMode.DARK,
                            R.string.theme_oled to ThemeMode.OLED
                        )) {
                            ModeChip(stringResource(label), selected = themeMode == mode) { ThemeMode.set(context, mode) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.accent_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.accent_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    AccentPicker()
                }
            }
            }
            SettingItem(listOf(R.string.sounds_title, R.string.sounds_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sounds_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.sounds_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = sounds,
                        onCheckedChange = {
                            sounds = it
                            AppPrefs.setSoundsEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }

            // Cómo se pulsan los botones del mando: deslizando de uno a otro y,
            // si no, si se mantienen al salirse el dedo. Los dos se excluyen en
            // la interfaz (deslizar manda); el valor guardado no se toca
            }
        }
        SettingsSection(R.string.settings_sec_controls, query, counter, open) {
            SettingItem(listOf(R.string.floating_title, R.string.floating_sub)) {
                var floating by remember { mutableStateOf(AppPrefs.floatingSticksDefault) }
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                    border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        SwitchRow(stringResource(R.string.floating_title), floating, stringResource(R.string.floating_sub)) {
                            floating = it
                            AppPrefs.setFloatingSticks(context, it)
                        }
                    }
                }
            }
            SettingItem(listOf(R.string.right_stick_title, R.string.right_stick_sub, R.string.mode_stick, R.string.mode_trackpad, R.string.ed_deadzone, R.string.ed_deadzone_sub, R.string.anti_deadzone_title, R.string.anti_deadzone_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(stringResource(R.string.right_stick_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.right_stick_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    ChipFlow {
                        ModeChip(
                            stringResource(R.string.mode_stick),
                            selected = rightStickMode == AppPrefs.RIGHT_STICK_MODE_ANALOG
                        ) {
                            rightStickMode = AppPrefs.RIGHT_STICK_MODE_ANALOG
                            AppPrefs.setRightStickMode(context, AppPrefs.RIGHT_STICK_MODE_ANALOG)
                        }
                        ModeChip(
                            stringResource(R.string.mode_trackpad),
                            selected = rightStickMode == AppPrefs.RIGHT_STICK_MODE_TRACKPAD
                        ) {
                            rightStickMode = AppPrefs.RIGHT_STICK_MODE_TRACKPAD
                            AppPrefs.setRightStickMode(context, AppPrefs.RIGHT_STICK_MODE_TRACKPAD)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (rightStickMode != AppPrefs.RIGHT_STICK_MODE_TRACKPAD) {
                        LabeledSlider(
                            stringResource(R.string.ed_deadzone),
                            rightDeadzone, 0f..0.5f,
                            "${(rightDeadzone * 100).roundToInt()}%",
                            stringResource(R.string.ed_deadzone_sub)
                        ) {
                            rightDeadzone = it
                            AppPrefs.setRightStickDeadzone(context, it)
                        }
                    }
                    LabeledSlider(
                        stringResource(R.string.anti_deadzone_title),
                        antiDeadzone, 0f..0.9f,
                        "${(antiDeadzone * 100).roundToInt()}%",
                        stringResource(R.string.anti_deadzone_sub)
                    ) {
                        antiDeadzone = it
                        AppPrefs.setRightStickAntiDeadzone(context, it)
                    }
                    LabeledSlider(
                        stringResource(R.string.ed_sensitivity),
                        trackpadSens, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
                        "%.2fx".format(trackpadSens),
                        log = true
                    ) {
                        trackpadSens = it
                        AppPrefs.setTrackpadSensitivity(context, it)
                    }
                    if (rightStickMode == AppPrefs.RIGHT_STICK_MODE_TRACKPAD) {
                        InertiaControls(trackpadInertia) {
                            trackpadInertia = it
                            AppPrefs.setTrackpadInertia(context, it)
                        }
                    }
                    Text(stringResource(R.string.right_stick_profiles_note), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
                }
            }
            }
            SettingItem(listOf(R.string.look_title, R.string.look_sub, R.string.gyro_title, R.string.gyro_sub, R.string.pad_settings_global_note)) {
            // Cámara en el espacio libre y giroscopio del diseño original (cada
            // perfil de diseño lleva los suyos, en «⚙ Ajustes del mando»)
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(stringResource(R.string.pad_settings_global_note), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
                    Spacer(Modifier.height(10.dp))
                    LookSettingsSection(globalLook) {
                        globalLook = it
                        AppPrefs.setGlobalLook(context, it)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = PepoColors.CardBorder)
                    Spacer(Modifier.height(12.dp))
                    GyroSettingsSection(globalGyro) {
                        globalGyro = it
                        AppPrefs.setGlobalGyro(context, it)
                    }
                }
            }
            }
            SettingItem(listOf(R.string.slide_press_title, R.string.slide_press_sub, R.string.sticky_press_title, R.string.sticky_press_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.slide_press_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.slide_press_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = slidePress,
                        onCheckedChange = {
                            slidePress = it
                            AppPrefs.setSlidePress(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp, end = 18.dp, bottom = 16.dp)
                        .alpha(if (slidePress) 0.5f else 1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sticky_press_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.sticky_press_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = stickyPress,
                        enabled = !slidePress,
                        onCheckedChange = {
                            stickyPress = it
                            AppPrefs.setStickyPress(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }
            }
            SettingItem(listOf(R.string.physical_pad_title, R.string.physical_pad_sub, R.string.physical_pad_none, R.string.physical_pad_found)) {
            // Mando físico / telescópico: sus botones van al PC por la app
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.physical_pad_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.physical_pad_sub), style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = physicalPad,
                            onCheckedChange = {
                                physicalPad = it
                                AppPrefs.setPhysicalPad(context, it)
                                if (!it) PhysicalPad.releaseAll()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (padNames.isEmpty()) stringResource(R.string.physical_pad_none)
                        else stringResource(R.string.physical_pad_found, padNames.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (padNames.isEmpty()) PepoColors.TextDim else PepoColors.Ok
                    )
                }
            }
            }
            SettingItem(listOf(R.string.vol_title, R.string.vol_sub, R.string.vol_up_label, R.string.vol_down_label)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.vol_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.vol_sub),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = volB,
                            onCheckedChange = {
                                volB = it
                                AppPrefs.setVolDownIsB(context, it)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                        )
                    }

                    if (volB) {
                                    Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.vol_up_label), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        ChipFlow {
                            for ((label, act) in listOf(
                                "ZR / RT" to "ZR",
                                "ZL / LT" to "ZL",
                                "R / RB" to "R",
                                "L / LB" to "L",
                                "A" to "A",
                                "B" to "B",
                                "X" to "X",
                                "Y" to "Y",
                                "R3" to "STICK_R",
                                "L3" to "STICK_L"
                            )) {
                                ModeChip(label, selected = volUpAction == act) {
                                    volUpAction = act
                                    AppPrefs.setVolUpAction(context, act)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.vol_down_label), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        ChipFlow {
                            for ((label, act) in listOf(
                                "ZR / RT" to "ZR",
                                "ZL / LT" to "ZL",
                                "R / RB" to "R",
                                "L / LB" to "L",
                                "A" to "A",
                                "B" to "B",
                                "X" to "X",
                                "Y" to "Y",
                                "R3" to "STICK_R",
                                "L3" to "STICK_L"
                            )) {
                                ModeChip(label, selected = volDownAction == act) {
                                    volDownAction = act
                                    AppPrefs.setVolDownAction(context, act)
                                }
                            }
                        }
                    }
                }
            }
            }
            SettingItem(listOf(R.string.backup_title, R.string.backup_sub)) { ProfileBackupCard() }
            SettingItem(listOf(R.string.wol_title, R.string.wol_sub)) { WakeOnLanCard() }
            SettingItem(listOf(R.string.layout_reset_title, R.string.layout_reset_btn)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(stringResource(R.string.layout_reset_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            PadProfileStore.deleteAll(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.layout_reset_btn), color = PepoColors.Blue)
                    }
                }
            }
            }
        }
        SettingsSection(R.string.settings_sec_pointer, query, counter, open) {
            SettingItem(listOf(R.string.motion_title, R.string.motion_sub)) {
            // Sensor del puntero: giroscopio o acelerómetro (móviles sin giroscopio real)
            MotionSourceCard()
            }
            SettingItem(listOf(R.string.pointer_trackpad_title, R.string.pointer_trackpad_sub, R.string.pointer_mode_touch, R.string.pointer_mode_both)) {
            // Modo puntero: el ratón con el dedo
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) { PointerTrackpadSettings() }
            }
            }
            SettingItem(listOf(R.string.kb_click_title, R.string.kb_click_sub)) {
            // Modo puntero: el botón «Teclado» hace antes clic donde apuntas
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.kb_click_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.kb_click_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = kbClick,
                        onCheckedChange = {
                            kbClick = it
                            AppPrefs.setKeyboardClickFirst(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }
            }
            SettingItem(listOf(R.string.chips_title, R.string.chips_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.chips_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.chips_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = dolphinChips,
                        onCheckedChange = {
                            dolphinChips = it
                            AppPrefs.setShowDolphinChips(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }

            // Fila multimedia del mando vertical: solo en modo puntero (el PC no
            // atiende esas teclas en los demás) o, con esto, en todos los modos
            }
            SettingItem(listOf(R.string.media_everywhere_title, R.string.media_everywhere_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.media_everywhere_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.media_everywhere_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = mediaEverywhere,
                        onCheckedChange = {
                            mediaEverywhere = it
                            AppPrefs.setMediaEverywhere(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }

            // Nunchuk en el mismo móvil (Dolphin): stick, C y Z con el móvil de lado
            }
        }
        SettingsSection(R.string.settings_sec_vibration, query, counter, open) {
            SettingItem(listOf(R.string.haptic_title, R.string.haptic_sub, R.string.haptic_subtle, R.string.haptic_crisp, R.string.haptic_strong, R.string.haptic_system_title, R.string.haptic_system_sub, R.string.haptic_test)) {
            // Vibración de los botones: clic nítido tipo microswitch, con su fuerza
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(stringResource(R.string.haptic_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.haptic_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    ChipFlow {
                        for ((label, lvl) in listOf(
                            R.string.haptic_off to Haptics.OFF,
                            R.string.haptic_subtle to Haptics.SUBTLE,
                            R.string.haptic_crisp to Haptics.CRISP,
                            R.string.haptic_strong to Haptics.STRONG,
                            R.string.haptic_custom to Haptics.CUSTOM
                        )) {
                            ModeChip(stringResource(label), selected = hapticLevel == lvl, dense = true) {
                                hapticLevel = lvl
                                AppPrefs.setHapticLevel(context, lvl)
                                Haptics.preview(view, lvl)
                            }
                        }
                    }
                    if (hapticLevel == Haptics.CUSTOM) HapticCustomEditor()
                    Spacer(Modifier.height(12.dp))
                    HapticSourcesEditor()
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        stringResource(R.string.haptic_system_title),
                        hapticSystem,
                        stringResource(R.string.haptic_system_sub)
                    ) {
                        hapticSystem = it
                        AppPrefs.setHapticSystem(context, it)
                        Haptics.preview(view, hapticLevel)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { Haptics.preview(view, maxOf(hapticLevel, Haptics.SUBTLE)) }) {
                            IconLabel(R.drawable.ms_vibration, stringResource(R.string.haptic_test), PepoColors.Blue)
                        }
                        Text(hapticInfo, style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
                    }
                }
            }
            }
            SettingItem(listOf(R.string.rumble_title, R.string.rumble_sub)) {
            GameRumbleCard()
            }
        }
        SettingsSection(R.string.settings_sec_wii, query, counter, open) {
            SettingItem(listOf(R.string.nunchuk_own_title, R.string.nunchuk_own_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.nunchuk_own_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.nunchuk_own_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = ownNunchuk,
                        onCheckedChange = {
                            ownNunchuk = it
                            AppPrefs.setOwnNunchuk(context, it)
                            // Con el enlace vivo se aplica ya (el receptor lo confirma con el eco)
                            LinkState.sendNunchuk?.invoke(it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }

            // Lado del mando + Nunchuk apaisado: se pregunta la primera vez; aquí se cambia
            }
            SettingItem(listOf(R.string.side_title, R.string.side_sub)) {
            SideCard(R.string.side_title, R.string.side_sub, nunchukSide) { NunchukSide.save(context, it) }

            // GamePad de Wii U sin pantalla táctil: botones más grandes
            }
            SettingItem(listOf(R.string.noscreen_title, R.string.noscreen_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.noscreen_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.noscreen_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = noScreen,
                        onCheckedChange = {
                            noScreen = it
                            AppPrefs.setGamePadNoScreen(context, it)
                            if (it) fullScreen = false
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }

            // Pantalla del GamePad a pantalla completa (mando real en el PC), con la
            // subopción del botón de teclado
            }
            SettingItem(listOf(R.string.fullscreen_title, R.string.fullscreen_sub, R.string.fullscreen_kb_title, R.string.fullscreen_kb_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fullscreen_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.fullscreen_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = fullScreen,
                        onCheckedChange = {
                            fullScreen = it
                            AppPrefs.setGamePadFullScreen(context, it)
                            if (it) noScreen = false
                            LinkState.sendScreenOnly?.invoke(it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp, end = 18.dp, bottom = 16.dp)
                        .alpha(if (fullScreen) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fullscreen_kb_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.fullscreen_kb_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = fullScreenKb,
                        enabled = fullScreen,
                        onCheckedChange = {
                            fullScreenKb = it
                            AppPrefs.setGamePadFullScreenKeyboard(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }

            // Lado del GamePad de Wii U: el suyo, aparte del mando + Nunchuk
            }
            SettingItem(listOf(R.string.side_gamepad_title, R.string.side_gamepad_sub)) {
            SideCard(R.string.side_gamepad_title, R.string.side_gamepad_sub, gamePadSide) { GamePadSide.save(context, it) }

            // Avisos del receptor sobre el mando al cambiar de modo
            }
        }
        SettingsSection(R.string.settings_sec_app, query, counter, open) {
            SettingItem(listOf(R.string.notices_title, R.string.notices_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.notices_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.notices_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = notices,
                        onCheckedChange = {
                            notices = it
                            AppPrefs.setReceiverNotices(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }

            // Aviso de versión nueva: la única consulta fuera de la red local
            }
            SettingItem(listOf(R.string.update_title, R.string.update_sub)) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.update_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = updateCheck,
                        onCheckedChange = {
                            updateCheck = it
                            AppPrefs.setUpdateCheckEnabled(context, it)
                            UpdateNotice.settingsChanged(context)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                    )
                }
            }
            }
            SettingItem(listOf(R.string.update_title)) {
            UpdateSettingsCard()
            }
            SettingItem(listOf(R.string.link_other_pc, R.string.link_other_pc_sub)) {
            Card(
                onClick = onNewPairing,
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
                border = BorderStroke(1.5.dp, PepoColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(stringResource(R.string.link_other_pc), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.link_other_pc_sub), style = MaterialTheme.typography.bodyMedium)
                }
            }
            }
            SettingItem(listOf(R.string.diag_title, R.string.diag_entry_sub)) { DiagEntryCard(onDiag) }
        }
        if (query.isNotBlank() && counter.count == 0) {
            Text(stringResource(R.string.settings_search_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
        }
        if (open == null) {
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.settings_override_note),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.about, versionName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back), color = PepoColors.TextDim)
        }
        }
        Spacer(Modifier.height(16.dp))
    }
}
