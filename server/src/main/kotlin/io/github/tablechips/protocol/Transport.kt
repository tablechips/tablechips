package io.github.tablechips.protocol

/**
 * A two-way channel of protocol frames. Everything above this interface ignores
 * what is underneath: today a WebSocket, in tests a pair of channels.
 *
 * No online mode is planned, but the seam costs nothing and keeps the door open.
 */
interface Transport {
    /** Sends one frame. Failures mean the peer is gone; the caller drops it. */
    suspend fun send(text: String)

    /**
     * Delivers incoming frames to [handler] and suspends until the peer goes
     * away or the connection is closed.
     */
    suspend fun onMessage(handler: suspend (String) -> Unit)
}
