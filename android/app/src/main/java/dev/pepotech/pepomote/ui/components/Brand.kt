package dev.pepotech.pepomote.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.ui.theme.PepoColors

const val APP_NAME = "RevolutionMote+"

/** Logo + «RevolutionMote+» (el «+» con el color de acento). */
@Composable
fun BrandTitle(modifier: Modifier = Modifier, logo: Dp = 44.dp, fontSize: TextUnit = 30.sp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (logo > 0.dp) Image(painterResource(R.drawable.rm_logo), contentDescription = null, modifier = Modifier.size(logo))
        Text(
            buildAnnotatedString {
                append("RevolutionMote")
                withStyle(SpanStyle(color = PepoColors.Blue)) { append("+") }
            },
            style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize),
            maxLines = 1
        )
    }
}
