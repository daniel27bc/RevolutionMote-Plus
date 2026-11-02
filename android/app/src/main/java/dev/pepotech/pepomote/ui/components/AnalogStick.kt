package dev.pepotech.pepomote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.control.AppPrefs
import dev.pepotech.pepomote.control.Haptics
import dev.pepotech.pepomote.control.StickMap
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlin.math.roundToInt

/**
 * Stick analógico virtual del Nunchuk. El pomo sigue al pulgar dentro del
 * círculo (posición respecto al centro: tocar el borde = tope) y al soltar
 * vuelve al centro con un muelle. Salida −127..127, +y arriba, zona muerta
 * pequeña (StickMap); tic háptico al salir del centro.
 */
@Composable
fun AnalogStick(
    sizeDp: Dp,
    deadzone: Float = StickMap.DEAD_ZONE,
    antiDeadzone: Float = 0f,
    sensitivity: Float = 1f,
    /**
     * Flotante (como en los juegos de móvil): toda la caja es zona del
     * stick y su centro aparece donde cae el pulgar, sin tener que buscarlo.
     * Apagado: el stick fijo de siempre.
     */
    floating: Boolean = AppPrefs.floatingSticksDefault,
    onStick: (x: Int, y: Int) -> Unit
) {
    val view = LocalView.current
    val dz by rememberUpdatedState(deadzone)
    val adz by rememberUpdatedState(antiDeadzone)
    val sens by rememberUpdatedState(sensitivity)
    val float by rememberUpdatedState(floating)
    // Flotante: la base es más pequeña que la zona, para poder moverse dentro
    val baseDp = if (floating) sizeDp * 0.68f else sizeDp
    val knobDp = baseDp * 0.46f
    var active by remember { mutableStateOf(false) }
    var knob by remember { mutableStateOf(Offset.Zero) } // desplazamiento del pomo, px
    var origin by remember { mutableStateOf(Offset.Zero) } // desplazamiento de la base (flotante), px
    val baseShown by animateOffsetAsState(
        targetValue = if (active) origin else Offset.Zero,
        animationSpec = if (active) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "base"
    )
    // Bajo el dedo va directo (sin retraso); al soltar, muelle al centro
    val shown by animateOffsetAsState(
        targetValue = if (active) knob else Offset.Zero,
        animationSpec = if (active) snap() else spring(stiffness = Spring.StiffnessMedium),
        label = "knob"
    )

    Box(
        modifier = Modifier
            .size(sizeDp)
            .then(
                if (floating) Modifier.border(1.dp, PepoColors.CardBorder.copy(alpha = 0.5f), CircleShape)
                else Modifier
            )
            .pointerInput(sizeDp, floating) {
                var centered = true
                awaitEachGesture {
                    // Geometría por gesto: siempre la del layout actual
                    val boxCenter = Offset(size.width / 2f, size.height / 2f)
                    var center = boxCenter
                    val travel = (baseDp.toPx() - knobDp.toPx()) / 2f // recorrido del pomo
                    fun move(pos: Offset) {
                        val d = pos - center
                        val len = d.getDistance()
                        knob = if (len > travel) d * (travel / len) else d
                        val (x, y) = StickMap.map(d.x, d.y, travel, dz, adz, sens)
                        val atCenter = x == 0 && y == 0
                        if (centered && !atCenter) {
                            Haptics.tap(view, HapticFeedbackConstants.CLOCK_TICK, Haptics.Source.STICK)
                        }
                        centered = atCenter
                        onStick(x, y)
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    if (float) {
                        // El centro, donde ha caído el pulgar
                        center = down.position
                        origin = down.position - boxCenter
                    }
                    active = true
                    move(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        move(change.position)
                    }
                    active = false
                    knob = Offset.Zero
                    centered = true
                    onStick(0, 0)
                }
            },
        contentAlignment = Alignment.Center
    ) {
      Box(
        Modifier
            .offset { IntOffset(baseShown.x.roundToInt(), baseShown.y.roundToInt()) }
            .size(baseDp)
            .background(PepoColors.Card, CircleShape)
            .border(1.5.dp, if (active) PepoColors.Glow else PepoColors.CardBorder, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        // Marcas de los cuatro ejes: orientan el pulgar sin mirar
        for ((ax, ay) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
            Box(
                Modifier
                    .align(BiasAlignment(ax * 0.84f, ay * 0.84f))
                    .size(6.dp)
                    .background(PepoColors.CardBorder, CircleShape)
            )
        }
        Box(
            Modifier
                .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                .size(knobDp)
                .shadow(if (active) 2.dp else 6.dp, CircleShape)
                .background(if (active) PepoColors.BlueHover else PepoColors.Blue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(knobDp * 0.28f)
                    .background(PepoColors.Glow, CircleShape)
            )
        }
      }
    }
}
