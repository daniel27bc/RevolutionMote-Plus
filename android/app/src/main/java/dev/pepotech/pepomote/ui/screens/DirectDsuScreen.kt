package dev.pepotech.pepomote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.ButtonState
import dev.pepotech.pepomote.sensor.MotionEngine
import dev.pepotech.pepomote.sensor.SenderKind
import dev.pepotech.pepomote.server.ServerNetworks
import dev.pepotech.pepomote.server.core.DirectDsu
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

@Composable
internal fun DirectDsuScreen(foreground: Boolean, onPlaying: (Boolean) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.dsu_direct_title)
    var addresses by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var switchMotion by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var engine by remember { mutableStateOf<MotionEngine?>(null) }
    var failed by remember { mutableStateOf(false) }
    val currentOnPlaying by androidx.compose.runtime.rememberUpdatedState(onPlaying)
    LaunchedEffect(context, foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            addresses = ServerNetworks.addresses(context).local
            val activeAddress = selected
            if (activeAddress != null && activeAddress !in addresses) {
                failed = true
                selected = null
            }
            delay(1_000)
        }
    }
    DisposableEffect(selected, foreground, switchMotion) {
        val host = selected
        var server: DirectDsu? = null
        var motion: MotionEngine? = null
        if (foreground && host != null) {
            try {
                val address = InetAddress.getByName(host) as Inet4Address
                val prefix = NetworkInterface.getByInetAddress(address)?.interfaceAddresses
                    ?.firstOrNull { it.address == address }?.networkPrefixLength?.toInt()
                    ?.takeIf { it in 1..32 } ?: error("Prefijo de red no disponible")
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                val udp = DirectDsu(address, prefix, switchMotion = switchMotion) {
                    main.post {
                        if (selected == host) {
                            failed = true
                            selected = null
                        }
                    }
                }
                server = udp
                ButtonState.reset()
                val sensor = MotionEngine(context.applicationContext, 0, SenderKind.SWITCH, udp::input)
                motion = sensor
                sensor.start()
                engine = sensor
            } catch (_: Exception) {
                motion?.stop()
                server?.close()
                failed = true
                selected = null
            }
        }
        onDispose {
            motion?.stop()
            server?.close()
            engine = null
        }
    }
    LaunchedEffect(engine) { currentOnPlaying(engine != null) }
    DisposableEffect(Unit) { onDispose { currentOnPlaying(false) } }

    val live = engine
    val endpoint = selected?.let { "${it}:${DirectDsu.PORT}" }
    if (live != null && endpoint != null) {
        BackHandler { selected = null }
        DirectDsuPad(DirectDsuPadUi(title, live) {
            Text(endpoint, color = PepoColors.Blue)
            Text(stringResource(R.string.dsu_direct_emulator), color = PepoColors.TextDim)
        }) { selected = null }
    } else {
        DirectDsuSetup(addresses, switchMotion, failed, { switchMotion = it }, { failed = false; selected = it }, onExit)
    }
}

@Composable
internal fun DirectDsuSetup(
    addresses: List<String>,
    switchMotion: Boolean,
    failed: Boolean,
    onProfile: (Boolean) -> Unit,
    onStart: (String) -> Unit,
    onExit: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(PepoColors.Background).statusBarsPadding()
            .navigationBarsPadding().displayCutoutPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onExit) { Text(stringResource(R.string.exit), color = PepoColors.Error) }
        Text(stringResource(R.string.dsu_direct_title), style = MaterialTheme.typography.headlineSmall, color = PepoColors.Text)
        Text(stringResource(R.string.dsu_direct_emulator), color = PepoColors.TextDim)
        ModeChip(stringResource(R.string.dsu_direct_standard), selected = !switchMotion) { onProfile(false) }
        ModeChip(stringResource(R.string.dsu_direct_switch), selected = switchMotion) { onProfile(true) }
        if (addresses.isEmpty()) Text(stringResource(R.string.dsu_direct_no_network), color = PepoColors.TextDim)
        for (address in addresses) {
            TextButton(onClick = { onStart(address) }) {
                Text(stringResource(R.string.dsu_direct_start, address, DirectDsu.PORT), color = PepoColors.Blue)
            }
        }
        if (failed) Text(stringResource(R.string.dsu_direct_error), color = PepoColors.Error)
    }
}
