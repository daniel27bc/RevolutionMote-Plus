package dev.pepotech.pepomote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pepotech.pepomote.R
import dev.pepotech.pepomote.control.ClipboardTransfer
import dev.pepotech.pepomote.service.LinkState
import dev.pepotech.pepomote.service.UiLink
import dev.pepotech.pepomote.ui.theme.PepoColors
import kotlinx.coroutines.delay
import java.util.UUID

private data class ClipboardRequest(val id: String, val receiving: Boolean)

@Composable
internal fun ClipboardDialog(ready: Boolean, connected: Boolean, onDismiss: () -> Unit) {
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val label = stringResource(R.string.clipboard_title)
    var pending by remember { mutableStateOf<ClipboardRequest?>(null) }
    var status by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(clipboard, label) {
        LinkState.clipboardReplies.collect { reply ->
            val request = pending ?: return@collect
            if (reply.id != request.id) return@collect
            pending = null
            status = when {
                !reply.ok -> when (reply.error) {
                    "empty" -> R.string.clipboard_empty
                    "disabled" -> R.string.clipboard_enable_pc
                    "invalid_text" -> R.string.clipboard_invalid
                    else -> R.string.clipboard_unavailable
                }
                !request.receiving -> R.string.clipboard_sent
                reply.text != null && clipboard != null -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, reply.text))
                    R.string.clipboard_received
                }
                else -> R.string.clipboard_unavailable
            }
        }
    }
    LaunchedEffect(ready, pending) {
        val request = pending ?: return@LaunchedEffect
        if (ready) delay(5_000)
        if (pending == request) {
            pending = null
            status = R.string.clipboard_unavailable
        }
    }

    fun transfer(receiving: Boolean) {
        val send = LinkState.requestClipboard
        if (send == null || LinkState.flow.value !is UiLink.Connected) {
            status = R.string.clipboard_unavailable
            return
        }
        val text = if (receiving) null else {
            val clip = clipboard?.primaryClip
            if (clip == null || clip.itemCount == 0 || !clip.description.hasMimeType("text/*")) {
                status = R.string.clipboard_empty
                return
            }
            val value = clip.getItemAt(0).text?.toString().orEmpty()
            if (!ClipboardTransfer.valid(value)) {
                status = if (value.isEmpty()) R.string.clipboard_empty else R.string.clipboard_invalid
                return
            }
            value
        }
        val request = ClipboardRequest(UUID.randomUUID().toString(), receiving)
        pending = request
        status = null
        send(request.id, text)
    }

    EditorDialog(onDismiss) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val hint = when {
            !connected -> R.string.clipboard_connect
            !ready -> R.string.clipboard_receiver
            else -> R.string.clipboard_enable_pc
        }
        Text(stringResource(hint), color = PepoColors.TextDim)
        TextButton(
            onClick = { transfer(receiving = false) },
            enabled = ready && pending == null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.clipboard_to_pc)) }
        TextButton(
            onClick = { transfer(receiving = true) },
            enabled = ready && pending == null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.clipboard_from_pc)) }
        val message = if (pending != null) R.string.clipboard_waiting else status
        if (message != null && message != hint) {
            Text(stringResource(message), color = PepoColors.TextDim)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.ed_done), color = PepoColors.Blue, fontWeight = FontWeight.Bold)
        }
    }
}
