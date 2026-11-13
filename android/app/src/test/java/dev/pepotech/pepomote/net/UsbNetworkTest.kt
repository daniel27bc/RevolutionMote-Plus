package dev.pepotech.pepomote.net

import org.junit.Assert.*
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class UsbNetworkTest {
    @Test fun solo_elige_destinos_de_la_subred_del_cable() {
        val local = InetAddress.getByName("192.168.18.1") as Inet4Address
        val broadcast = InetAddress.getByName("192.168.19.255") as Inet4Address
        val network = UsbNetwork(local, broadcast, 23)
        assertTrue(network.reaches("192.168.19.30"))
        assertFalse(network.reaches("192.168.20.30"))
        assertFalse(network.reaches("equipo.local"))
        assertFalse(network.reaches("192.168.18.256"))
    }
}
