package io.github.tablechips.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkAddressesTest {

    @Test
    fun `tethering interfaces are offered before anything else`() {
        val names = listOf("eth0", "wlan0", "ap0", "docker0", "rndis0")

        val ordered = names.sortedByDescending { hotspotRank(it) }

        assertEquals("ap0", ordered.first())
        assertTrue(ordered.indexOf("wlan0") < ordered.indexOf("rndis0"))
        assertTrue(ordered.indexOf("rndis0") < ordered.indexOf("eth0"))
        assertEquals(0, hotspotRank("docker0"))
    }

    @Test
    fun `the url a guest types is built from the address and the port`() {
        assertEquals("http://192.168.43.1:8080/", HostAddress("ap0", "192.168.43.1").url(8080))
    }

    @Test
    fun `the machine running the tests reports usable addresses`() {
        val addresses = localIpv4Addresses()

        // A container may have exactly one; a phone with the hotspot on has two.
        assertTrue(addresses.none { it.address.startsWith("127.") })
        assertTrue(addresses.none { it.address.startsWith("169.254.") })
        assertTrue(addresses.all { it.address.count { c -> c == '.' } == 3 })
    }

    @Test
    fun `a busy port is skipped for the next one`() {
        java.net.ServerSocket(0).use { taken ->
            val port = taken.localPort

            assertEquals(port + 1, firstFreePort(port, attempts = 5))
        }
    }
}
