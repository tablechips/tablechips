package io.github.tablechips.server

import io.github.tablechips.protocol.ClientMessage
import io.github.tablechips.protocol.ProtocolJson
import io.github.tablechips.protocol.ServerMessage
import io.github.tablechips.protocol.StateMessage
import io.github.tablechips.protocol.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.test.assertIs

/**
 * A transport with no sockets under it. It exists to prove the point of the
 * interface: the protocol layer is testable without any network at all.
 */
class TestTransport : Transport {
    private val inbound = Channel<String>(Channel.UNLIMITED)
    val received = mutableListOf<String>()

    override suspend fun send(text: String) {
        received += text
    }

    override suspend fun onMessage(handler: suspend (String) -> Unit) {
        for (frame in inbound) handler(frame)
    }

    suspend fun sendToServer(message: ClientMessage) {
        inbound.send(ProtocolJson.encodeToString(message))
    }

    suspend fun sendRaw(frame: String) {
        inbound.send(frame)
    }

    fun close() {
        inbound.close()
    }

    fun messages(): List<ServerMessage> = received.map { ProtocolJson.decodeFromString(it) }

    fun lastState(): StateMessage {
        val last = messages().last()
        assertIs<StateMessage>(last, "expected a state frame, got $last")
        return last
    }

    fun lastMessage(): ServerMessage = messages().last()
}

/** Attaches a transport to the host and returns the job serving it. */
fun CoroutineScope.connect(host: TableHost, transport: TestTransport): Job =
    launch { host.serve(transport) }
