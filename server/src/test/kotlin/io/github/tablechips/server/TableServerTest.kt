package io.github.tablechips.server

import io.github.tablechips.core.PlayerId
import io.github.tablechips.core.Table
import io.github.tablechips.core.TableConfig
import io.github.tablechips.protocol.Join
import io.github.tablechips.protocol.ProtocolJson
import io.github.tablechips.protocol.ServerMessage
import io.github.tablechips.protocol.Sit
import io.github.tablechips.protocol.StateMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The phase 1 acceptance criterion, as a test: a client that is not this app
 * reaches the server over plain http and keeps a plain ws socket open to it.
 */
class TableServerTest {

    private lateinit var tableHost: TableHost
    private lateinit var server: TableServer
    private var port: Int = 0

    private val client = HttpClient(CIO) { install(WebSockets) }

    @BeforeTest
    fun start() = runBlocking {
        tableHost = TableHost(
            table = Table("ABCD", TableConfig(defaultBuyIn = 100), clock = { 0 }),
            newId = { PlayerId("fixed-id") },
        )
        // Port 0 lets the operating system choose, so tests never collide.
        server = TableServer(tableHost, preferredPort = 0)
        port = server.start()
        Unit
    }

    @AfterTest
    fun stop() {
        client.close()
        server.stop()
    }

    @Test
    fun `health answers over plain http`() = runBlocking {
        val response = client.get("http://127.0.0.1:$port/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"protocol\":1"), body)
        assertTrue(body.contains("\"room\":\"ABCD\""), body)
    }

    @Test
    fun `the web client is served from the root`() = runBlocking {
        val body = client.get("http://127.0.0.1:$port/").bodyAsText()

        assertTrue(body.contains("<title>TableChips</title>"), body.take(200))
        // No remote asset may sneak in: the hotspot has no internet.
        assertFalse(body.contains("https://"), "the page must not load anything from outside")
    }

    @Test
    fun `the websocket echo answers a page served in the clear`() = runBlocking {
        withTimeout(10_000) {
            client.webSocket("ws://127.0.0.1:$port/echo") {
                send(Frame.Text("ping"))
                val reply = incoming.receive() as Frame.Text
                assertEquals("ping", reply.readText())
            }
        }
    }

    @Test
    fun `a real socket plays the protocol end to end`() = runBlocking {
        withTimeout(10_000) {
            client.webSocket("ws://127.0.0.1:$port/ws") {
                send(Frame.Text(ProtocolJson.encodeToString(Join(name = "Anna") as io.github.tablechips.protocol.ClientMessage)))
                val joined = receiveMessage()
                assertIs<StateMessage>(joined)
                assertEquals(PlayerId("fixed-id"), joined.you)

                send(Frame.Text(ProtocolJson.encodeToString(Sit(seat = 2) as io.github.tablechips.protocol.ClientMessage)))
                val seated = receiveMessage()
                assertIs<StateMessage>(seated)
                assertEquals(2, seated.state.player(PlayerId("fixed-id"))!!.seat)
                assertEquals(100, seated.state.player(PlayerId("fixed-id"))!!.stack)
            }
        }
    }

    @Test
    fun `the server reports the port it really bound`() {
        assertTrue(port > 0)
        assertEquals(port, server.port)
    }

    private suspend fun io.ktor.websocket.WebSocketSession.receiveMessage(): ServerMessage {
        val frame = incoming.receive() as Frame.Text
        return ProtocolJson.decodeFromString(frame.readText())
    }
}

private fun assertFalse(condition: Boolean, message: String) = assertTrue(!condition, message)
