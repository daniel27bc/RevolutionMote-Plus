package dev.pepotech.pepomote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.view.HapticFeedbackConstants
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.AddOption
import dev.pepotech.pepomote.control.ButtonState
import dev.pepotech.pepomote.control.ElementKind
import dev.pepotech.pepomote.control.Haptics
import dev.pepotech.pepomote.control.PadElement
import dev.pepotech.pepomote.control.PadProfile
import dev.pepotech.pepomote.control.PadProfiles
import dev.pepotech.pepomote.control.RetroLayout
import dev.pepotech.pepomote.control.StickMap
import dev.pepotech.pepomote.control.TrackpadEngine
import dev.pepotech.pepomote.control.UiSounds
import dev.pepotech.pepomote.ui.components.AnalogStick
import dev.pepotech.pepomote.ui.components.FaceCluster
import dev.pepotech.pepomote.ui.components.FitText
import dev.pepotech.pepomote.ui.components.PadCross
import dev.pepotech.pepomote.ui.components.RightStickTrackpad
import dev.pepotech.pepomote.ui.components.RoundButton
import dev.pepotech.pepomote.ui.components.ShoulderButton
import dev.pepotech.pepomote.ui.components.retroLabel
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ancho de una pastilla (gatillo/hombro) respecto a su alto. */
private const val PILL_RATIO = 2.3f

/** Bits del rombo de la plantilla que se ve ahora. */
internal fun RetroLayout.faceBits(): List<Int> = face.map { it.bit }

/** El nombre de un botón según la plantilla (LB en Xbox, L en Nintendo…). */
@Composable
internal fun buttonLabel(bit: Int, layout: RetroLayout): String {
    layout.face.firstOrNull { it.bit == bit }?.let { return retroLabel(it.label) }
    layout.center.firstOrNull { it.bit == bit }?.let { return retroLabel(it.label) }
    val s = layout.shoulders
    return when (bit) {
        ButtonState.A -> "A"
        ButtonState.B -> "B"
        ButtonState.X -> "X"
        ButtonState.Y -> "Y"
        ButtonState.L -> s.l ?: "L"
        ButtonState.R -> s.r ?: "R"
        ButtonState.ZL -> s.l2 ?: "ZL"
        ButtonState.ZR -> s.r2 ?: "ZR"
        ButtonState.STICK_L -> "L3"
        ButtonState.STICK_R -> "R3"
        ButtonState.MINUS -> "−"
        ButtonState.PLUS -> "+"
        ButtonState.HOME -> stringResource(R.string.home_btn)
        ButtonState.SCREEN -> stringResource(R.string.capture)
        else -> "?"
    }
}

/** Nombre largo de un control para el ⚙ y la lista «Añadir». */
@Composable
internal fun elementTitle(kind: ElementKind, bit: Int, layout: RetroLayout): String = when (kind) {
    ElementKind.STICK_L -> stringResource(R.string.ed_left_stick)
    ElementKind.STICK_R -> stringResource(R.string.ed_right_stick)
    ElementKind.DPAD -> stringResource(R.string.ed_dpad)
    ElementKind.FACE -> stringResource(R.string.ed_face)
    ElementKind.GYRO -> stringResource(R.string.ed_gyro_button)
    ElementKind.BUTTON -> stringResource(R.string.ed_button, buttonLabel(bit, layout))
}

/**
 * Los controles del perfil, cada uno donde se dejó. Fuera de edición son
 * controles de verdad; en edición cada uno lleva un marco que se arrastra
 * (y se pellizca para cambiar el tamaño) y su ⚙.
 */
@Composable
internal fun CustomPadCanvas(
    profile: PadProfile,
    layout: RetroLayout,
    editing: Boolean,
    onElementChange: (old: PadElement, new: PadElement) -> Unit,
    onGear: (PadElement) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        // Cámara en el espacio libre: debajo de todo, así solo coge los
        // dedos que no caen en ningún control
        if (!editing) LookSurface(profile.look, Modifier.matchParentSize())
        for (e in profile.elements) key(e.id) {
            val (bw, bh) = elementBox(e, h)
            val ox = w * e.x - bw / 2
            val oy = h * e.y - bh / 2
            Box(
                Modifier
                    .offset(ox, oy)
                    .size(bw, bh)
                    .alpha(if (editing) maxOf(profile.opacity, 0.55f) else profile.opacity),
                contentAlignment = Alignment.Center
            ) {
                ElementControl(e, layout, profile.gyro, bw, bh)
            }
            if (editing) {
                EditFrame(
                    element = e,
                    boxW = bw,
                    boxH = bh,
                    areaW = w,
                    areaH = h,
                    onChange = onElementChange,
                    onGear = { onGear(e) }
                )
            }
        }
    }
}

