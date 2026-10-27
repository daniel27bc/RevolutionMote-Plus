package dev.pepotech.pepomote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pepotech.pepomote.control.TrackpadEngine
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlin.math.roundToInt

/**
 * Superficie táctil que emula el Stick Derecho como si fuera un Trackpad / Ratón
 * ("Mouse-Joystick"), traduciendo el desplazamiento y velocidad del dedo directamente
 * en deflexión suave de stick analógico.
 */
@Composable
fun RightStickTrackpad(
    sizeDp: Dp,
    sensitivity: Float = 1.0f,
    antiDeadzone: Float = 0.15f,
    onStick: (x: Int, y: Int) -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    var active by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(sizeDp)
            .background(PepoColors.Card, shape)
            .border(1.5.dp, if (active) PepoColors.Glow else PepoColors.CardBorder, shape)
            .pointerInput(sizeDp, sensitivity, antiDeadzone) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    active = true
                    var last = down.position
                    touchPos = down.position
                    drag(down.id) { change ->
                        change.consume()
                        val curr = change.position
                        val dx = curr.x - last.x
                        val dy = curr.y - last.y
                        last = curr
                        touchPos = curr
                        val (sx, sy) = TrackpadEngine.processDelta(
                            dx, dy,
                            sensitivity = sensitivity,
                            antiDeadzone = antiDeadzone
                        )
                        onStick(sx, sy)
                    }
                    active = false
                    onStick(0, 0)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(if (active) 0.8f else 0.4f)
        ) {
            Text(
                "TRACKPAD",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) PepoColors.Glow else PepoColors.Blue,
                letterSpacing = 2.sp
            )
            Text(
                "Aim",
                style = MaterialTheme.typography.bodySmall,
                color = PepoColors.DimText,
                fontSize = 10.sp
            )
        }

        // Indicador luminoso de toque
        if (active) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (touchPos.x - 12.dp.toPx()).roundToInt(),
                            (touchPos.y - 12.dp.toPx()).roundToInt()
                        )
                    }
                    .size(24.dp)
                    .background(PepoColors.Glow.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}
