package dev.pepotech.pepomote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.ui.theme.PepoColors

// Glifos propios, dibujados a mano — nada de iconografía ajena.
enum class ChannelGlyph { Qr, Pad, Pointer, Stick, GamePad, Switch, Gear, Server, Retro, Keyboard, Wiimote, Nunchuk, Dolphin, Xbox, Invader }

/** `wide`: tarjeta apaisada a todo el ancho (glifo a la izquierda, textos al lado). */
@Composable
fun ChannelCard(
    title: String,
    subtitle: String,
    glyph: ChannelGlyph,
    accent: Color = PepoColors.Blue,
    wide: Boolean = false,
    /** Icono de Material Symbols en vez del glifo dibujado (el engranaje de Ajustes). */
    icon: Int? = null,
    onClick: () -> Unit = {}
) {
    val ink = PepoColors.Text
    val card = PepoColors.Card
    val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val glyphSize = if (land) 40.dp else 52.dp
    Card(
        onClick = onClick,
        // Apaisado: tarjetas más bajas que anchas (cuadradas eran enormes)
        modifier = if (wide) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(if (land) 1.3f else 1f),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PepoColors.Card),
        border = BorderStroke(1.5.dp, PepoColors.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) Ico(icon, accent, 40.dp) else Canvas(modifier = Modifier.size(40.dp)) { drawGlyph(glyph, accent, ink, card) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Ico(icon, accent, glyphSize, Modifier.padding(bottom = 4.dp))
                } else {
                    Canvas(modifier = Modifier.size(glyphSize).padding(bottom = 4.dp)) {
                        drawGlyph(glyph, accent, ink, card)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (land) 1 else 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun DrawScope.drawGlyph(glyph: ChannelGlyph, accent: Color, ink: Color, card: Color) {
    when (glyph) {
        ChannelGlyph.Wiimote -> drawWiimoteGlyph(accent, ink)
        ChannelGlyph.Nunchuk -> drawNunchukGlyph(accent, ink)
        ChannelGlyph.Dolphin -> drawDolphinGlyph(accent, card)
        ChannelGlyph.Xbox -> drawXboxGlyph(accent, card)
        ChannelGlyph.Invader -> drawInvaderGlyph(accent)
        ChannelGlyph.Qr -> drawQrGlyph(accent)
        ChannelGlyph.Pad -> drawPadGlyph(accent)
        ChannelGlyph.Pointer -> drawPointerGlyph(accent)
        ChannelGlyph.Stick -> drawStickGlyph(accent)
        ChannelGlyph.GamePad -> drawGamePadGlyph(accent)
        ChannelGlyph.Switch -> drawSwitchSplitGlyph()
        ChannelGlyph.Gear -> drawGearGlyph(accent)
        ChannelGlyph.Server -> drawServerGlyph(accent)
        ChannelGlyph.Retro -> drawRetroGlyph(accent)
        ChannelGlyph.Keyboard -> drawKeyboardGlyph(accent)
    }
}

private fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
private fun sz(x: Float, y: Float) = androidx.compose.ui.geometry.Size(x, y)
private fun cr(r: Float) = androidx.compose.ui.geometry.CornerRadius(r)

/**
 * Mando de Wii de frente: cuerpo alargado del color [body] (blanco en el
 * tema claro, negro en el oscuro) con su contorno, cruceta, A, −/HOME/+ y
 * los botones 1 y 2.
 */
private fun DrawScope.drawWiimoteGlyph(body: Color, ink: Color) {
    val w = size.width
    val h = size.height
    val bw = w * 0.36f
    val left = (w - bw) / 2f
    val detail = if (body.luminance() > 0.5f) Color(0xFF8A939B) else Color(0xFF9AA4AD)
    drawRoundRect(body, o(left, h * 0.03f), sz(bw, h * 0.94f), cr(bw * 0.4f))
    drawRoundRect(ink.copy(alpha = 0.55f), o(left, h * 0.03f), sz(bw, h * 0.94f), cr(bw * 0.4f), style = Stroke(w * 0.035f))
    val cx = w / 2f
    // Cruceta
    drawRoundRect(detail, o(cx - w * 0.045f, h * 0.11f), sz(w * 0.09f, h * 0.2f), cr(w * 0.015f))
    drawRoundRect(detail, o(cx - w * 0.1f, h * 0.165f), sz(w * 0.2f, h * 0.09f), cr(w * 0.015f))
    // A
    drawCircle(detail, w * 0.07f, o(cx, h * 0.42f))
    // − HOME +
    for (dx in listOf(-0.09f, 0f, 0.09f)) drawCircle(detail, w * 0.025f, o(cx + w * dx, h * 0.6f))
    // 1 y 2
    drawCircle(detail, w * 0.045f, o(cx, h * 0.74f))
    drawCircle(detail, w * 0.045f, o(cx, h * 0.86f))
}

/** Nunchuk: cuerpo de gota del color [body], el stick arriba y C / Z. */
private fun DrawScope.drawNunchukGlyph(body: Color, ink: Color) {
    val w = size.width
    val h = size.height
    val detail = if (body.luminance() > 0.5f) Color(0xFF8A939B) else Color(0xFF9AA4AD)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        cubicTo(w * 0.8f, h * 0.04f, w * 0.82f, h * 0.3f, w * 0.74f, h * 0.5f)
        cubicTo(w * 0.66f, h * 0.7f, w * 0.64f, h * 0.96f, w * 0.5f, h * 0.96f)
        cubicTo(w * 0.36f, h * 0.96f, w * 0.34f, h * 0.7f, w * 0.26f, h * 0.5f)
        cubicTo(w * 0.18f, h * 0.3f, w * 0.2f, h * 0.04f, w * 0.5f, h * 0.04f)
        close()
    }
    drawPath(path, body)
    drawPath(path, ink.copy(alpha = 0.55f), style = Stroke(w * 0.035f))
    drawCircle(detail, w * 0.13f, o(w * 0.5f, h * 0.24f))
    drawCircle(body, w * 0.07f, o(w * 0.5f, h * 0.24f))
    drawCircle(detail, w * 0.035f, o(w * 0.5f, h * 0.48f))
}

/** Un delfín saltando (dibujo propio). */
private fun DrawScope.drawDolphinGlyph(fill: Color, eye: Color) {
    val path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
        "M44,24.5 C41.5,24.8 39.6,24 38.6,22.6 C37,17.5 32,14 26,13.2 L22.5,6.5 C21.2,9.5 20,12 18.6,13.6 " +
            "C13,15.5 9,20.5 7.5,27 L3,29.5 C5.5,31 6.8,32.6 7.2,35.2 C8.5,33 10.2,31.8 12.6,31.6 L10.4,29.8 " +
            "C13.5,22 20,18.5 27,18.6 C32,18.7 36.5,21.5 39,25.4 C40.6,26.6 42.6,26.4 44,24.5 Z " +
            "M26,19.2 L23,25.5 L30,19.6 Z"
    ).toPath()
    // El dibujo ocupa x 3..44, y 6.5..35: se escala a lo ancho y se centra
    val k = size.minDimension / 41f
    scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
        translate(-3f, -6.5f + (41f - 28.5f) / 2f) {
            drawPath(path, fill)
            drawCircle(eye, 1.2f, o(35.2f, 18.6f))
        }
    }
}

