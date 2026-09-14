package io.github.victormico.tablechips.server

import java.net.Inet4Address
import java.net.NetworkInterface

/** One address guests can reach, with the interface it belongs to. */
data class HostAddress(
    val interfaceName: String,
    val address: String,
) {
    fun url(port: Int): String = "http://$address:$port/"
}

/**
 * The hotspot address is not always 192.168.43.1: it depends on the vendor, on
 * whether the phone is tethering over USB, and on Android's own hotspot
 * randomisation. Never hardcode it; enumerate and, when there is more than one,
 * show them all and let the host read out the right one.
 */
fun localIpv4Addresses(
    interfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces().toList(),
): List<HostAddress> =
    interfaces
        .asSequence()
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { networkInterface ->
            networkInterface.inetAddresses.asSequence()
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                .map { HostAddress(networkInterface.name, it.hostAddress ?: "") }
        }
        .filter { it.address.isNotEmpty() }
        .distinct()
        .sortedWith(compareBy({ -hotspotRank(it.interfaceName) }, { it.interfaceName }, { it.address }))
        .toList()

/**
 * How likely an interface is to be the one guests are attached to. Names are a
 * hint, not a guarantee, which is why the screen shows every address it found.
 */
internal fun hotspotRank(interfaceName: String): Int {
    val name = interfaceName.lowercase()
    return when {
        // Android's tethering interfaces, in the order vendors use them.
        name.startsWith("ap") || name.startsWith("swlan") -> 3
        name.startsWith("wlan") || name.startsWith("wl") -> 2
        name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("bt-pan") -> 1
        else -> 0
    }
}

private fun java.util.Enumeration<NetworkInterface>.toList(): List<NetworkInterface> =
    java.util.Collections.list(this)