private fun elementBox(e: PadElement, areaH: Dp): Pair<Dp, Dp> {
    val s = areaH * e.size
    return if (e.kind == ElementKind.BUTTON && e.pill) (s * PILL_RATIO) to s else s to s
}

@Composable
private fun ElementControl(e: PadElement, layout: RetroLayout, gyro: GyroConfig, bw: Dp, bh: Dp) {
    when (e.kind) {
        ElementKind.STICK_L, ElementKind.STICK_R -> {
            val sink: (Int, Int) -> Unit = if (e.kind == ElementKind.STICK_L) {
                { x, y -> ButtonState.setStick(x, y) }
            } else {
                { x, y -> ButtonState.setStick2(x, y) }
            }
            // Al quitar el stick o cambiar de tipo, nada se queda inclinado
            DisposableEffect(e.kind, e.trackpad) { onDispose { sink(0, 0) } }
            if (e.trackpad) {
                RightStickTrackpad(bw, e.sensitivity, e.antiDeadzone, sink)
            } else {
                AnalogStick(bw, e.deadzone, e.antiDeadzone, e.sensitivity, sink)
            }
        }
        ElementKind.DPAD -> PadCross(sizeDp = bw)
        ElementKind.FACE -> FaceCluster(layout, bw, bh)
        ElementKind.GYRO -> GyroButton(bw, gyro)
        ElementKind.BUTTON -> {
            val label = buttonLabel(e.bit, layout)
            val text = (bh.value * 0.36f).roundToInt().coerceIn(9, 28)
            when {
                e.turbo -> TurboButton(
                    label, e.bit, bw, bh,
                    if (e.pill) RoundedCornerShape(bh / 2.5f) else CircleShape,
                    text
                )
                e.pill -> ShoulderButton(label, e.bit, bw, bh, textSize = text)
                else -> RoundButton(label, bw, e.bit, textSize = text, pop = e.bit == ButtonState.A)
            }
        }
    }
}

/**
 * Botón con turbo: mientras se mantiene, pulsa y suelta solo unas 8 veces
 * por segundo (cada flanco llega al receptor: la retención de PressLatch
 * guarda los toques cortos). Al levantar el dedo queda suelto.
 */
