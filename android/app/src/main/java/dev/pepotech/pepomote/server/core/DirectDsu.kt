package dev.pepotech.pepomote.server.core

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

/** Un mando local. Los emuladores se suscriben por UDP, sin sesión PMP remota. */
internal class DirectDsu(
    address: Inet4Address,
    private val prefix: Int,
    port: Int = PORT,
    private val switchMotion: Boolean = false,
    private val onError: (IOException) -> Unit = {},
) : AutoCloseable {
    private val socket = DatagramSocket(null).apply {
        try {
            bind(InetSocketAddress(address, port))
            soTimeout = 4
        } catch (e: IOException) {
            close()
            throw e
        }
    }
    val port: Int = socket.localPort
    private data class Sample(val input: ReceiverInput, val receivedAt: Long)
    private val sample = AtomicReference<Sample?>(null)
    private val clients = mutableMapOf<SocketAddress, Long>()
    private var counter = 0
    private val worker = Thread(::serve, "pepomote-dsu-direct").apply { isDaemon = true; start() }

    fun input(packet: ByteArray) {
        val input = ReceiverProtocol.parseInput(packet) ?: return
        sample.set(Sample(input, System.nanoTime()))
    }

    private fun serve() {
        val bytes = ByteArray(129)
        val packet = DatagramPacket(bytes, bytes.size)
        var lastOutput = 0L
        var lastReplyWindow = 0L
        var replies = 0
        try {
            while (!socket.isClosed) {
                packet.length = bytes.size
                val received = try {
                    socket.receive(packet)
                    true
                } catch (_: SocketTimeoutException) {
                    false
                }
                val now = System.nanoTime()
                clients.entries.removeAll { it.value <= now }
                if (now - lastReplyWindow >= SECOND) {
                    replies = 0
                    lastReplyWindow = now
                }
                if (received && replies < 160 && sameSubnet(socket.localAddress, packet.address, prefix)) {
                    val request = DsuCodec.parseRequest(bytes.copyOf(packet.length))
                    if (request != null) {
                        replies++
                        when (request.type) {
                            DsuCodec.VERSION -> send(packet.socketAddress, DsuCodec.version())
                            DsuCodec.PORT_INFO -> {
                                val live = sample.get()?.takeIf { now - it.receivedAt < SECOND }
                                for (slot in request.ports) {
                                    send(packet.socketAddress, DsuCodec.portInfo(slot, slot == 0 && live != null,
                                        if (slot == 0) live?.input?.batteryPct ?: 0 else 0))
                                }
                            }
                            DsuCodec.PAD_DATA -> {
                                if (request.slots and 1 != 0 && (clients.size < 8 || packet.socketAddress in clients)) {
                                    clients[packet.socketAddress] = now + 5 * SECOND
                                }
                            }
                        }
                    }
                }
                val live = sample.get()
                val fresh = live != null && now - live.receivedAt < 250_000_000L
                val interval = if (fresh) 4_000_000L else 500_000_000L
                if (clients.isNotEmpty() && now - lastOutput >= interval) {
                    lastOutput = now
                    val data = if (fresh && live != null) live.input else neutral(now)
                    val connected = live != null && now - live.receivedAt < SECOND
                    val wire = DsuCodec.padData(0, data, ReceiverMode.Eden, connected, false, ++counter, switchMotion)
                    for (client in clients.keys) send(client, wire)
                }
            }
        } catch (e: IOException) {
            if (!socket.isClosed) onError(e)
        } finally {
            socket.close()
        }
    }

    private fun send(target: SocketAddress, bytes: ByteArray) {
        try {
            socket.send(DatagramPacket(bytes, bytes.size, target))
        } catch (_: SocketException) {
            // Un cliente de una red que se acaba de ir no impide atender a los demás.
        }
    }

    override fun close() {
        socket.close()
        if (Thread.currentThread() !== worker) worker.join(300)
    }

    companion object {
        const val PORT = 26760
        private const val SECOND = 1_000_000_000L

        fun sameSubnet(local: InetAddress, remote: InetAddress, prefix: Int): Boolean {
            if (local !is Inet4Address || remote !is Inet4Address || prefix !in 1..32) return false
            val full = prefix / 8
            val partial = prefix % 8
            val a = local.address
            val b = remote.address
            for (i in 0 until full) if (a[i] != b[i]) return false
            return partial == 0 || ((a[full].toInt() xor b[full].toInt()) and (255 shl (8 - partial))) == 0
        }

        private fun neutral(now: Long) =
            ReceiverInput(0, 0, now / 1000, 0f, 0f, 0f, 0f, 0f, 0f, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}
