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
enum class ChannelGlyph { Qr, Pad, Pointer, Stick, GamePad, Switch, Gear, Server, Retro, Keyboard, Wiimote, Nunchuk, Dolphin, Xbox, Invader, WiiU, Kbm }

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
    /** Imagen a color tal cual (logos) en vez del glifo. */
    image: Int? = null,
    /** Silueta de un color: se tiñe con [accent]. */
    mask: Int? = null,
    /**
     * Logo propio del programa, si lo has puesto tú en `res/drawable` con
     * este nombre (p. ej. `logo_dolphin.xml` o `.png`). Si no está, se usa
     * el glifo dibujado.
     */
    logoName: String? = null,
    onClick: () -> Unit = {}
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    @Suppress("DiscouragedApi")
    val logo = androidx.compose.runtime.remember(logoName) {
        logoName?.let { ctx.resources.getIdentifier(it, "drawable", ctx.packageName) }?.takeIf { it != 0 }
    }
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
                val pic = logo ?: image
                if (pic != null) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(pic), null,
                        Modifier.size(glyphSize).padding(bottom = 4.dp)
                    )
                } else if (mask != null) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(mask), null,
                        Modifier.size(glyphSize).padding(bottom = 4.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent)
                    )
                } else if (icon != null) {
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
        ChannelGlyph.WiiU -> drawWiiUGlyph(accent)
        ChannelGlyph.Kbm -> drawKbmGlyph(accent, card)
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

/** Un delfín saltando (dibujo propio, en una caja de 100). */
private fun DrawScope.drawDolphinGlyph(fill: Color, eye: Color) {
    val path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
        "M93,41 C89,39 85,38 82,37 C80,31 74,26 66,24 C61,23 57,23 54,23.5 C50,17 45,12 39,9 C41,14 42,19 41,25 C31,30 23,39 18,51 C16,56 15,60 14,63 C10,60 6,58 2,58 C6,63 9,67 11,72 C10,76 9,80 7,84 C12,82 17,78 20,74 C24,64 31,55 41,49 C49,44 57,42 65,41.5 C73,41.5 79,42.5 84,43.5 C87,44 90,43.5 93,41 Z M59,42 C57,48 54,53 49,56 C56,55 62,50 66,41.5 Z"
    ).toPath()
    // El dibujo ocupa x 2..93, y 9..84: se escala a lo ancho y se centra
    val k = size.minDimension / 93f
    scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
        translate(-2f, -9f + (91f - 75f) / 2f) {
            drawPath(path, fill)
            drawCircle(eye, 2.2f, o(76f, 33f))
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

/** GamePad de Wii U en línea: cuerpo, pantalla, sticks, cruceta y botones. */
private fun DrawScope.drawWiiUGlyph(c: Color) {
    val w = size.width
    val h = size.height
    val st = Stroke(w * 0.055f)
    drawRoundRect(c, o(w * 0.03f, h * 0.2f), sz(w * 0.94f, h * 0.6f), cr(w * 0.16f), style = st)
    drawRoundRect(c, o(w * 0.3f, h * 0.31f), sz(w * 0.4f, h * 0.36f), cr(w * 0.03f), style = st)
    drawRoundRect(c, o(w * 0.42f, h * 0.235f), sz(w * 0.16f, h * 0.035f), cr(w * 0.02f))
    drawCircle(c, w * 0.055f, o(w * 0.16f, h * 0.34f), style = Stroke(w * 0.045f))
    drawCircle(c, w * 0.055f, o(w * 0.84f, h * 0.34f), style = Stroke(w * 0.045f))
    drawRect(c, o(w * 0.145f, h * 0.5f), sz(w * 0.03f, h * 0.13f))
    drawRect(c, o(w * 0.095f, h * 0.55f), sz(w * 0.13f, h * 0.03f))
    for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
        drawCircle(c, w * 0.018f, o(w * 0.84f + dx * w * 0.045f, h * 0.565f + dy * w * 0.045f))
    }
    drawCircle(c, w * 0.018f, o(w * 0.5f, h * 0.73f))
}

/** Teclado con las ondas de inalámbrico encima y un ratón al lado (relleno). */
private fun DrawScope.drawKbmGlyph(c: Color, hole: Color) {
    val w = size.width
    val h = size.height
    // Ondas
    for (r in listOf(0.2f, 0.13f, 0.065f)) {
        drawArc(
            c, 225f, 90f, false,
            topLeft = o(w * 0.37f - w * r, h * 0.37f - w * r), size = sz(w * r * 2, w * r * 2),
            style = Stroke(w * 0.04f, cap = StrokeCap.Round)
        )
    }
    // Teclado
    val top = h * 0.47f
    val kw = w * 0.7f
    val kh = h * 0.32f
    drawRoundRect(c, o(w * 0.02f, top), sz(kw, kh), cr(w * 0.035f))
    val kx = kw / 9f
    for (row in 0..2) for (col in 0..7) {
        if (row == 2 && col in 2..5) continue
        drawRect(hole, o(w * 0.02f + kx * 0.55f + col * kx, top + kh * (0.14f + row * 0.28f)), sz(kx * 0.62f, kh * 0.17f))
    }
    drawRect(hole, o(w * 0.02f + kx * 2.55f, top + kh * 0.7f), sz(kx * 3.62f, kh * 0.17f))
    // Ratón
    val mx = w * 0.79f
    val mw = w * 0.19f
    val mt = top + kh * 0.05f
    val mh = kh * 0.95f
    drawRoundRect(c, o(mx, mt), sz(mw, mh), cr(mw / 2f))
    drawLine(hole, o(mx + mw / 2f, mt + mh * 0.08f), o(mx + mw / 2f, mt + mh * 0.38f), strokeWidth = w * 0.025f, cap = StrokeCap.Round)
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
