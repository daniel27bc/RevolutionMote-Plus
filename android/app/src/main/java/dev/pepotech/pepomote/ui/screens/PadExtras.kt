package dev.pepotech.pepomote.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.AppPrefs
import dev.pepotech.pepomote.control.ButtonState
import dev.pepotech.pepomote.control.PointerTrackpad
import dev.pepotech.pepomote.control.GyroActivation
import dev.pepotech.pepomote.control.GyroAim
import dev.pepotech.pepomote.control.GyroConfig
import dev.pepotech.pepomote.control.Kbm
import dev.pepotech.pepomote.control.Haptics
import dev.pepotech.pepomote.control.LookConfig
import dev.pepotech.pepomote.control.LookZone
import dev.pepotech.pepomote.control.MouseConfig
import dev.pepotech.pepomote.control.PadProfiles
import dev.pepotech.pepomote.control.PressHit
import dev.pepotech.pepomote.control.PressMode
import dev.pepotech.pepomote.control.UiSounds
import dev.pepotech.pepomote.ui.components.Ico
import dev.pepotech.pepomote.ui.components.LocalPressRegistry
import dev.pepotech.pepomote.ui.components.trackpadInput
import dev.pepotech.pepomote.ui.components.mouseGestures
import androidx.compose.runtime.key
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlin.math.roundToInt

/**
 * Cámara en el espacio libre (como en CoD Mobile): una superficie invisible
 * DEBAJO de los controles. Un dedo que cae en un hueco sin controles gira la
 * cámara como un trackpad (stick derecho); los controles de encima siguen
 * cogiendo sus propios toques. Con «Pulsar deslizando» los botones no cogen
 * el toque, así que aquí se mira que no haya un botón debajo.
 */
@Composable
internal fun LookSurface(look: LookConfig, modifier: Modifier = Modifier) {
    if (look.zone == LookZone.OFF) return
    val registry = LocalPressRegistry.current
    val origin = remember { FloatArray(2) }
    DisposableEffect(Unit) { onDispose { ButtonState.setStick2From(ButtonState.SRC_LOOK, 0, 0) } }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (look.zone == LookZone.RIGHT) 0.5f else 1f)
                .align(Alignment.CenterEnd)
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    origin[0] = p.x
                    origin[1] = p.y
                }
                .trackpadInput(
                    sensitivity = look.sensitivity,
                    antiDeadzone = look.antiDeadzone,
                    invertY = look.invertY,
                    inertia = look.inertia,
                    accept = { pos: Offset ->
                        !(PressMode.slide && registry != null &&
                            PressHit.resolve(registry.zones(), pos.x + origin[0], pos.y + origin[1]) != null)
                    }
                ) { x, y -> ButtonState.setStick2From(ButtonState.SRC_LOOK, x, y) }
        )
    }
}

/**
 * Cámara también desde los botones: con la cámara en el espacio libre, un
 * dedo que pulsa un botón (saltar, por ejemplo) y luego se desliza sigue
 * girando la cámara, como en los juegos de móvil (girarse en el aire). Mira
 * los dedos ANTES que los botones y no consume nada: el botón sigue pulsado.
 * Un dedo es «de botón» si al bajar se ha pulsado un botón nuevo (así los
 * sticks y la cámara normal no cuentan dos veces).
 */
