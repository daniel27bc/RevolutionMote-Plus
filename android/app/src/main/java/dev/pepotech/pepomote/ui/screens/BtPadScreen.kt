package dev.pepotech.pepomote.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.BtHid
import dev.pepotech.pepomote.control.BtPad
import dev.pepotech.pepomote.service.UiLink
import dev.pepotech.pepomote.ui.theme.PepoColors

/**
 * Lo que el mando universal enseña en su cabecera cuando va por Bluetooth
 * (null = va por el receptor, como siempre).
 */
internal class BtPadUi(val title: String, val controls: @Composable () -> Unit)

internal val LocalBtPad = compositionLocalOf<BtPadUi?> { null }

/** El mando universal de siempre, mandando por Bluetooth en vez de por el receptor. */
@Composable
fun BtPadScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val bt by BtHid.state.collectAsState()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { ok -> ok }) BtPad.start(context)
    }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    DisposableEffect(Unit) {
        val missing = BtHid.permissions.filter {
            context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) BtPad.start(context) else permLauncher.launch(missing.toTypedArray())
        onDispose { BtPad.stop() }
    }
    val (text, color) = when (bt.status) {
        BtHid.Status.UNSUPPORTED -> stringResource(R.string.btpad_unsupported) to PepoColors.Error
        BtHid.Status.OFF -> stringResource(R.string.kbm_bt_off) to PepoColors.Warn
        BtHid.Status.NO_PERMISSION -> stringResource(R.string.kbm_bt_permission) to PepoColors.Warn
        BtHid.Status.STARTING -> stringResource(R.string.kbm_bt_starting) to PepoColors.TextDim
        BtHid.Status.READY -> stringResource(R.string.kbm_bt_ready) to PepoColors.TextDim
        BtHid.Status.CONNECTED -> stringResource(R.string.kbm_bt_connected, bt.host ?: "") to PepoColors.Ok
    }
    val ui = BtPadUi(stringResource(R.string.btpad_title)) {
        Column {
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
            if (bt.status == BtHid.Status.READY) {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        discoverable.launch(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                        )
                    }) { Text(stringResource(R.string.kbm_bt_visible), color = PepoColors.Blue) }
                    for (d in BtHid.bonded().take(4)) {
                        val name = BtHid.nameOf(d)
                        TextButton(onClick = { BtHid.connect(d) }) { Text(name, color = PepoColors.Text, maxLines = 1) }
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalBtPad provides ui) {
        GamePadScreen(link = UiLink.Disconnected, onDisconnect = onExit)
    }
}
