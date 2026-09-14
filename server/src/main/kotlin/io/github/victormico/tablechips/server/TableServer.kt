package io.github.victormico.tablechips.server

import io.github.victormico.tablechips.protocol.PROTOCOL_VERSION
import io.github.victormico.tablechips.protocol.Transport
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.io.IOException
import java.net.ServerSocket

const val DEFAULT_PORT: Int = 8080

/**
 * The embedded server the host phone runs: static client, health check and the
 * WebSocket the whole game goes through.
 *
 * Plain http and ws on purpose. A page served over http may open a ws:// socket
 * to the same address with no certificate and no secure context, which is
 * exactly what an offline hotspot can provide and what WebRTC could not.
 */
class TableServer(
    private val tableHost: TableHost,
    private val preferredPort: Int = DEFAULT_PORT,
    private val portAttempts: Int = 10,
) {
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /** The port actually bound, which is not always the preferred one. */
    var port: Int = -1
        private set

    /** Starts the server and returns the port it ended up on. */
    suspend fun start(): Int {
        check(engine == null) { "already started" }
        val candidate = firstFreePort(preferredPort, portAttempts)
        val server = embeddedServer(CIO, port = candidate, host = "0.0.0.0") {
            module(tableHost)
        }
        engine = server
        server.start(wait = false)
        port = server.engine.resolvedConnectors().first().port
        return port
    }

    fun stop(gracePeriodMillis: Long = 300, timeoutMillis: Long = 1_500) {
        engine?.stop(gracePeriodMillis, timeoutMillis)
        engine = null
        port = -1
    }

    /** Every address a guest could type, best guess first. */
    fun addresses(): List<HostAddress> = localIpv4Addresses()
}

/**
 * Port 8080 is the default and the next free one is used when something else
 * already holds it. Probing and then binding is a small race, but losing it only
 * means the next start picks another port.
 */
internal fun firstFreePort(preferred: Int, attempts: Int): Int {
    for (offset in 0 until attempts) {
        val candidate = preferred + offset
        try {
            ServerSocket(candidate).use { return candidate }
        } catch (_: IOException) {
            // Taken. Try the next one.
        }
    }
    // Let the operating system pick; the caller reads the resolved port back.
    return 0
}

fun Application.module(tableHost: TableHost) {
    install(WebSockets) {
        // Phones sleep and screens turn off during a long game; pings keep the
        // intermediate hops from dropping an idle socket.
        pingPeriodMillis = 15_000
        timeoutMillis = 30_000
    }

    routing {
        get("/health") {
            call.respondText(
                """{"app":"tablechips","protocol":$PROTOCOL_VERSION,"room":"${tableHost.table.roomCode}"}""",
                ContentType.Application.Json,
            )
        }

        /** Connectivity probe: everything it does is send back what it received. */
        webSocket("/echo") {
            for (frame in incoming) {
                if (frame is Frame.Text) send(Frame.Text(frame.readText()))
            }
        }

        /** The game. One socket per client, full state on every change. */
        webSocket("/ws") {
            tableHost.serve(WebSocketTransport(this))
        }

        // The web client, served from the classpath so the same jar works on a
        // desktop JVM and inside the APK.
        staticResources("/", "web") {
            default("index.html")
        }
    }
}

/** Adapts a Ktor WebSocket session to the transport the protocol layer speaks. */
class WebSocketTransport(private val session: WebSocketSession) : Transport {
    override suspend fun send(text: String) {
        session.send(Frame.Text(text))
    }

    override suspend fun onMessage(handler: suspend (String) -> Unit) {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) handler(frame.readText())
            }
        } catch (_: ClosedReceiveChannelException) {
            // The client hung up. Normal end of a connection.
        }
    }
}
