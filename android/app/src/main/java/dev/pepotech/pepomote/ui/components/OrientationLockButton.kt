package dev.pepotech.pepomote.ui.components

import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.AppPrefs
import dev.pepotech.pepomote.control.OrientationLock
import dev.pepotech.pepomote.ui.screens.rememberDisplayRotation
import dev.pepotech.pepomote.ui.theme.PepoColors

/** 🔒 / 🔓: fija la orientación de ahora o la suelta ([OrientationLock]). */
@Composable
fun OrientationLockButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lock by OrientationLock.state.collectAsState()
    val rotation = rememberDisplayRotation()
    val locked = lock != AppPrefs.ORIENTATION_FREE
    val desc = stringResource(if (locked) R.string.orientation_unlock else R.string.orientation_lock)
    TextButton(
        onClick = { OrientationLock.toggle(context, rotation) },
        modifier = modifier.semantics { contentDescription = desc }
    ) {
        Ico(
            if (locked) R.drawable.ms_lock_fill1 else R.drawable.ms_lock_open,
            if (locked) PepoColors.Warn else PepoColors.TextDim,
            22.dp
        )
    }
}
