package dev.pepotech.pepomote.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.AppPrefs
import dev.pepotech.pepomote.control.BtHid
import dev.pepotech.pepomote.control.GyroAim
import dev.pepotech.pepomote.control.Kbm
import dev.pepotech.pepomote.control.MouseConfig
import dev.pepotech.pepomote.net.PairStore
import dev.pepotech.pepomote.service.LinkForegroundService
import dev.pepotech.pepomote.service.LinkState
import dev.pepotech.pepomote.service.UiLink
import dev.pepotech.pepomote.ui.components.Ico
import dev.pepotech.pepomote.ui.theme.PepoColors

/** Por dónde va el teclado y ratón de la pantalla dedicada. */
internal object KbmTransport {
    const val APP = "app"
    const val BT = "bt"
}

/**
 * Teclado y ratón: el móvil como touchpad, clics, rueda, teclado y teclas
 * especiales. Va por la app (receptor de RevolutionMote+ en el PC) o por
 * Bluetooth (sin instalar nada en el otro equipo). Al entrar se elige, salvo
 * que haya uno predeterminado; desde la propia pantalla se cambia.
 */
@Composable
fun KbmScreen(onBack: () -> Unit, onPair: () -> Unit) {
    val context = LocalContext.current
    var transport by remember { mutableStateOf(AppPrefs.kbmTransport(context)) }
    var asking by remember { mutableStateOf(transport.isEmpty()) }
    var isDefault by remember { mutableStateOf(transport.isNotEmpty()) }
    var mouse by remember { mutableStateOf(AppPrefs.globalMouse(context)) }
    var showSettings by remember { mutableStateOf(false) }
    val link by LinkState.flow.collectAsState()
    val bt by BtHid.state.collectAsState()

    // Bluetooth: permisos de Android 12+ y arrancar el perfil HID
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { ok -> ok }) BtHid.start(context)
    }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // Por dónde sale lo que se toca
    DisposableEffect(transport, asking) {
        when {
            asking || transport.isEmpty() -> Kbm.backend = Kbm.Backend.NONE
            transport == KbmTransport.BT -> {
                Kbm.backend = Kbm.Backend.BT
                val missing = BtHid.permissions.filter {
                    context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) BtHid.start(context) else permLauncher.launch(missing.toTypedArray())
            }
            else -> Kbm.backend = Kbm.Backend.NONE // la app: hasta que el PC diga que sabe
        }
        onDispose {
            Kbm.backend = Kbm.Backend.NONE
            if (transport == KbmTransport.BT) BtHid.stop()
        }
    }

    // Por la app: conectar con el PC emparejado y, si entiende teclado y
    // ratón, pasarlo a un modo que no mueva el cursor con el giro del móvil
    val connected = link as? UiLink.Connected
    val appReady = transport == KbmTransport.APP && !asking && connected?.supportsKbm == true
    LaunchedEffect(transport, asking) {
        if (transport == KbmTransport.APP && !asking && !LinkState.flow.value.alive && PairStore.load(context) != null) {
            LinkForegroundService.start(context)
        }
    }
    LaunchedEffect(appReady) {
        if (appReady) {
            Kbm.backend = Kbm.Backend.APP
            val c = LinkState.flow.value as? UiLink.Connected
            if (c != null && c.mode == LinkState.MODE_POINTER) {
                LinkState.requestMode(if (c.supportsGamepad) LinkState.MODE_GAMEPAD else LinkState.MODE_DOLPHIN)
            }
        } else if (transport == KbmTransport.APP) {
            Kbm.backend = Kbm.Backend.NONE
        }
    }
    // Con el PC conectado: que los paquetes del giro no apunten (el ratón lo
    // mueve esta pantalla, en relativo)
    val engine = LinkState.motion
    DisposableEffect(engine, appReady) {
        val prev = engine?.padAim
        if (appReady) engine?.padAim = false
        onDispose { if (prev != null) engine?.padAim = prev }
    }

    // El giro como ratón: sensores propios (sin PC no hay motor de sensores)
    GyroMouseSensors(enabled = mouse.gyro && !asking && transport.isNotEmpty(), mouse = mouse)

    Box(
        Modifier
            .fillMaxSize()
            .background(PepoColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Cabecera: atrás, por dónde va, estado, ajustes
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { IconLabel(R.drawable.ms_close, stringResource(R.string.kbm_exit), PepoColors.Error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { asking = true }) {
                    IconLabel(
                        if (transport == KbmTransport.BT) R.drawable.ms_bluetooth else R.drawable.ms_wifi,
                        stringResource(if (transport == KbmTransport.BT) R.string.kbm_bt else R.string.kbm_app),
                        PepoColors.Blue
                    )
                }
                TextButton(onClick = { showSettings = true }) { IconLabel(R.drawable.ms_tune, stringResource(R.string.ed_pad_settings), PepoColors.Blue) }
            }
            KbmStatus(transport, asking, link, bt, onPair, onBluetoothVisible = {
                discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
            })
            Spacer(Modifier.height(6.dp))
            // El touchpad grande
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                MousePad(maxWidth, maxHeight, mouseMode = true, sensitivity = mouse.padSens, antiDeadzone = 0f, inertia = mouse.inertia)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((idx, label) in listOf(0 to R.string.ed_mouse_left, 2 to R.string.kbm_middle, 1 to R.string.ed_mouse_right)) {
                    HoldKey(stringResource(label), Modifier.weight(1f).height(54.dp)) { down -> Kbm.button(idx, down) }
                }
            }
            Spacer(Modifier.height(8.dp))
            SpecialKeys()
            Spacer(Modifier.height(8.dp))
            TextSender()
        }

        if (asking) {
            TransportDialog(
                current = transport,
                rememberChoice = isDefault,
                btSupported = BtHid.supported(context),
                onPick = { t, def ->
                    transport = t
                    isDefault = def
                    AppPrefs.setKbmTransport(context, if (def) t else "")
                    asking = false
                },
                onDismiss = { if (transport.isEmpty()) onBack() else asking = false }
            )
        }
        if (showSettings) {
            EditorDialog({ showSettings = false }) {
                MouseSettingsSection(mouse) {
                    mouse = it
                    AppPrefs.setGlobalMouse(context, it)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isDefault,
                        onCheckedChange = {
                            isDefault = it
                            AppPrefs.setKbmTransport(context, if (it) transport else "")
                        },
                        colors = CheckboxDefaults.colors(checkedColor = PepoColors.Blue)
                    )
                    Text(stringResource(R.string.kbm_remember), style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = { showSettings = false }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Qué pasa con la conexión, y qué hacer si falta algo. */
@Composable
private fun KbmStatus(
    transport: String,
    asking: Boolean,
    link: UiLink,
    bt: BtHid.State,
    onPair: () -> Unit,
    onBluetoothVisible: () -> Unit
) {
    if (asking || transport.isEmpty()) return
    val context = LocalContext.current
    val (text, color) = if (transport == KbmTransport.APP) {
        val c = link as? UiLink.Connected
        when {
            PairStore.load(context) == null -> stringResource(R.string.kbm_app_unpaired) to PepoColors.Warn
            c == null -> stringResource(R.string.kbm_app_connecting) to PepoColors.TextDim
            !c.supportsKbm -> stringResource(R.string.kbm_app_old_receiver) to PepoColors.Warn
            else -> stringResource(R.string.kbm_app_ready, c.pcName) to PepoColors.Ok
        }
    } else {
        when (bt.status) {
            BtHid.Status.UNSUPPORTED -> stringResource(R.string.kbm_bt_unsupported) to PepoColors.Error
            BtHid.Status.OFF -> stringResource(R.string.kbm_bt_off) to PepoColors.Warn
            BtHid.Status.NO_PERMISSION -> stringResource(R.string.kbm_bt_permission) to PepoColors.Warn
            BtHid.Status.STARTING -> stringResource(R.string.kbm_bt_starting) to PepoColors.TextDim
            BtHid.Status.READY -> stringResource(R.string.kbm_bt_ready) to PepoColors.TextDim
            BtHid.Status.CONNECTED -> stringResource(R.string.kbm_bt_connected, bt.host ?: "") to PepoColors.Ok
        }
    }
    Column {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        if (transport == KbmTransport.APP && PairStore.load(context) == null) {
            TextButton(onClick = onPair) { IconLabel(R.drawable.ms_add, stringResource(R.string.kbm_pair), PepoColors.Blue) }
        }
        if (transport == KbmTransport.BT && bt.status == BtHid.Status.READY) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBluetoothVisible) { IconLabel(R.drawable.ms_bluetooth, stringResource(R.string.kbm_bt_visible), PepoColors.Blue) }
                for (d in BtHid.bonded().take(4)) {
                    val name = runCatching { d.name }.getOrNull() ?: d.address
                    TextButton(onClick = { BtHid.connect(d) }) { Text(name, color = PepoColors.Text, maxLines = 1) }
                }
            }
        }
    }
}