@Composable
internal fun Modifier.buttonLook(enabled: Boolean, look: LookConfig): Modifier {
    val cfg by rememberUpdatedState(look)
    val v = remember { FloatArray(2) }
    val lastMove = remember { LongArray(1) }
    val fingers = remember { IntArray(1) }
    var running by remember { mutableStateOf(false) }
    DisposableEffect(enabled) { onDispose { ButtonState.setStick2From(ButtonState.SRC_BUTTON_LOOK, 0, 0) } }
    androidx.compose.runtime.LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0 to 0
        while (true) {
            androidx.compose.runtime.withFrameMillis { }
            if (fingers[0] == 0) {
                v[0] = 0f; v[1] = 0f
                ButtonState.setStick2From(ButtonState.SRC_BUTTON_LOOK, 0, 0)
                running = false
                break
            }
            if (android.os.SystemClock.uptimeMillis() - lastMove[0] > 24L) {
                v[0] *= 0.45f; v[1] *= 0.45f
            }
            val c = cfg
            val (x, y) = dev.pepotech.pepomote.control.TrackpadEngine.fromVelocity(v[0], v[1], c.sensitivity, c.antiDeadzone)
            val out = x to (if (c.invertY) -y else y)
            if (out != last) {
                last = out
                ButtonState.setStick2From(ButtonState.SRC_BUTTON_LOOK, out.first, out.second)
            }
        }
    }
    if (!enabled) return this
    return this.pointerInput(Unit) {
        // id → bits pulsados al bajar; null = aún no se sabe si es de botón
        val start = HashMap<Long, Int>()
        val buttonFingers = HashSet<Long>()
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                for (ch in ev.changes) {
                    val id = ch.id.value
                    if (ch.pressed && !ch.previousPressed) {
                        val inZone = cfg.zone == LookZone.ALL || (cfg.zone == LookZone.RIGHT && ch.position.x > size.width / 2f)
                        if (inZone) start[id] = ButtonState.current()
                        continue
                    }
                    if (!ch.pressed) {
                        start.remove(id)
                        if (buttonFingers.remove(id)) fingers[0] = buttonFingers.size
                        continue
                    }
                    // Primer movimiento: ¿pulsó un botón nuevo al bajar?
                    val before = start.remove(id)
                    if (before != null && ButtonState.current() and before.inv() != 0) {
                        buttonFingers += id
                        fingers[0] = buttonFingers.size
                        running = true
                    }
                    if (id in buttonFingers) {
                        val d = ch.positionChangeIgnoreConsumed()
                        val dt = (ch.uptimeMillis - ch.previousUptimeMillis).coerceAtLeast(1L)
                        v[0] += (d.x / density / dt - v[0]) * 0.55f
                        v[1] += (d.y / density / dt - v[1]) * 0.55f
                        lastMove[0] = android.os.SystemClock.uptimeMillis()
                    }
                }
            }
        }
    }
}

/**
 * Trackpad de ratón (modo puntero): una superficie invisible debajo de los
 * controles; el dedo en un hueco mueve el cursor del PC ([PointerTrackpad]).
 * Mientras está puesta, mover el móvil no mueve el cursor.
 */
@Composable
internal fun PointerTrackpadLayer(
    modifier: Modifier = Modifier,
    manageActive: Boolean = true,
    inertia: Float? = null
) {
    val context = LocalContext.current
    val inert = inertia ?: remember { AppPrefs.pointerTrackpadInertia(context) }
    if (manageActive) {
        DisposableEffect(Unit) {
            PointerTrackpad.sensitivity = AppPrefs.pointerTrackpadSens(context)
            PointerTrackpad.active = true
            onDispose { PointerTrackpad.active = false }
        }
    }
    val gestures = remember { AppPrefs.touchGestures(context) }
    // El mismo touchpad completo que en el modo ratón: toque = A (clic),
    // dos dedos = B, doble toque y mantener = arrastrar con A
    Box(modifier.mouseGestures(dev.pepotech.pepomote.ui.components.PointerOut, 1f, inert, gestures))
}

/**
 * Mando universal en modo ratón: los huecos libres mueven el cursor ([Kbm]),
 * con la sensibilidad de los touchpads y su inercia.
 */
@Composable
internal fun MouseFreeLayer(modifier: Modifier, sensitivity: Float, inertia: Float) {
    val context = LocalContext.current
    val gestures = remember { AppPrefs.touchGestures(context) }
    DisposableEffect(Unit) { onDispose { Kbm.setTouch(Kbm.SRC_FREE, 0f, 0f) } }
    // Los huecos libres son un touchpad completo: clic, doble clic, agarrar…
    Box(modifier.mouseGestures(dev.pepotech.pepomote.ui.components.KbmOut(Kbm.SRC_FREE), sensitivity, inertia, gestures))
}

