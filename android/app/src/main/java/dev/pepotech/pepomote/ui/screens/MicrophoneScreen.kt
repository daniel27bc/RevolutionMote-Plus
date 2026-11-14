package dev.pepotech.pepomote.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.net.MicrophoneClient
import dev.pepotech.pepomote.net.MicrophoneEndpoint
import dev.pepotech.pepomote.service.LinkState
import dev.pepotech.pepomote.service.UiLink
import dev.pepotech.pepomote.ui.theme.PepoColors

@Composable
internal fun MicrophoneScreen(foreground: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val endpoint by LinkState.microphoneEndpoint.collectAsState()
    val link by LinkState.flow.collectAsState()
    var pendingPermission by remember { mutableStateOf<MicrophoneEndpoint?>(null) }
    var requested by remember { mutableStateOf<MicrophoneEndpoint?>(null) }
    var status by remember { mutableStateOf<Int?>(null) }
    // Falta VB-CABLE: el diálogo para instalarlo desde aquí (null = cerrado)
    var driver by remember { mutableStateOf<DriverStep?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingPermission
        pendingPermission = null
        if (granted && target != null && target == LinkState.microphoneEndpoint.value) {
            requested = target
            status = R.string.microphone_connecting
        } else if (!granted) status = R.string.microphone_permission
    }
    LaunchedEffect(endpoint, foreground) {
        if (requested != null && (!foreground || endpoint != requested)) {
            requested = null
            status = R.string.microphone_stopped
        }
    }
    DisposableEffect(requested, endpoint, foreground) {
        val target = requested
        var active = true
        val client = if (foreground && target != null && target == endpoint) {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            MicrophoneClient(target) { reply ->
                val result = reply.status
                main.post {
                    if (!active || requested != target || LinkState.microphoneEndpoint.value != target) return@post
                    if (result == "ready") {
                        status = R.string.microphone_live
                    } else {
                        requested = null
                        status = when (result) {
                            "driver_missing" -> R.string.microphone_driver_missing
                            "busy" -> R.string.microphone_busy
                            else -> R.string.microphone_error
                        }
                        if (result == "driver_missing") driver = DriverStep.Offer
                    }
                }
            }
        } else null
        onDispose {
            active = false
            client?.close()
        }
    }

    // Instalar VB-CABLE en el PC: el receptor saca la ventana de permiso de
    // Windows y avisa al terminar. Con él ya puesto, el micrófono arranca solo.
    DisposableEffect(driver == DriverStep.Running, endpoint) {
        val target = endpoint
        var active = true
        val client = if (driver == DriverStep.Running && target != null) {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            MicrophoneClient(target, install = true) { reply ->
                main.post {
                    if (!active || driver != DriverStep.Running) return@post
                    driver = when (reply.status) {
                        "installing" -> DriverStep.Running
                        "installed", "ready" -> {
                            requested = target
                            status = R.string.microphone_connecting
                            null
                        }
                        "restart" -> DriverStep.Done(R.string.microphone_restart)
                        "declined" -> DriverStep.Declined
                        "busy" -> DriverStep.Done(R.string.microphone_busy)
                        // Un receptor viejo no sabe instalar: a mano
                        "manual", "driver_missing" -> DriverStep.Manual(reply.path)
                        else -> DriverStep.Manual(null)
                    }
                }
            }
        } else null
        onDispose {
            active = false
            client?.close()
        }
    }

    val connected = link is UiLink.Connected
    MicrophonePanel(
        available = foreground && endpoint != null && connected,
        connected = connected,
        running = requested != null,
        status = status,
        onConnect = {
            endpoint?.let { target ->
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    requested = target
                    status = R.string.microphone_connecting
                } else {
                    pendingPermission = target
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        onStop = {
            requested = null
            status = R.string.microphone_stopped
        },
        onExit = {
            requested = null
            onExit()
        }
    )

    driver?.let { step ->
        DriverDialog(
            step = step,
            canInstall = endpoint != null && connected,
            onInstall = { driver = DriverStep.Running },
            onWeb = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vb-audio.com/Cable/")))
                }
            },
            onDismiss = { if (step != DriverStep.Running) driver = null }
        )
    }
}

/** En qué punto está la instalación de VB-CABLE desde el móvil. */
internal sealed interface DriverStep {
    /** Falta: se ofrece instalarlo en el PC. */
    data object Offer : DriverStep
    /** El PC está con la ventana de permiso o instalando. */
    data object Running : DriverStep
    /** Se dijo que no al permiso de Windows: se puede volver a intentar. */
    data object Declined : DriverStep
    /** Terminó con este aviso (reiniciar el PC, otro móvil ocupado…). */
    data class Done(val message: Int) : DriverStep
    /** No se pudo solo: a mano, desde [folder] (ya abierta en el PC) o desde la web. */
    data class Manual(val folder: String?) : DriverStep
}

@Composable
internal fun DriverDialog(
    step: DriverStep,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onWeb: () -> Unit,
    onDismiss: () -> Unit,
) {
    EditorDialog(onDismiss = onDismiss) {
        Text(
            stringResource(if (step is DriverStep.Manual) R.string.microphone_manual_title else R.string.microphone_driver_title),
            style = MaterialTheme.typography.titleLarge, color = PepoColors.Text
        )
        when (step) {
            DriverStep.Offer, DriverStep.Declined -> {
                if (step == DriverStep.Declined) Text(stringResource(R.string.microphone_declined), color = PepoColors.Warn)
                Text(stringResource(R.string.microphone_driver_help), color = PepoColors.TextDim)
                TextButton(onClick = onInstall, enabled = canInstall) {
                    Text(stringResource(R.string.microphone_install), color = PepoColors.Blue)
                }
            }
            DriverStep.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = PepoColors.Blue, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.microphone_installing), color = PepoColors.Text)
            }
            is DriverStep.Done -> Text(stringResource(step.message), color = PepoColors.Text)
            is DriverStep.Manual -> {
                if (step.folder != null) {
                    Text(stringResource(R.string.microphone_manual_folder), color = PepoColors.TextDim)
                    Text(step.folder, color = PepoColors.Text, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.microphone_manual_web), color = PepoColors.TextDim)
                }
                Text(stringResource(R.string.microphone_manual_steps), color = PepoColors.TextDim)
                TextButton(onClick = onWeb) { Text(stringResource(R.string.microphone_driver_web), color = PepoColors.Blue) }
            }
        }
        if (step != DriverStep.Running) TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.ed_done), color = PepoColors.Blue)
        }
    }
}

@Composable
internal fun MicrophonePanel(
    available: Boolean,
    connected: Boolean,
    running: Boolean,
    status: Int?,
    onConnect: () -> Unit,
    onStop: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(PepoColors.Background).statusBarsPadding()
            .navigationBarsPadding().displayCutoutPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onExit) { Text(stringResource(R.string.exit), color = PepoColors.Error) }
        Text(stringResource(R.string.microphone_title), style = MaterialTheme.typography.headlineSmall, color = PepoColors.Text)
        Text(
            stringResource(when {
                !connected -> R.string.microphone_need_link
                !available -> R.string.microphone_need_windows
                else -> R.string.microphone_select_input
            }),
            color = PepoColors.TextDim
        )
        TextButton(onClick = if (running) onStop else onConnect, enabled = running || available) {
            Text(stringResource(if (running) R.string.microphone_stop else R.string.microphone_connect))
        }
        status?.let { Text(stringResource(it), color = PepoColors.Text) }
    }
}