@Composable
private fun TurboButton(label: String, bit: Int, w: Dp, h: Dp, shape: Shape, textSize: Int) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var down by remember { mutableStateOf(false) }
    var pulse by remember { mutableStateOf(false) }
    DisposableEffect(bit) { onDispose { ButtonState.set(bit, false) } }
    Box(
        Modifier
            .size(w, h)
            .shadow(if (down) 1.dp else 5.dp, shape)
            .background(if (pulse) PepoColors.Glow else if (down) PepoColors.BlueHover else PepoColors.Card, shape)
            .border(1.5.dp, PepoColors.Warn, shape)
            .pointerInput(bit) {
                awaitEachGesture {
                    val start = awaitFirstDown()
                    start.consume()
                    down = true
                    Haptics.tap(view, HapticFeedbackConstants.KEYBOARD_TAP)
                    UiSounds.blip()
                    val job: Job = scope.launch {
                        while (true) {
                            pulse = true
                            ButtonState.set(bit, true)
                            delay(TURBO_HALF_MS)
                            pulse = false
                            ButtonState.set(bit, false)
                            delay(TURBO_HALF_MS)
                        }
                    }
                    try {
                        drag(start.id) { it.consume() }
                    } finally {
                        job.cancel()
                        pulse = false
                        down = false
                        ButtonState.set(bit, false)
                        Haptics.release(view)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        FitText(
            label,
            style = MaterialTheme.typography.titleLarge.copy(color = PepoColors.Text, fontWeight = FontWeight.Bold),
            maxSize = textSize.sp,
            modifier = Modifier.padding(horizontal = 3.dp)
        )
        Text(
            "T",
            color = PepoColors.Warn,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 2.dp)
        )
    }
}

private const val TURBO_HALF_MS = 60L

/** El marco de edición de un control: arrastrar, pellizcar y su ⚙. */
@Composable
private fun EditFrame(
    element: PadElement,
    boxW: Dp,
    boxH: Dp,
    areaW: Dp,
    areaH: Dp,
    onChange: (PadElement, PadElement) -> Unit,
    onGear: () -> Unit
) {
    val latest by rememberUpdatedState(element)
    val change by rememberUpdatedState(onChange)
    val shape = if (element.kind == ElementKind.BUTTON && !element.pill || element.kind == ElementKind.GYRO || element.isStick && !element.trackpad) {
        CircleShape
    } else {
        RoundedCornerShape(12.dp)
    }
    val ox = areaW * element.x - boxW / 2
    val oy = areaH * element.y - boxH / 2
    Box(
        Modifier
            .offset(ox, oy)
            .size(boxW, boxH)
            .border(2.dp, PepoColors.Glow, shape)
            .background(PepoColors.Glow.copy(alpha = 0.10f), shape)
            .pointerInput(element.id) {
                val wPx = areaW.toPx()
                val hPx = areaH.toPx()
                detectTransformGestures { _, pan, zoom, _ ->
                    val cur = latest
                    val next = PadProfiles.clampElement(
                        cur.copy(
                            x = cur.x + pan.x / wPx,
                            y = cur.y + pan.y / hPx,
                            size = cur.size * zoom
                        )
                    )
                    change(cur, next)
                }
            }
    )
    // El ⚙, encima del marco (se queda el toque antes que el arrastre)
    val gear = 30.dp
    Box(
        Modifier
            .offset(ox + boxW - gear * 0.7f, oy - gear * 0.3f)
            .size(gear)
            .shadow(4.dp, CircleShape)
            .background(PepoColors.Blue, CircleShape)
            .clickable { onGear() },
        contentAlignment = Alignment.Center
    ) {
        Text("⚙", color = PepoColors.OnAccent, fontSize = 16.sp)
    }
}

/** Barra del modo edición, arriba: añadir, perfiles, opacidad, restablecer, cancelar y guardar. */
@Composable
internal fun EditorToolbar(
    profile: PadProfile,
    onAdd: () -> Unit,
    onProfiles: () -> Unit,
    onPadSettings: () -> Unit,
    onOpacity: (Float) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp),
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.Glow)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = onAdd) { Text("＋ " + stringResource(R.string.ed_add), color = PepoColors.Blue) }
            TextButton(onClick = onProfiles) {
                Text("▤ " + profile.name, color = PepoColors.Text, maxLines = 1)
            }
            TextButton(onClick = onPadSettings) { Text("🎯 " + stringResource(R.string.ed_pad_settings), color = PepoColors.Blue) }
            Text(stringResource(R.string.ed_opacity), style = MaterialTheme.typography.labelMedium, color = PepoColors.TextDim)
            Slider(
                value = profile.opacity,
                onValueChange = onOpacity,
                valueRange = PadProfiles.MIN_OPACITY..1f,
                modifier = Modifier.width(110.dp),
                colors = sliderColors()
            )
            Text("${(profile.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onReset) { Text(stringResource(R.string.layout_reset), color = PepoColors.Error) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.ed_cancel), color = PepoColors.TextDim) }
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.layout_save), color = PepoColors.Glow, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun sliderColors() = SliderDefaults.colors(
    thumbColor = PepoColors.Blue,
    activeTrackColor = PepoColors.Blue,
    inactiveTrackColor = PepoColors.CardBorder
)

/** Solo para las capturas de los tests: los diálogos se pintan en línea. */
internal val LocalInlineDialogs = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Tarjeta flotante de los diálogos del editor. */
@Composable
internal fun EditorDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Capturas de los tests: el diálogo pintado en la misma ventana
    if (LocalInlineDialogs.current) {
        Card(
            colors = CardDefaults.cardColors(containerColor = PepoColors.Background),
            border = BorderStroke(1.5.dp, PepoColors.CardBorder)
        ) { Column(Modifier.padding(18.dp)) { content() } }
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.widthIn(max = 520.dp).heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = PepoColors.Background),
            border = BorderStroke(1.5.dp, PepoColors.CardBorder)
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) { content() }
        }
    }
}

/** Un slider con su título y el valor a la derecha. */
@Composable
internal fun LabeledSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    shown: String,
    subtitle: String? = null,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(shown, style = MaterialTheme.typography.titleSmall, color = PepoColors.Blue)
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, colors = sliderColors())
    }
}