/**
 * En las pantallas del mando de Wii: con el modo puntero y el trackpad en
 * «solo el dedo» o «los dos», los huecos sin botones mueven el cursor. Va
 * DENTRO de la caja de la pantalla (la que lleva `slideCanvas`), debajo de
 * los botones.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.PointerPadUnderlay(link: dev.pepotech.pepomote.service.UiLink) {
    val mode by PointerTrackpad.mode.collectAsState()
    val pointer = (link as? dev.pepotech.pepomote.service.UiLink.Connected)?.mode == dev.pepotech.pepomote.service.LinkState.MODE_POINTER
    if (mode != PointerTrackpad.OFF && pointer) {
        DisposableEffect(mode) {
            PointerTrackpad.mixGyro = mode == PointerTrackpad.BOTH
            onDispose { PointerTrackpad.mixGyro = false }
        }
        PointerTrackpadLayer(Modifier.matchParentSize())
    }
}

private val POINTER_MODES = listOf(
    Triple(PointerTrackpad.OFF, R.string.pointer_mode_aim, R.string.pointer_mode_aim_desc),
    Triple(PointerTrackpad.ON, R.string.pointer_mode_touch, R.string.pointer_mode_touch_desc),
    Triple(PointerTrackpad.BOTH, R.string.pointer_mode_both, R.string.pointer_mode_both_desc)
)

@Composable
private fun PointerModeIcon(mode: Int, tint: androidx.compose.ui.graphics.Color, size: Dp) {
    when (mode) {
        PointerTrackpad.OFF -> Ico(R.drawable.ms_my_location, tint, size)
        PointerTrackpad.ON -> Ico(R.drawable.ms_touchpad_mouse, tint, size)
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_my_location, tint, size * 0.72f)
            Ico(R.drawable.ms_touchpad_mouse, tint, size * 0.72f)
        }
    }
}

/**
 * Selector de tres posiciones del modo puntero: apuntar con el móvil, solo
 * el dedo, o los dos a la vez. Iconos para no ocupar; mantener pulsado
 * cualquiera explica lo que hace.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PointerModeSwitch(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val mode by PointerTrackpad.mode.collectAsState()
    var hint by remember { mutableStateOf<Int?>(null) }
    val pill = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val seg = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)
    Box(modifier) {
        Row(
            Modifier
                .background(PepoColors.Card, pill)
                .border(1.5.dp, PepoColors.CardBorder, pill)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for ((m, label, _) in POINTER_MODES) {
                val selected = mode == m
                val name = stringResource(label)
                Box(
                    Modifier
                        .height(28.dp)
                        .padding(horizontal = 1.dp)
                        .background(if (selected) PepoColors.Blue else androidx.compose.ui.graphics.Color.Transparent, seg)
                        .combinedClickable(
                            onClickLabel = name,
                            onLongClick = {
                                Haptics.tap(view, HapticFeedbackConstants.LONG_PRESS)
                                hint = m
                            }
                        ) {
                            Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP)
                            PointerTrackpad.setMode(context, m)
                        }
                        .semantics {
                            contentDescription = name
                            this.selected = selected
                        }
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PointerModeIcon(m, if (selected) PepoColors.OnAccent else PepoColors.TextDim, 18.dp)
                }
            }
        }
        hint?.let { h ->
            val (_, label, desc) = POINTER_MODES[h]
            androidx.compose.runtime.LaunchedEffect(h) {
                kotlinx.coroutines.delay(4000)
                hint = null
            }
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, 110),
                onDismissRequest = { hint = null }
            ) {
                Column(
                    Modifier
                        .padding(8.dp)
                        .widthIn(max = 260.dp)
                        .shadow(6.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(PepoColors.Card, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PointerModeIcon(h, PepoColors.Blue, 18.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Un globo con el título y lo que hace un botón (mantener pulsado para verlo). */
