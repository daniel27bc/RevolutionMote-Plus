package dev.pepotech.pepomote.net

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.BufferedReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

internal data class MicrophoneEndpoint(
    val host: String,
    val port: Int,
    val token: String,
    val sessionId: Int,
    val source: InetAddress?,
)

internal class MicrophoneClient(
    endpoint: MicrophoneEndpoint,
    onStatus: (String) -> Unit,
) : AutoCloseable {
    private val running = AtomicBoolean(true)
    private val socket = Socket()
    private val thread = Thread({
        try {
            endpoint.source?.let { socket.bind(InetSocketAddress(it, 0)) }
            socket.tcpNoDelay = true
            socket.soTimeout = 6_000
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 4_000)
            val output = socket.getOutputStream()
            val hello = JSONObject().put("m", "microphone").put("pv", 1)
                .put("session_id", endpoint.sessionId.toUInt().toLong()).put("token", endpoint.token)
            output.write("$hello\n".toByteArray(Charsets.UTF_8))
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val status = response(reader)
            if (status != "ready") {
                if (running.get()) onStatus(status)
            } else if (running.get()) {
                record(onReady = { if (running.get()) onStatus("ready") }) { output.write(it) }
            }
        } catch (_: Exception) {
            if (running.get()) onStatus("unavailable")
        } finally {
            socket.close()
        }
    }, "pepomote-microphone").apply { isDaemon = true; start() }

    // La confirmación llega en una línea corta; la captura empieza tras el OK.
    private fun response(reader: BufferedReader): String {
        val line = StringBuilder()
        while (line.length < 256) {
            val char = reader.read()
            if (char == -1) break
            if (char == 10) return JSONObject(line.toString()).optString("status", "unavailable")
            line.append(char.toChar())
        }
        return "unavailable"
    }

    @SuppressLint("MissingPermission") // El permiso se pide al pulsar «Conectar micrófono».
    private fun record(onReady: () -> Unit, send: (ByteArray) -> Unit) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0)
        val mic = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, SAMPLES * 4))
        try {
            check(mic.state == AudioRecord.STATE_INITIALIZED)
            if (!running.get()) return
            mic.startRecording()
            check(mic.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            onReady()
            val samples = ShortArray(SAMPLES)
            val bytes = ByteArray(SAMPLES * 2)
            var buffered = 0
            while (running.get()) {
                val read = mic.read(samples, buffered, samples.size - buffered, AudioRecord.READ_BLOCKING)
                if (!running.get()) break
                check(read > 0)
                buffered += read
                if (buffered != samples.size) continue
                for (i in samples.indices) {
                    val value = samples[i].toInt()
                    bytes[i * 2] = value.toByte()
                    bytes[i * 2 + 1] = (value shr 8).toByte()
                }
                send(bytes)
                buffered = 0
            }
        } finally {
            try {
                if (mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) mic.stop()
            } finally {
                mic.release()
            }
        }
    }

    override fun close() {
        running.set(false)
        socket.close()
        thread.interrupt()
    }

    companion object {
        private const val RATE = 48_000
        private const val SAMPLES = 960
    }
}