/** Mando de estilo Xbox: cuerpo con asas, dos sticks a distinta altura y cuatro botones. */
private fun DrawScope.drawXboxGlyph(fill: Color, hole: Color) {
    val w = size.width
    val h = size.height
    val body = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.3f, h * 0.24f)
        lineTo(w * 0.7f, h * 0.24f)
        cubicTo(w * 0.86f, h * 0.24f, w * 0.94f, h * 0.36f, w * 0.97f, h * 0.56f)
        cubicTo(w * 1.0f, h * 0.76f, w * 0.94f, h * 0.84f, w * 0.86f, h * 0.84f)
        cubicTo(w * 0.78f, h * 0.84f, w * 0.74f, h * 0.7f, w * 0.66f, h * 0.66f)
        lineTo(w * 0.34f, h * 0.66f)
        cubicTo(w * 0.26f, h * 0.7f, w * 0.22f, h * 0.84f, w * 0.14f, h * 0.84f)
        cubicTo(w * 0.06f, h * 0.84f, w * 0.0f, h * 0.76f, w * 0.03f, h * 0.56f)
        cubicTo(w * 0.06f, h * 0.36f, w * 0.14f, h * 0.24f, w * 0.3f, h * 0.24f)
        close()
    }
    drawPath(body, fill)
    // Stick izquierdo arriba, derecho abajo (como en Xbox)
    drawCircle(hole, w * 0.065f, o(w * 0.24f, h * 0.42f))
    drawCircle(hole, w * 0.065f, o(w * 0.62f, h * 0.56f))
    // Cruceta
    drawRect(hole, o(w * 0.355f, h * 0.48f), sz(w * 0.03f, h * 0.12f))
    drawRect(hole, o(w * 0.31f, h * 0.525f), sz(w * 0.12f, h * 0.03f))
    // A B X Y
    for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
        drawCircle(hole, w * 0.028f, o(w * 0.77f + dx * w * 0.06f, h * 0.42f + dy * w * 0.06f))
    }
}