/** Elegir por dónde va: la app o Bluetooth, y si se recuerda. */
@Composable
private fun TransportDialog(
    current: String,
    rememberChoice: Boolean,
    btSupported: Boolean,
    onPick: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var def by remember { mutableStateOf(rememberChoice) }
    EditorDialog(onDismiss) {
        Text(stringResource(R.string.kbm_choose_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        TransportOption(
            R.drawable.ms_wifi, stringResource(R.string.kbm_app), stringResource(R.string.kbm_app_sub),
            current == KbmTransport.APP
        ) { onPick(KbmTransport.APP, def) }
        TransportOption(
            R.drawable.ms_bluetooth, stringResource(R.string.kbm_bt),
            stringResource(if (btSupported) R.string.kbm_bt_sub else R.string.kbm_bt_unsupported),
            current == KbmTransport.BT,
            enabled = btSupported
        ) { onPick(KbmTransport.BT, def) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = def, onCheckedChange = { def = it }, colors = CheckboxDefaults.colors(checkedColor = PepoColors.Blue))
            Text(stringResource(R.string.kbm_remember), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TransportOption(
    @androidx.annotation.DrawableRes icon: Int,
    title: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) PepoColors.Blue.copy(alpha = 0.15f) else PepoColors.Card, shape)
            .border(1.5.dp, if (selected) PepoColors.Blue else PepoColors.CardBorder, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(icon, if (enabled) PepoColors.Blue else PepoColors.TextDim, 28.dp, Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) PepoColors.Text else PepoColors.TextDim)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = PepoColors.TextDim)
        }
    }
}

