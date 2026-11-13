package dev.pepotech.pepomote.server.core

import dev.pepotech.pepomote.net.PmpCodec
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class DirectDsuTest {
    private val local = InetAddress.getByName("127.0.0.1") as Inet4Address

    @Test fun suscripcion_dsu_entrega_sensores_del_mando_local() {
        DirectDsu(local, prefix = 32, port = 0).use { server ->
            DatagramSocket(0, local).use { client ->
                client.soTimeout = 2000
                val sample = PmpCodec.encodeInput(
                    sessionId = 0, seq = 1, tSensorUs = 123_456,
                    quat = floatArrayOf(1f, 0f, 0f, 0f), gyro = floatArrayOf(1f, 0f, 0f),
                    accel = floatArrayOf(0f, 9.80665f, 0f), buttons = 1,
                    recenterCount = 0, batteryPct = 100, touchScrollDy = 0, flags = 0
                )
                server.input(sample)
                val request = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(0x43555344).putShort(1001).putShort(12).putInt(0).putInt(42)
                    .putInt(DsuCodec.PAD_DATA).putLong(0)
                request.putInt(8, CRC32().apply { update(request.array()) }.value.toInt())
                client.send(DatagramPacket(request.array(), request.capacity(), local, server.port))
                val reply = DatagramPacket(ByteArray(128), 128)
                client.receive(reply)
                assertEquals(100, reply.length)
                val data = ByteBuffer.wrap(reply.data).order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(0x53555344, data.getInt(0))
                assertEquals(123_456L, data.getLong(68))
                assertEquals(57.29578f, data.getFloat(88), 0.0001f)
            }
        }
    }

    @Test fun comprueba_la_subred_incluso_si_el_prefijo_no_termina_en_un_octeto() {
        val local = InetAddress.getByName("192.168.18.17")
        assertTrue(DirectDsu.sameSubnet(local, InetAddress.getByName("192.168.19.12"), 23))
        assertFalse(DirectDsu.sameSubnet(local, InetAddress.getByName("192.168.20.12"), 23))
        assertFalse(DirectDsu.sameSubnet(local, InetAddress.getByName("192.168.19.12"), 0))
    }
}