/** Marcianito de píxeles (lo retro), dibujo propio. */
private fun DrawScope.drawInvaderGlyph(fill: Color) {
    val rows = listOf(
        "..X.....X..",
        "...X...X...",
        "..XXXXXXX..",
        ".XX.XXX.XX.",
        "XXXXXXXXXXX",
        "X.XXXXXXX.X",
        "X.X.....X.X",
        "...XX.XX..."
    )
    val cell = size.width / 12f
    val top = (size.height - cell * rows.size) / 2f
    val left = (size.width - cell * 11) / 2f
    for ((r, line) in rows.withIndex()) for ((c, ch) in line.withIndex()) {
        if (ch == 'X') drawRect(fill, o(left + c * cell, top + r * cell), sz(cell + 0.5f, cell + 0.5f))
    }
}

/** Joy-Con: rojo a la izquierda y azul a la derecha (los colores de siempre). */
private fun DrawScope.drawSwitchSplitGlyph() {
    val w = size.width
    val h = size.height
    val red = Color(0xFFE60012)
    val blue = Color(0xFF00B4E0)
    drawRoundRect(red, o(w * 0.14f, h * 0.08f), sz(w * 0.3f, h * 0.84f), cr(w * 0.12f))
    drawRoundRect(blue, o(w * 0.56f, h * 0.08f), sz(w * 0.3f, h * 0.84f), cr(w * 0.12f))
    drawRect(red, o(w * 0.3f, h * 0.08f), sz(w * 0.14f, h * 0.84f))
    drawRect(blue, o(w * 0.56f, h * 0.08f), sz(w * 0.14f, h * 0.84f))
    drawCircle(Color(0xFF2B2B2B), w * 0.065f, o(w * 0.3f, h * 0.32f))
    drawCircle(Color(0xFF2B2B2B), w * 0.065f, o(w * 0.7f, h * 0.62f))
}

/** Teclado (a la izquierda, algo más pequeño) y un ratón a su derecha. */
private fun DrawScope.drawKeyboardGlyph(accent: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.06f
    val kbW = w * 0.68f
    val kbH = w * 0.44f
    val top = h * 0.38f
    drawRoundRect(accent, o(stroke / 2f, top + stroke / 2f), sz(kbW - stroke, kbH - stroke), cr(w * 0.08f), style = Stroke(width = stroke))
    val key = w * 0.075f
    for (row in 0..1) {
        for (col in 0..3) {
            drawRoundRect(accent, o(w * 0.1f + col * w * 0.13f, top + kbH * (0.2f + row * 0.26f)), sz(key, key), cr(w * 0.015f))
        }
    }
    drawRoundRect(accent, o(w * 0.18f, top + kbH * 0.72f), sz(w * 0.32f, key * 0.9f), cr(w * 0.015f))
    // Ratón: cuerpo ovalado, la raya entre los botones y el cable
    val mx = w * 0.74f
    val mw = w * 0.24f
    val mTop = h * 0.34f
    val mh = w * 0.4f
    drawRoundRect(accent, o(mx, mTop), sz(mw, mh), cr(mw / 2f), style = Stroke(width = stroke))
    drawLine(accent, o(mx + mw / 2f, mTop + stroke), o(mx + mw / 2f, mTop + mh * 0.38f), strokeWidth = stroke * 0.8f)
    drawLine(accent, o(mx + stroke * 0.6f, mTop + mh * 0.4f), o(mx + mw - stroke * 0.6f, mTop + mh * 0.4f), strokeWidth = stroke * 0.8f)
    drawLine(accent, o(mx + mw / 2f, mTop), o(mx + mw / 2f, h * 0.18f), strokeWidth = stroke * 0.8f)
}

