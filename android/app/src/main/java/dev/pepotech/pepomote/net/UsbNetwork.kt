package dev.pepotech.pepomote.net

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

internal data class UsbNetwork(val address: Inet4Address, val broadcast: Inet4Address, val prefix: Int) {
    fun reaches(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val remote = parts.map { it.toIntOrNull()?.takeIf { number -> number in 0..255 } ?: return false }
        val local = address.address
        for (index in 0..3) {
            val bits = (prefix - index * 8).coerceIn(0, 8)
            val mask = 255 shl (8 - bits)
            if ((((local[index].toInt() and 255) xor remote[index]) and mask) != 0) return false
        }
        return true
    }

    companion object {
        // Interfaces de la conexión USB compartida de Android (RNDIS / NCM).
        private val usbName = Regex("(?:rndis|usb|ncm)[0-9_.-]*", RegexOption.IGNORE_CASE)

        fun active(): List<UsbNetwork> = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && usbName.matches(it.name) }
                .flatMap { network ->
                    network.interfaceAddresses.mapNotNull {
                        val local = it.address as? Inet4Address ?: return@mapNotNull null
                        val broadcast = it.broadcast as? Inet4Address ?: return@mapNotNull null
                        val prefix = it.networkPrefixLength.toInt().takeIf { bits -> bits in 1..30 } ?: return@mapNotNull null
                        UsbNetwork(local, broadcast, prefix)
                    }
                }
        }.getOrDefault(emptyList())

        fun sourceFor(host: String): InetAddress? = active().firstOrNull { it.reaches(host) }?.address
    }
}