/** El ⚙ de un control: tamaño, ajustes de stick o de botón, y quitarlo. */
@Composable
internal fun ElementSettingsDialog(
    element: PadElement,
    profile: PadProfile,
    layout: RetroLayout,
    onChange: (PadElement) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    EditorDialog(onDismiss) {
        Text(
            elementTitle(element.kind, element.bit, layout),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            stringResource(R.string.ed_size),
            element.size,
            PadProfiles.MIN_SIZE..PadProfiles.MAX_SIZE,
            "${(element.size * 100).roundToInt()}%"
        ) { onChange(element.copy(size = it)) }

        if (element.isStick) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.ed_stick_type), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(stringResource(R.string.mode_stick), selected = !element.trackpad) {
                    onChange(element.copy(trackpad = false))
                }
                ModeChip(stringResource(R.string.mode_trackpad), selected = element.trackpad) {
                    onChange(element.copy(trackpad = true))
                }
            }
            Spacer(Modifier.height(10.dp))
            StickResponsePreview(element)
            if (!element.trackpad) {
                LabeledSlider(
                    stringResource(R.string.ed_deadzone),
                    element.deadzone, 0f..0.5f,
                    "${(element.deadzone * 100).roundToInt()}%",
                    stringResource(R.string.ed_deadzone_sub)
                ) { onChange(element.copy(deadzone = it)) }
            }
            LabeledSlider(
                stringResource(R.string.anti_deadzone_title),
                element.antiDeadzone, 0f..0.6f,
                "${(element.antiDeadzone * 100).roundToInt()}%",
                stringResource(R.string.anti_deadzone_sub)
            ) { onChange(element.copy(antiDeadzone = it)) }
            LabeledSlider(
                stringResource(R.string.sensitivity_title),
                element.sensitivity, 0.2f..3f,
                "%.1fx".format(element.sensitivity)
            ) { onChange(element.copy(sensitivity = it)) }
        }

        if (element.kind == ElementKind.BUTTON) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ed_function), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val targets = PadProfiles.remapTargets(profile, element, layout.faceBits())
            FlowChips(targets.map { buttonLabel(it, layout) to it }, element.bit) { bit ->
                onChange(element.copy(bit = bit, pill = PadProfiles.isPillBit(bit)))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ed_shape_pill), style = MaterialTheme.typography.titleSmall)
                }
                Switch(
                    checked = element.pill,
                    onCheckedChange = { onChange(element.copy(pill = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Blue)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ed_turbo), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ed_turbo_sub), style = MaterialTheme.typography.bodySmall, color = PepoColors.TextDim)
                }
                Switch(
                    checked = element.turbo,
                    onCheckedChange = { onChange(element.copy(turbo = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = PepoColors.Warn)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onRemove) { Text("🗑 " + stringResource(R.string.ed_remove), color = PepoColors.Error) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Chips en varias filas (la lista de funciones de un botón). */
@Composable
internal fun FlowChips(options: List<Pair<String, Int>>, selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in options.chunked(5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((label, bit) in row) {
                    ModeChip(label, selected = bit == selected, dense = true) { onPick(bit) }
                }
            }
        }
    }
}

/**
 * Vista previa del stick: a la izquierda, el círculo con la zona muerta en
 * rojo y el anillo donde empieza la salida; a la derecha, la curva de
 * respuesta real (se calcula con StickMap / TrackpadEngine, lo mismo que se
 * manda al PC): cuánto sale según cuánto mueves el dedo.
 */
@Composable
private fun StickResponsePreview(e: PadElement) {
    val border = PepoColors.CardBorder
    val blue = PepoColors.Blue
    val glow = PepoColors.Glow
    val dead = PepoColors.Error.copy(alpha = 0.35f)
    val dim = PepoColors.TextDim
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(104.dp)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(border, r, c, style = Stroke(3f))
            if (!e.trackpad) {
                drawCircle(dead, r * e.deadzone, c)
                drawCircle(glow, r * e.deadzone, c, style = Stroke(2f))
                // Hasta dónde hay que llevar el dedo para el tope
                val full = (e.deadzone + (1f - e.deadzone) / e.sensitivity).coerceAtMost(1f)
                drawCircle(blue, r * full, c, style = Stroke(2.5f))
            } else {
                drawCircle(blue.copy(alpha = 0.15f), r, c)
            }
            drawCircle(blue, 6f, c)
        }
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                val pad = 6f
                val gw = size.width - pad * 2
                val gh = size.height - pad * 2
                drawLine(border, Offset(pad, pad + gh), Offset(pad + gw, pad + gh), 2f)
                drawLine(border, Offset(pad, pad), Offset(pad, pad + gh), 2f)
                val path = Path()
                val steps = 60
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val out = if (e.trackpad) {
                        TrackpadEngine.fromVelocity(t * TrackpadEngine.FULL_SPEED * 1.5f, 0f, e.sensitivity, e.antiDeadzone).first
                    } else {
                        StickMap.map(t * 100f, 0f, 100f, e.deadzone, e.antiDeadzone, e.sensitivity).first
                    } / 127f
                    val px = pad + gw * t
                    val py = pad + gh * (1f - out)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, glow, style = Stroke(4f))
            }
            Text(
                stringResource(if (e.trackpad) R.string.ed_curve_trackpad else R.string.ed_curve_stick),
                style = MaterialTheme.typography.bodySmall,
                color = dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** «＋ Añadir»: lo que falta en el diseño, con un toque se pone en el centro. */
@Composable
internal fun AddControlDialog(
    options: List<AddOption>,
    layout: RetroLayout,
    onPick: (AddOption) -> Unit,
    onDismiss: () -> Unit
) {
    EditorDialog(onDismiss) {
        Text(stringResource(R.string.ed_add_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        if (options.isEmpty()) {
            Text(stringResource(R.string.ed_add_none), style = MaterialTheme.typography.bodyMedium, color = PepoColors.TextDim)
        }
        for (o in options) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(o) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("＋", color = PepoColors.Blue, fontSize = 18.sp, modifier = Modifier.width(28.dp))
                Text(elementTitle(o.kind, o.bit, layout), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.ed_done), color = PepoColors.Blue)
        }
    }
}

/**
 * Perfiles: elegir cuál se usa (o el diseño original), crear uno nuevo,
 * duplicar, renombrar y borrar. [editing]: dentro del editor, elegir un
 * perfil lo carga para editarlo; fuera, solo lo activa.
 */
@Composable
internal fun ProfilesDialog(
    profiles: List<PadProfile>,
    current: String?,
    allowOriginal: Boolean,
    onPick: (String?) -> Unit,
    onNew: () -> Unit,
    onDuplicate: (PadProfile) -> Unit,
    onRename: (PadProfile, String) -> Unit,
    onDelete: (PadProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var renaming by remember { mutableStateOf<PadProfile?>(null) }
    var newName by remember { mutableStateOf("") }
    EditorDialog(onDismiss) {
        Text(stringResource(R.string.ed_profiles), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        if (allowOriginal) {
            ProfileRow(stringResource(R.string.ed_original), selected = current == null, onClick = { onPick(null) })
        }
        for (p in profiles) {
            ProfileRow(p.name, selected = current == p.name, onClick = { onPick(p.name) }) {
                TextButton(onClick = { renaming = p; newName = p.name }) { Text("✎", color = PepoColors.Blue) }
                TextButton(onClick = { onDuplicate(p) }) { Text("⧉", color = PepoColors.Blue) }
                TextButton(onClick = { onDelete(p) }) { Text("🗑", color = PepoColors.Error) }
            }
        }
        val r = renaming
        if (r != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(32) },
                singleLine = true,
                label = { Text(stringResource(R.string.ed_rename)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.ed_cancel), color = PepoColors.TextDim) }
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onRename(r, newName.trim())
                        renaming = null
                    }
                ) { Text(stringResource(R.string.ed_done), color = PepoColors.Blue) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onNew) { Text("＋ " + stringResource(R.string.ed_new_profile), color = PepoColors.Blue) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ProfileRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (selected) PepoColors.Blue.copy(alpha = 0.15f) else PepoColors.Card, shape)
            .border(1.5.dp, if (selected) PepoColors.Blue else PepoColors.CardBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (selected) "●" else "○", color = PepoColors.Blue, modifier = Modifier.width(22.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

/** Una pastilla de la barra del mando: legible encima de los controles. */
@Composable
private fun BarChip(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .shadow(3.dp, shape)
            .background(PepoColors.Card, shape)
            .border(1.5.dp, PepoColors.CardBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * La barra de abajo fuera de edición: candado, perfil activo (cambiarlo),
 * giroscopio sí/no ([gyroOn] null = este mando no lo usa), ajustes del mando
 * y «Editar diseño».
 */
@Composable
internal fun ProfileBar(
    activeName: String?,
    gyroOn: Boolean?,
    onGyroToggle: () -> Unit,
    onPadSettings: () -> Unit,
    onProfiles: () -> Unit,
    onEdit: () -> Unit,
    lock: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lock()
        BarChip("▤ " + (activeName ?: stringResource(R.string.ed_original)), PepoColors.Text, onProfiles)
        if (gyroOn != null) {
            BarChip(
                "🎯 " + stringResource(if (gyroOn) R.string.gyro_on else R.string.gyro_off),
                if (gyroOn) PepoColors.Ok else PepoColors.TextDim,
                onGyroToggle
            )
        }
        BarChip("⚙ " + stringResource(R.string.ed_pad_settings), PepoColors.Blue, onPadSettings)
        BarChip("✎ " + stringResource(R.string.layout_edit_btn), PepoColors.Blue, onEdit)
    }
}
