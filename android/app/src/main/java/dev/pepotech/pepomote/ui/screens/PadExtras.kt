package dev.pepotech.pepomote.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.pepotech.pepomote.control.Haptics
import dev.pepotech.pepomote.control.LookConfig
import dev.pepotech.pepomote.control.LookZone
import dev.pepotech.pepomote.control.PadProfiles
import dev.pepotech.pepomote.control.PressHit
import dev.pepotech.pepomote.control.PressMode
import dev.pepotech.pepomote.control.UiSounds
import dev.pepotech.pepomote.ui.components.LocalPressRegistry
import dev.pepotech.pepomote.ui.components.trackpadInput
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
 * Trackpad de ratón (modo puntero): una superficie invisible debajo de los
 * controles; el dedo en un hueco mueve el cursor del PC ([PointerTrackpad]).
 * Mientras está puesta, mover el móvil no mueve el cursor.
 */
@Composable
internal fun PointerTrackpadLayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inertia = remember { AppPrefs.pointerTrackpadInertia(context) }
    DisposableEffect(Unit) {
        PointerTrackpad.sensitivity = AppPrefs.pointerTrackpadSens(context)
        PointerTrackpad.active = true
        onDispose { PointerTrackpad.active = false }
    }
    Box(
        modifier.trackpadInput(
            sensitivity = 1f,
            antiDeadzone = 0f,
            inertia = inertia,
            onVelocity = { vx, vy -> PointerTrackpad.setVelocity(vx, vy) }
        ) { _, _ -> }
    )
}

/** «🖱 Trackpad»: mover el ratón con el dedo, sí o no. */
@Composable
internal fun PointerTrackpadChip(on: Boolean, onChange: (Boolean) -> Unit) {
    ModeChip("🖱 " + stringResource(R.string.pointer_trackpad_chip), selected = on, toggle = true, compact = true) {
        onChange(!on)
    }
}

/** Ajustes del ratón con el dedo: interruptor, sensibilidad e inercia. */
@Composable
internal fun PointerTrackpadSettings() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AppPrefs.pointerTrackpad(context)) }
    var sens by remember { mutableStateOf(AppPrefs.pointerTrackpadSens(context)) }
    var inertia by remember { mutableStateOf(AppPrefs.pointerTrackpadInertia(context)) }
    SectionTitle(stringResource(R.string.pointer_trackpad_title), stringResource(R.string.pointer_trackpad_sub))
    SwitchRow(stringResource(R.string.pointer_trackpad_enable), on) {
        on = it
        AppPrefs.setPointerTrackpad(context, it)
    }
    if (on) {
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
}

/** El botón 🎯: mantenerlo apunta con el giro, o un toque lo enciende y lo apaga. */
@Composable
internal fun GyroButton(size: Dp, gyro: GyroConfig) {
    val view = LocalView.current
    val toggle = gyro.activation == GyroActivation.TOGGLE_BUTTON
    var on by remember { mutableStateOf(if (toggle) GyroAim.toggled else false) }
    val isToggle by rememberUpdatedState(toggle)
    DisposableEffect(Unit) {
        onDispose { GyroAim.buttonHeld = false }
    }
    Box(
        Modifier
            .size(size)
            .shadow(if (on) 1.dp else 6.dp, CircleShape)
            .background(if (on) PepoColors.Ok else PepoColors.Card, CircleShape)
            .border(2.dp, if (on) PepoColors.Ok else PepoColors.Blue, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP)
                    UiSounds.blip()
                    if (isToggle) {
                        GyroAim.toggled = !GyroAim.toggled
                        on = GyroAim.toggled
                        waitForUpOrCancellation()
                    } else {
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
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎯", fontSize = (size.value * 0.34f).coerceIn(12f, 30f).sp)
            Text(
                stringResource(R.string.gyro_short),
                color = if (on) PepoColors.OnAccent else PepoColors.Text,
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
    Spacer(Modifier.height(6.dp))
    LabeledSlider(
        stringResource(R.string.gyro_sens_x), gyro.sensX, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
        "%.2fx".format(gyro.sensX), log = true
    ) { onChange(gyro.copy(sensX = it)) }
    LabeledSlider(
        stringResource(R.string.gyro_sens_y), gyro.sensY, PadProfiles.MIN_SENS..PadProfiles.MAX_SENS,
        "%.2fx".format(gyro.sensY), log = true
    ) { onChange(gyro.copy(sensY = it)) }
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
    SwitchRow(stringResource(R.string.invert_x), gyro.invertX) { onChange(gyro.copy(invertX = it)) }
    SwitchRow(stringResource(R.string.invert_y), gyro.invertY) { onChange(gyro.copy(invertY = it)) }
    SwitchRow(stringResource(R.string.gyro_swap), gyro.swapAxes, stringResource(R.string.gyro_swap_sub)) {
        onChange(gyro.copy(swapAxes = it))
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
    onDismiss: () -> Unit
) {
    EditorDialog(onDismiss) {
        LookSettingsSection(look, onLook)
        if (showGyro) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = PepoColors.CardBorder)
            Spacer(Modifier.height(12.dp))
            GyroSettingsSection(gyro, onGyro)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