/** Mando clásico apaisado: cuerpo, cruceta a la izquierda y dos botones a la derecha. */
private fun DrawScope.drawRetroGlyph(accent: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(accent, androidx.compose.ui.geometry.Offset(w * .04f, h * .28f),
        androidx.compose.ui.geometry.Size(w * .92f, h * .44f),
        androidx.compose.ui.geometry.CornerRadius(w * .08f), style = Stroke(w * .045f))
    // cruceta
    drawLine(accent, androidx.compose.ui.geometry.Offset(w * .27f, h * .38f),
        androidx.compose.ui.geometry.Offset(w * .27f, h * .62f), w * .07f, StrokeCap.Round)
    drawLine(accent, androidx.compose.ui.geometry.Offset(w * .15f, h * .50f),
        androidx.compose.ui.geometry.Offset(w * .39f, h * .50f), w * .07f, StrokeCap.Round)
    // B y A
    drawCircle(accent, w * .05f, androidx.compose.ui.geometry.Offset(w * .66f, h * .55f))
    drawCircle(accent, w * .05f, androidx.compose.ui.geometry.Offset(w * .82f, h * .45f))
}

private fun DrawScope.drawServerGlyph(accent: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(accent, androidx.compose.ui.geometry.Offset(w * .25f, h * .04f),
        androidx.compose.ui.geometry.Size(w * .5f, h * .92f),
        androidx.compose.ui.geometry.CornerRadius(w * .08f), style = Stroke(w * .045f))
    for (y in listOf(.30f, .46f, .62f)) {
        drawLine(accent, androidx.compose.ui.geometry.Offset(w * .38f, h * y),
            androidx.compose.ui.geometry.Offset(w * .62f, h * y), w * .045f, StrokeCap.Round)
    }
    drawCircle(accent, w * .03f, androidx.compose.ui.geometry.Offset(w * .5f, h * .84f))
}

private fun DrawScope.drawSwitchGlyph(accent: Color) {
    val w = size.width
    val h = size.height
    for (x in listOf(0.16f, 0.56f)) {
        drawRoundRect(accent, androidx.compose.ui.geometry.Offset(w * x, h * 0.08f),
            androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.84f),
            androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = Stroke(w * 0.04f))
    }
    drawCircle(accent, w * 0.065f, androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.32f))
    drawCircle(accent, w * 0.065f, androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.68f))
}

private fun DrawScope.drawQrGlyph(accent: Color) {
    val cell = size.width / 5f
    val positions = listOf(
        0 to 0, 1 to 0, 3 to 0, 4 to 0,
        0 to 1, 4 to 1,
        2 to 2,
        0 to 3, 3 to 3,
        0 to 4, 1 to 4, 4 to 4
    )
    positions.forEach { (x, y) ->
        drawRoundRect(
            color = accent,
            topLeft = androidx.compose.ui.geometry.Offset(x * cell + cell * 0.1f, y * cell + cell * 0.1f),
            size = androidx.compose.ui.geometry.Size(cell * 0.8f, cell * 0.8f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.2f)
        )
    }
}

