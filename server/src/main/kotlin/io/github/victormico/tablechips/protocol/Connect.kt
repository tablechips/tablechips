package io.github.victormico.tablechips.protocol

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
