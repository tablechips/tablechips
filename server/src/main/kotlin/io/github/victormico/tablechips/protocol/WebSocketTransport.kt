package io.github.victormico.tablechips.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException

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