private fun DrawScope.drawPadGlyph(accent: Color) {
    val w = size.width
    val arm = w * 0.3f
    val thick = w * 0.28f
    val r = androidx.compose.ui.geometry.CornerRadius(thick * 0.35f)
    // Cruceta: barra vertical + barra horizontal
    drawRoundRect(
        color = accent,
        topLeft = androidx.compose.ui.geometry.Offset((w - thick) / 2f, (w - (arm * 2 + thick)) / 2f),
        size = androidx.compose.ui.geometry.Size(thick, arm * 2 + thick),
        cornerRadius = r
    )
    drawRoundRect(
        color = accent,
        topLeft = androidx.compose.ui.geometry.Offset((w - (arm * 2 + thick)) / 2f, (w - thick) / 2f),
        size = androidx.compose.ui.geometry.Size(arm * 2 + thick, thick),
        cornerRadius = r
    )
}

private fun DrawScope.drawPointerGlyph(accent: Color) {
    val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = accent, radius = size.width * 0.42f, center = c, style = Stroke(width = size.width * 0.10f))
    drawCircle(color = accent, radius = size.width * 0.12f, center = c)
}

/** Stick empujado arriba-derecha: aro, vástago y pomo. */
private fun DrawScope.drawStickGlyph(accent: Color) {
    val w = size.width
    val c = androidx.compose.ui.geometry.Offset(w / 2f, size.height / 2f)
    drawCircle(color = accent, radius = w * 0.42f, center = c, style = Stroke(width = w * 0.07f))
    val knob = androidx.compose.ui.geometry.Offset(c.x + w * 0.15f, c.y - w * 0.15f)
    drawLine(color = accent, start = c, end = knob, strokeWidth = w * 0.11f, cap = StrokeCap.Round)
    drawCircle(color = accent, radius = w * 0.17f, center = knob)
}

/** GamePad de Wii U: cuerpo apaisado con la pantalla en medio y un stick a cada lado. */
private fun DrawScope.drawGamePadGlyph(accent: Color) {
    val w = size.width
    val stroke = w * 0.08f
    val bodyH = w * 0.64f
    val top = (size.height - bodyH) / 2f
    drawRoundRect(
        color = accent,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, top + stroke / 2f),
        size = androidx.compose.ui.geometry.Size(w - stroke, bodyH - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
        style = Stroke(width = stroke)
    )
    // Pantalla táctil
    drawRoundRect(
        color = accent,
        topLeft = androidx.compose.ui.geometry.Offset(w * 0.31f, top + bodyH * 0.24f),
        size = androidx.compose.ui.geometry.Size(w * 0.38f, bodyH * 0.52f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f)
    )
    // Sticks
    drawCircle(color = accent, radius = w * 0.075f, center = androidx.compose.ui.geometry.Offset(w * 0.17f, top + bodyH * 0.40f))
    drawCircle(color = accent, radius = w * 0.075f, center = androidx.compose.ui.geometry.Offset(w * 0.83f, top + bodyH * 0.40f))
    // Cruceta y botones, insinuados
    drawCircle(color = accent, radius = w * 0.04f, center = androidx.compose.ui.geometry.Offset(w * 0.17f, top + bodyH * 0.72f))
    drawCircle(color = accent, radius = w * 0.04f, center = androidx.compose.ui.geometry.Offset(w * 0.83f, top + bodyH * 0.72f))
}

private fun DrawScope.drawGearGlyph(accent: Color) {
    val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val rOut = size.width * 0.42f
    val rIn = size.width * 0.16f
    // Dientes: 8 rectángulos rotados
    for (i in 0 until 8) {
        rotate(degrees = i * 45f, pivot = c) {
            drawRoundRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - size.width * 0.06f, c.y - rOut),
                size = androidx.compose.ui.geometry.Size(size.width * 0.12f, size.width * 0.20f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.03f)
            )
        }
    }
    drawCircle(color = accent, radius = rOut * 0.72f, center = c, style = Stroke(width = size.width * 0.10f))
    drawCircle(color = accent, radius = rIn, center = c)
}

private fun DrawScope.rotate(degrees: Float, pivot: androidx.compose.ui.geometry.Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.rotate(degrees, pivot)
    block()
    drawContext.transform.rotate(-degrees, pivot)
}
