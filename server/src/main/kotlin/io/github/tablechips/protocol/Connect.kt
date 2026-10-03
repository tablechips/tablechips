package io.github.tablechips.protocol

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession

/**
 * Opens WebSockets to a table over plain ws, which is all an offline hotspot
 * can offer and all this needs: a page served over http may open one to the
 * same address with no certificate and no secure context.
 */
class TableConnection(private val url: String) : AutoCloseable {

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            // Phones sleep and screens turn off; pings keep an idle socket alive.
            pingIntervalMillis = 15_000
        }
    }

    suspend fun open(): Transport = WebSocketTransport(client.webSocketSession(url))

    override fun close() {
        client.close()
    }

    companion object {
        /** The scheme a scanned code uses to reach the app. */
        const val SCHEME = "tablechips"

        /** The address of a table, from whatever the host read out or the QR carried. */
        fun webSocketUrl(host: String, port: Int): String = "ws://$host:$port/ws"

        /** What a person types: an address, maybe with a port, maybe with http:// */
        fun parse(typed: String, defaultPort: Int): String? {
            val cleaned = typed.trim().removePrefix("http://").removePrefix("https://")
                .trimEnd('/')
                .substringBefore('/')
            if (cleaned.isEmpty()) return null
            val host = cleaned.substringBefore(':')
            val port = cleaned.substringAfter(':', "").toIntOrNull() ?: defaultPort
            if (host.isEmpty() || port !in 1..65535) return null
            return webSocketUrl(host, port)
        }
    }
}

/**
 * A table somebody pointed us at: by scanning a code, by following a link, or
 * by typing an address out loud across a table.
 *
 * Whatever the source, this is untrusted input — a QR carries whatever whoever
 * printed it decided — so the app shows the address before connecting to it.
 */
data class TableLink(val host: String, val port: Int, val room: String? = null) {
    val address: String get() = "$host:$port"
    val webSocketUrl: String get() = TableConnection.webSocketUrl(host, port)
}

/**
 * Reads the three shapes a table's address arrives in: the custom scheme an
 * intent carries, the http link a camera reads off a code, and the bare
 * address a person types.
 */
fun parseTableLink(text: String, defaultPort: Int): TableLink? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val room = Regex("[?&]room=([^&#]+)").find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    if (trimmed.startsWith("${TableConnection.SCHEME}://")) {
        val host = Regex("[?&]host=([^&#]+)").find(trimmed)?.groupValues?.get(1) ?: return null
        val port = Regex("[?&]port=([0-9]+)").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
            ?: defaultPort
        return TableLink(host, port, room).takeIf { it.port in 1..65535 && host.isNotBlank() }
    }

    val authority = trimmed
        .removePrefix("http://").removePrefix("https://")
        .substringBefore('/')
        .substringBefore('?')
    if (authority.isEmpty()) return null
    val host = authority.substringBefore(':')
    val port = authority.substringAfter(':', "").toIntOrNull() ?: defaultPort
    if (host.isEmpty() || port !in 1..65535) return null
    return TableLink(host, port, room)
}