@Composable
internal fun HintPopup(title: String, desc: String, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(title) {
        kotlinx.coroutines.delay(4000)
        onDismiss()
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(0, 110),
        onDismissRequest = onDismiss
    ) {
        Column(
            Modifier
                .padding(8.dp)
                .widthIn(max = 260.dp)
                .shadow(6.dp, shape)
                .background(PepoColors.Card, shape)
                .padding(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Botón de icono: un toque lo usa y mantenerlo explica qué hace. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun HintIconButton(
    @androidx.annotation.DrawableRes icon: Int,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var hint by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(36.dp)
            .combinedClickable(
                onClickLabel = title,
                onLongClick = {
                    Haptics.tap(view, HapticFeedbackConstants.LONG_PRESS)
                    hint = true
                },
                onClick = onClick
            )
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center
    ) {
        Ico(icon, tint, 20.dp)
        if (hint) HintPopup(title, desc) { hint = false }
    }
}

/**
 * Arriba a la derecha del mando de Wii: en modo puntero, el selector del
 * trackpad y sus ajustes (⚙); y el candado de orientación. Mantener
 * cualquiera explica lo que hace.
 */
@Composable
internal fun PointerQuickControls(link: dev.pepotech.pepomote.service.UiLink, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pointer = (link as? dev.pepotech.pepomote.service.UiLink.Connected)?.mode == dev.pepotech.pepomote.service.LinkState.MODE_POINTER
    val lock by dev.pepotech.pepomote.control.OrientationLock.state.collectAsState()
    val rotation = rememberDisplayRotation()
    val locked = lock != AppPrefs.ORIENTATION_FREE
    var settings by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (pointer) {
            PointerModeSwitch()
            HintIconButton(
                R.drawable.ms_tune, PepoColors.TextDim,
                stringResource(R.string.pointer_settings_title), stringResource(R.string.pointer_settings_hint)
            ) { settings = true }
        }
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            val side by dev.pepotech.pepomote.service.NunchukSide.saved.collectAsState()
            HintIconButton(
                R.drawable.ms_swap_horiz, PepoColors.TextDim,
                stringResource(R.string.side_menu_title), stringResource(R.string.side_menu_hint)
            ) {
                dev.pepotech.pepomote.service.NunchukSide.save(
                    context,
                    if (side == dev.pepotech.pepomote.service.LandscapeSide.Right) dev.pepotech.pepomote.service.LandscapeSide.Left
                    else dev.pepotech.pepomote.service.LandscapeSide.Right
                )
            }
        }
        HintIconButton(
            if (locked) R.drawable.ms_lock_fill1 else R.drawable.ms_lock_open,
            if (locked) PepoColors.Warn else PepoColors.TextDim,
            stringResource(if (locked) R.string.orientation_unlock else R.string.orientation_lock),
            stringResource(R.string.orientation_hint)
        ) { dev.pepotech.pepomote.control.OrientationLock.toggle(context, rotation) }
    }
    if (settings) {
        EditorDialog({ settings = false }) {
            PointerTrackpadSettings(inPointer = true)
            TextButton(onClick = { settings = false }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Ajustes del ratón con el dedo: modo, sensibilidad e inercia. */
@Composable
internal fun PointerTrackpadSettings(inPointer: Boolean = false) {
    val context = LocalContext.current
    val mode by PointerTrackpad.mode.collectAsState()
    var sens by remember { mutableStateOf(AppPrefs.pointerTrackpadSens(context)) }
    var inertia by remember { mutableStateOf(AppPrefs.pointerTrackpadInertia(context)) }
    var gestures by remember { mutableStateOf(AppPrefs.touchGestures(context)) }
    SectionTitle(
        stringResource(if (inPointer) R.string.pointer_settings_title else R.string.pointer_trackpad_title),
        stringResource(R.string.pointer_trackpad_sub)
    )
    ChipFlow {
        for ((m, label, _) in POINTER_MODES) {
            ModeChip(stringResource(label), selected = mode == m, dense = true) { PointerTrackpad.setMode(context, m) }
        }
    }
    Text(stringResource(POINTER_MODES[mode].third), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
    if (mode != PointerTrackpad.OFF) {
        LabeledSlider(
            stringResource(R.string.ed_sensitivity), sens, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
            "%.2fx".format(sens), log = true
        ) {
            sens = it
            AppPrefs.setPointerTrackpadSens(context, it)
            PointerTrackpad.sensitivity = it
        }
        InertiaControls(inertia) {
            inertia = it
            AppPrefs.setPointerTrackpadInertia(context, it)
        }
    }
    SwitchRow(stringResource(R.string.gestures_title), gestures, stringResource(R.string.gestures_sub)) {
        gestures = it
        AppPrefs.setTouchGestures(context, it)
    }
}

/**
 * El botón del giroscopio, según cómo se active el giro:
 * - «Siempre»: un toque lo pausa y otro lo reanuda (verde = apuntando);
 * - «Alternar con el botón»: un toque lo enciende y otro lo apaga;
 * - el resto: mientras se mantiene, apunta.
 */
@Composable
internal fun GyroButton(size: Dp, gyro: GyroConfig) {
    val view = LocalView.current
    val mode = when (gyro.activation) {
        GyroActivation.ALWAYS -> 0
        GyroActivation.TOGGLE_BUTTON -> 1
        else -> 2
    }
    fun current(): Boolean = when (mode) {
        0 -> !GyroAim.paused
        1 -> GyroAim.toggled
        else -> false
    }
    var on by remember(mode) { mutableStateOf(current()) }
    val modeNow by rememberUpdatedState(mode)
    DisposableEffect(Unit) {
        onDispose { GyroAim.buttonHeld = false }
    }
    val gyroDesc = stringResource(R.string.ed_gyro_button)
    val gyroState = stringResource(if (on) R.string.diag_on else R.string.diag_off)
    Box(
        Modifier
            .size(size)
            .shadow(if (on) 1.dp else 6.dp, CircleShape)
            .background(if (on) PepoColors.Ok else PepoColors.Card, CircleShape)
            .border(2.dp, if (on) PepoColors.Ok else PepoColors.CardBorder, CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = gyroDesc
                role = androidx.compose.ui.semantics.Role.Switch
                stateDescription = gyroState
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP)
                    UiSounds.blip()
                    when (modeNow) {
                        0 -> {
                            GyroAim.paused = !GyroAim.paused
                            on = !GyroAim.paused
                            waitForUpOrCancellation()
                        }
                        1 -> {
                            GyroAim.toggled = !GyroAim.toggled
                            on = GyroAim.toggled
                            waitForUpOrCancellation()
                        }
                        else -> {
                            GyroAim.buttonHeld = true
                            on = true
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                GyroAim.buttonHeld = false
                                on = false
                                Haptics.release(view)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Ico(R.drawable.ms_my_location, if (on) PepoColors.OnAccent else PepoColors.TextDim, (size.value * 0.38f).coerceIn(14f, 34f).dp)
            Text(
                stringResource(R.string.gyro_short),
                color = if (on) PepoColors.OnAccent else PepoColors.TextDim,
                fontSize = (size.value * 0.16f).coerceIn(8f, 14f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Chips que saltan de línea cuando no caben (nunca se cortan). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** Título de apartado dentro de los diálogos. */
@Composable
internal fun SectionTitle(text: String, sub: String? = null) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (sub != null) {
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = PepoColors.TextDim)
    }
    Spacer(Modifier.height(8.dp))
}

/** Un interruptor con su título y explicación. */
@Composable
internal fun SwitchRow(title: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
        )
    }
}

@Composable
internal fun LookSettingsSection(look: LookConfig, onChange: (LookConfig) -> Unit) {
    SectionTitle(stringResource(R.string.look_title), stringResource(R.string.look_sub))
    ChipFlow {
        for ((label, zone) in listOf(
            R.string.look_off to LookZone.OFF,
            R.string.look_right to LookZone.RIGHT,
            R.string.look_all to LookZone.ALL
        )) {
            ModeChip(stringResource(label), selected = look.zone == zone) { onChange(look.copy(zone = zone)) }
        }
    }
    if (look.zone != LookZone.OFF) {
        Spacer(Modifier.height(6.dp))
        LabeledSlider(
            stringResource(R.string.ed_sensitivity), look.sensitivity, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
            "%.2fx".format(look.sensitivity), log = true
        ) { onChange(look.copy(sensitivity = it)) }
        LabeledSlider(
            stringResource(R.string.anti_deadzone_title), look.antiDeadzone, 0f..0.9f,
            "${(look.antiDeadzone * 100).roundToInt()}%",
            stringResource(R.string.anti_deadzone_sub)
        ) { onChange(look.copy(antiDeadzone = it)) }
        SwitchRow(stringResource(R.string.invert_y), look.invertY) { onChange(look.copy(invertY = it)) }
        InertiaControls(look.inertia) { onChange(look.copy(inertia = it)) }
    }
}

@Composable
internal fun GyroSettingsSection(gyro: GyroConfig, onChange: (GyroConfig) -> Unit) {
    SectionTitle(stringResource(R.string.gyro_title), stringResource(R.string.gyro_sub))
    SwitchRow(stringResource(R.string.gyro_enable), gyro.enabled) { onChange(gyro.copy(enabled = it)) }
    if (!gyro.enabled) return
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.gyro_activation), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    val acts = listOf(
        R.string.gyro_always to GyroActivation.ALWAYS,
        R.string.gyro_hold_lt to GyroActivation.HOLD_LT,
        R.string.gyro_hold_btn to GyroActivation.HOLD_BUTTON,
        R.string.gyro_toggle_btn to GyroActivation.TOGGLE_BUTTON,
        R.string.gyro_toggle_lt to GyroActivation.TOGGLE_LT
    )
    ChipFlow {
        for ((label, act) in acts) {
            ModeChip(stringResource(label), selected = gyro.activation == act) { onChange(gyro.copy(activation = act)) }
        }
    }
    if (gyro.needsButton) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.gyro_button_note), style = MaterialTheme.typography.bodySmall, color = PepoColors.Warn)
    }
    GroupTitle(stringResource(R.string.gyro_group_sens))
    Text(stringResource(R.string.gyro_landscape), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp))
    LabeledSlider(
        stringResource(R.string.gyro_sens_x), gyro.sensX, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
        "%.2fx".format(gyro.sensX), log = true
    ) { onChange(gyro.copy(sensX = it)) }
    LabeledSlider(
        stringResource(R.string.gyro_sens_y), gyro.sensY, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
        "%.2fx".format(gyro.sensY), log = true
    ) { onChange(gyro.copy(sensY = it)) }
    Text(stringResource(R.string.gyro_portrait), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
    LabeledSlider(
        stringResource(R.string.gyro_sens_x), gyro.sensXPortrait, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
        "%.2fx".format(gyro.sensXPortrait), log = true
    ) { onChange(gyro.copy(sensXPortrait = it)) }
    LabeledSlider(
        stringResource(R.string.gyro_sens_y), gyro.sensYPortrait, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
        "%.2fx".format(gyro.sensYPortrait), log = true
    ) { onChange(gyro.copy(sensYPortrait = it)) }
    GroupTitle(stringResource(R.string.gyro_group_feel))
    LabeledSlider(
        stringResource(R.string.anti_deadzone_title), gyro.antiDeadzone, 0f..0.9f,
        "${(gyro.antiDeadzone * 100).roundToInt()}%",
        stringResource(R.string.anti_deadzone_sub)
    ) { onChange(gyro.copy(antiDeadzone = it)) }
    LabeledSlider(
        stringResource(R.string.gyro_smoothing), gyro.smoothing, 0f..0.97f,
        "${(gyro.smoothing * 100).roundToInt()}%",
        stringResource(R.string.gyro_smoothing_sub)
    ) { onChange(gyro.copy(smoothing = it)) }
    GroupTitle(stringResource(R.string.gyro_group_axes))
    SwitchRow(stringResource(R.string.invert_x), gyro.invertX) { onChange(gyro.copy(invertX = it)) }
    SwitchRow(stringResource(R.string.invert_y), gyro.invertY) { onChange(gyro.copy(invertY = it)) }
    SwitchRow(stringResource(R.string.gyro_swap), gyro.swapAxes, stringResource(R.string.gyro_swap_sub)) {
        onChange(gyro.copy(swapAxes = it))
    }
}

/** Subtítulo de un grupo dentro de una sección (Sensibilidad, Respuesta, Ejes…). */
@Composable
internal fun GroupTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(color = PepoColors.Blue, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

/**
 * Precisión: cuánto frena y qué botón del mando la activa mientras se
 * mantiene (L3 para «aguantar la respiración», por ejemplo). El botón sigue
 * llegando al juego. También existe el botón «Precisión» para el diseño.
 */
@Composable
internal fun PrecisionSettingsSection(gyro: GyroConfig, onChange: (GyroConfig) -> Unit) {
    SectionTitle(stringResource(R.string.precision_title), stringResource(R.string.precision_sub))
    LabeledSlider(
        stringResource(R.string.precision_amount), gyro.precisionAmount, 0.1f..0.9f,
        "${(gyro.precisionAmount * 100).roundToInt()}%",
        stringResource(R.string.precision_amount_sub)
    ) { onChange(gyro.copy(precisionAmount = it)) }
    Text(stringResource(R.string.precision_bind), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
    Text(stringResource(R.string.precision_bind_sub), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        for ((bit, label) in listOf(
            0 to stringResource(R.string.precision_none),
            ButtonState.STICK_L to "L3",
            ButtonState.STICK_R to "R3",
            ButtonState.L to "LB",
            ButtonState.R to "RB",
            ButtonState.ZL to "LT",
            ButtonState.ZR to "RT"
        )) {
            ModeChip(label, selected = gyro.precisionBit == bit, dense = true) { onChange(gyro.copy(precisionBit = bit)) }
        }
    }
}

/** Gestos con el móvil: volante al inclinarlo y un botón al agitarlo. */
@Composable
internal fun GestureSettingsSection(gyro: GyroConfig, onChange: (GyroConfig) -> Unit) {
    SectionTitle(stringResource(R.string.gesture_title), stringResource(R.string.gesture_sub))
    SwitchRow(stringResource(R.string.gesture_tilt), gyro.tiltSteer, stringResource(R.string.gesture_tilt_sub)) {
        onChange(gyro.copy(tiltSteer = it))
    }
    if (gyro.tiltSteer) {
        LabeledSlider(
            stringResource(R.string.gesture_tilt_sens), gyro.tiltSens,
            dev.pepotech.pepomote.control.GyroAim.TILT_MIN_SENS..dev.pepotech.pepomote.control.GyroAim.TILT_MAX_SENS,
            "%.2fx".format(gyro.tiltSens)
        ) { onChange(gyro.copy(tiltSens = it)) }
        SwitchRow(stringResource(R.string.gesture_tilt_invert), gyro.tiltInvert) { onChange(gyro.copy(tiltInvert = it)) }
    }
    Text(stringResource(R.string.gesture_shake), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
    Text(stringResource(R.string.gesture_shake_sub), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        for ((bit, label) in listOf(
            0 to stringResource(R.string.precision_none),
            ButtonState.A to "A",
            ButtonState.B to "B",
            ButtonState.X to "X",
            ButtonState.Y to "Y",
            ButtonState.L to "LB",
            ButtonState.R to "RB",
            ButtonState.ZL to "LT",
            ButtonState.ZR to "RT",
            ButtonState.STICK_L to "L3",
            ButtonState.STICK_R to "R3"
        )) {
            ModeChip(label, selected = gyro.shakeBit == bit, dense = true) { onChange(gyro.copy(shakeBit = bit)) }
        }
    }
}

/** Inercia de un trackpad: interruptor y, encendida, cuánto dura. */
@Composable
internal fun InertiaControls(inertia: Float, onChange: (Float) -> Unit) {
    SwitchRow(stringResource(R.string.inertia_title), inertia > 0f, stringResource(R.string.inertia_sub)) {
        onChange(if (it) 0.6f else 0f)
    }
    if (inertia > 0f) {
        LabeledSlider(
            stringResource(R.string.inertia_amount), inertia, 0.05f..1f,
            "${(inertia * 100).roundToInt()}%"
        ) { onChange(it) }
    }
}

/** «Ajustes del mando»: cámara en el espacio libre y giroscopio, desde el propio mando. */
@Composable
internal fun PadSettingsDialog(
    look: LookConfig,
    gyro: GyroConfig,
    showGyro: Boolean,
    onLook: (LookConfig) -> Unit,
    onGyro: (GyroConfig) -> Unit,
    onDismiss: () -> Unit,
    mouse: MouseConfig? = null,
    onMouse: (MouseConfig) -> Unit = {}
) {
    // Una pestaña por tema en vez de una lista larguísima
    val tabs = buildList {
        add(0 to R.string.pad_tab_camera)
        if (showGyro) add(1 to R.string.pad_tab_gyro)
        if (showGyro) add(2 to R.string.pad_tab_precision)
        if (showGyro) add(4 to R.string.pad_tab_gestures)
        if (mouse != null) add(3 to R.string.pad_tab_mouse)
    }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    EditorDialog(onDismiss) {
        Text(stringResource(R.string.ed_pad_settings), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            for ((id, label) in tabs) ModeChip(stringResource(label), selected = tab == id, dense = true) { tab = id }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            1 -> GyroSettingsSection(gyro, onGyro)
            2 -> PrecisionSettingsSection(gyro, onGyro)
            4 -> GestureSettingsSection(gyro, onGyro)
            3 -> if (mouse != null) MouseSettingsSection(mouse, onMouse)
            else -> LookSettingsSection(look, onLook)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Modo ratón del mando universal: el giro como ratón, los touchpads y la inercia. */
@Composable
internal fun MouseSettingsSection(mouse: MouseConfig, onChange: (MouseConfig) -> Unit) {
    SectionTitle(stringResource(R.string.mouse_settings_title), stringResource(R.string.mouse_settings_sub))
    SwitchRow(stringResource(R.string.mouse_gyro), mouse.gyro, stringResource(R.string.mouse_gyro_sub)) {
        onChange(mouse.copy(gyro = it))
    }
    if (mouse.gyro) {
        LabeledSlider(
            stringResource(R.string.mouse_gyro_sens), mouse.gyroSens, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
            "%.2fx".format(mouse.gyroSens), log = true
        ) { onChange(mouse.copy(gyroSens = it)) }
        LabeledSlider(
            stringResource(R.string.gyro_smoothing), mouse.smoothing, 0f..0.97f,
            "${(mouse.smoothing * 100).roundToInt()}%"
        ) { onChange(mouse.copy(smoothing = it)) }
        SwitchRow(stringResource(R.string.invert_y), mouse.invertY) { onChange(mouse.copy(invertY = it)) }
    }
    LabeledSlider(
        stringResource(R.string.mouse_pad_sens), mouse.padSens, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
        "%.2fx".format(mouse.padSens), log = true
    ) { onChange(mouse.copy(padSens = it)) }
    InertiaControls(mouse.inertia) { onChange(mouse.copy(inertia = it)) }
}

/**
 * Touchpad del mando universal. En modo ratón es el trackpad de un portátil
 * completo ([mouseGestures]: clics, doble toque, agarrar, rueda, zoom y
 * gestos de tres dedos). En modo mando hace de trackpad de cámara (stick
 * derecho). Con inercia opcional al soltar.
 */
@Composable
internal fun MousePad(
    w: Dp,
    h: Dp,
    mouseMode: Boolean,
    sensitivity: Float,
    antiDeadzone: Float,
    inertia: Float
) {
    val context = LocalContext.current
    var touching by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val padDesc = stringResource(if (mouseMode) R.string.mousepad_label_mouse else R.string.mousepad_label_pad) +
        ". " + stringResource(R.string.mousepad_help)
    val gestures = remember { AppPrefs.touchGestures(context) }
    DisposableEffect(mouseMode) {
        onDispose {
            Kbm.setTouch(Kbm.SRC_PAD, 0f, 0f)
            ButtonState.setStick2From(ButtonState.SRC_LOOK, 0, 0)
        }
    }
    val base = Modifier
        .size(w, h)
        .background(PepoColors.Card, shape)
        .border(2.dp, if (touching) PepoColors.Glow else PepoColors.CardBorder, shape)
        .semantics(mergeDescendants = true) { contentDescription = padDesc }
    key(mouseMode) {
        Box(
            if (mouseMode) {
                base.mouseGestures(
                    dev.pepotech.pepomote.ui.components.KbmOut(Kbm.SRC_PAD),
                    sensitivity, inertia, gestures, onTouching = { touching = it }
                )
            } else {
                base.trackpadInput(
                    sensitivity = sensitivity,
                    antiDeadzone = antiDeadzone,
                    inertia = inertia,
                    onTouch = { touching = it != null }
                ) { x, y -> ButtonState.setStick2From(ButtonState.SRC_LOOK, x, y) }
            },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Ico(
                    if (mouseMode) R.drawable.ms_touchpad_mouse else R.drawable.ms_drag_pan,
                    if (touching) PepoColors.Blue else PepoColors.TextDim,
                    (minOf(w.value, h.value) * 0.22f).coerceIn(18f, 40f).dp
                )
            }
        }
    }
}

/** Un botón redondo con icono y rótulo (clics, teclado, mando/ratón). */
@Composable
internal fun IconPadButton(
    size: Dp,
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    /** false = solo el icono (el rótulo queda para TalkBack). */
    showLabel: Boolean = true,
    onPress: (Boolean) -> Unit
) {
    val view = LocalView.current
    var down by remember { mutableStateOf(false) }
    val on = down || highlighted
    Box(
        Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(if (down) 1.dp else 6.dp, CircleShape)
            .background(if (on) PepoColors.Blue else PepoColors.Card, CircleShape)
            .border(1.5.dp, if (on) PepoColors.Blue else PepoColors.CardBorder, CircleShape)
            // TalkBack: se lee el rótulo y el doble toque pulsa y suelta
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                contentDescription = label
                if (!enabled) disabled()
                onClick {
                    onPress(true)
                    onPress(false)
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val d = awaitFirstDown()
                    d.consume()
                    down = true
                    Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP)
                    onPress(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        down = false
                        onPress(false)
                        Haptics.release(view)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Ico(icon, if (on) PepoColors.OnAccent else PepoColors.Text, (size.value * (if (showLabel) 0.36f else 0.5f)).coerceIn(14f, 40f).dp)
            if (showLabel) Text(
                label,
                color = if (on) PepoColors.OnAccent else PepoColors.TextDim,
                fontSize = (size.value * 0.13f).coerceIn(8f, 13f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Una pista que sale la primera vez (y hasta que se toca «Entendido»): para
 * que se descubra lo que no se ve (gestos del touchpad, selector del puntero).
 */
@Composable
internal fun OneTimeHint(id: String, text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var show by remember(id) { mutableStateOf(!AppPrefs.hintSeen(context, id)) }
    if (!show) return
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Row(
        modifier
            .padding(12.dp)
            .widthIn(max = 520.dp)
            .shadow(8.dp, shape)
            .background(PepoColors.Card, shape)
            .border(1.5.dp, PepoColors.Blue, shape)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(R.drawable.ms_info, PepoColors.Blue, 22.dp)
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = PepoColors.Text), modifier = Modifier.weight(1f))
        TextButton(onClick = {
            show = false
            AppPrefs.setHintSeen(context, id)
        }) { Text(stringResource(R.string.hint_ok), color = PepoColors.Blue, fontWeight = FontWeight.Bold) }
    }
}