/** Una tecla de mantener (botones del ratón). */
@Composable
private fun HoldKey(label: String, modifier: Modifier, onPress: (Boolean) -> Unit) {
    val view = LocalView.current
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .background(if (down) PepoColors.Blue else PepoColors.Card, shape)
            .border(1.5.dp, if (down) PepoColors.Blue else PepoColors.CardBorder, shape)
            .pointerInputHold(view) { d ->
                down = d
                onPress(d)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (down) PepoColors.OnAccent else PepoColors.Text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Teclas especiales. Ctrl, Alt, Shift y Win se quedan pulsadas con un toque
 * (para combinaciones: Ctrl y luego C) y se sueltan con otro toque o tras la
 * siguiente tecla.
 */
@Composable
private fun SpecialKeys() {
    val sticky = remember { mutableStateListOf<String>() }
    fun tapKey(name: String) {
        Kbm.tap(name)
        for (m in sticky.toList()) Kbm.key(m, false)
        sticky.clear()
    }
    val mods = listOf("ControlLeft" to "Ctrl", "AltLeft" to "Alt", "ShiftLeft" to "Shift", "MetaLeft" to "Win")
    val keys = listOf(
        "Escape" to "Esc", "Tab" to "Tab", "Enter" to "Intro", "Backspace" to "Borrar", "Delete" to "Supr",
        "ArrowLeft" to "←", "ArrowUp" to "↑", "ArrowDown" to "↓", "ArrowRight" to "→",
        "Home" to "Inicio", "End" to "Fin", "PageUp" to "RePág", "PageDown" to "AvPág"
    ) + (1..12).map { "F$it" to "F$it" }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((name, label) in mods) {
            ModeChip(label, selected = name in sticky, toggle = true) {
                if (name in sticky) {
                    sticky.remove(name)
                    Kbm.key(name, false)
                } else {
                    sticky.add(name)
                    Kbm.key(name, true)
                }
            }
        }
        for ((name, label) in keys) {
            ModeChip(label, selected = false) { tapKey(name) }
        }
    }
}

/** Escribir un texto y mandarlo de golpe. */
@Composable
private fun TextSender() {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.kbm_type_here)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                Kbm.text(text)
                text = ""
            }),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = {
            Kbm.text(text)
            text = ""
        }) { IconLabel(R.drawable.ms_keyboard, stringResource(R.string.kbm_send), PepoColors.Blue) }
    }
}

/** El giro del móvil como ratón, con sus propios sensores. */
@Composable
private fun GyroMouseSensors(enabled: Boolean, mouse: MouseConfig) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> System.arraycopy(e.values, 0, accel, 0, 3)
                    Sensor.TYPE_GYROSCOPE -> GyroAim.onSample(e.values, accel, view.display?.rotation ?: 0)
                }
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        GyroAim.mouse = true
        GyroAim.active = true
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose {
            sm.unregisterListener(listener)
            GyroAim.active = false
            GyroAim.mouse = false
        }
    }
    DisposableEffect(mouse) {
        GyroAim.mouseConfig = mouse
        onDispose { }
    }
}

/** Mantener pulsado (sin umbral de arrastre), con su háptica. */
private fun Modifier.pointerInputHold(view: android.view.View, onDown: (Boolean) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val d = awaitFirstDown()
            d.consume()
            dev.pepotech.pepomote.control.Haptics.tap(view, android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            onDown(true)
            try {
                waitForUpOrCancellation()
            } finally {
                onDown(false)
                dev.pepotech.pepomote.control.Haptics.release(view)
            }
        }
    }
