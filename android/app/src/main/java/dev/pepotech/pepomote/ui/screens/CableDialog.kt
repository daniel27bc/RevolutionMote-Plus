package dev.pepotech.pepomote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.ui.components.Ico
import dev.pepotech.pepomote.ui.theme.PepoColors

/**
 * «Hemos detectado que estás conectado por cable»: al abrir un mando con la
 * conexión USB compartida activa. [onAnswer]: si se usa el cable y si hay que
 * recordar la respuesta (el ajuste «Conexión por cable» la guarda).
 */
@Composable
internal fun CableDialog(onAnswer: (useCable: Boolean, remember: Boolean) -> Unit, onDismiss: () -> Unit) {
    var keep by remember { mutableStateOf(false) }
    EditorDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_cable, PepoColors.Blue, 26.dp)
            Text(
                stringResource(R.string.cable_prompt_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.cable_prompt_text), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = keep, onCheckedChange = { keep = it }, colors = CheckboxDefaults.colors(checkedColor = PepoColors.Blue))
            Text(stringResource(R.string.cable_prompt_remember), style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAnswer(false, keep) }) {
                Text(stringResource(R.string.cable_prompt_no), color = PepoColors.TextDim)
            }
            TextButton(onClick = { onAnswer(true, keep) }) {
                Text(stringResource(R.string.cable_prompt_yes), color = PepoColors.Blue)
            }
        }
    }
}
