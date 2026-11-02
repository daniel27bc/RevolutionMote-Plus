package dev.pepotech.pepomote.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Un icono de Material Symbols (redondeado, `res/drawable/ms_*.xml`, Apache
 * 2.0; ver `assets/licenses`). Decorativo: el texto de al lado ya lo dice.
 */
@Composable
fun Ico(@DrawableRes res: Int, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
