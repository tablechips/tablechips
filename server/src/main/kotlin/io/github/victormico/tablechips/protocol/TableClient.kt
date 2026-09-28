package io.github.victormico.tablechips.protocol

import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.TableState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where the connection to the table stands, as the screen needs to show it. */
enum class Connection { CONNECTING, ONLINE, OFFLINE }

/** Everything a client knows. The table itself is whatever the server last said. */
data class ClientState(
    val connection: Connection = Connection.CONNECTING,
    val table: TableState? = null,
    val you: PlayerId? = null,
    val undoDepth: Int = 0,
    /** Code of the last refusal, for the client to translate. Cleared on the next state. */
    val error: String? = null,
    val kicked: Boolean = false,
) {
    val me get() = you?.let { id -> table?.players?.firstOrNull { it.id == id } }
    val seated get() = me?.seat != null
    val isHost get() = me?.isHost == true
}

/**
 * The player side of the protocol, transport and platform agnostic.
 *
 * It is the same conversation the web client has, written once in Kotlin so
 * the app does not reimplement it: join, keep the id you were given, and get
 * your seat back after every drop. Reconnection is not a luxury — a game lasts
 * hours and screens turn off.
 */
class TableClient(
    private val scope: CoroutineScope,
    private val openTransport: suspend () -> Transport,
    private val retryDelays: List<Long> = listOf(500, 1_000, 2_000, 5_000),
) {
    private val _state = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = _state.asStateFlow()

    private var transport: Transport? = null
    private var loop: Job? = null
    private var hello: Join? = null

    /**
     * Joins as [name], reusing [playerId] when the device has one: that is how
     * a seat comes back. A null id asks the table for a fresh one.
     */
    fun join(name: String, playerId: PlayerId? = null, room: String? = null) {
        hello = Join(name = name, playerId = playerId, room = room)
        if (loop == null) loop = scope.launch { run() }
    }

    /**
     * Changes the name the table shows. The join this client repeats on every
     * reconnection carries the new name too: otherwise the next dropped
     * connection would quietly rename the player back.
     */
    suspend fun rename(name: String) {
        hello = hello?.copy(name = name)
        send(Action(RenameAction(name)))
    }

    suspend fun send(message: ClientMessage) {
        val open = transport ?: return
        try {
            open.send(ProtocolJson.encodeToString(message))
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            // The reader loop will notice the socket is gone and reconnect.
        }
    }

    fun close() {
        loop?.cancel()
        loop = null
        transport = null
    }

    private suspend fun run() {
        var attempt = 0
        while (scope.isActive) {
            _state.update { it.copy(connection = Connection.CONNECTING) }
            try {
                val open = openTransport()
                transport = open
                attempt = 0
                _state.update { it.copy(connection = Connection.ONLINE) }
                hello?.let { open.send(ProtocolJson.encodeToString<ClientMessage>(it)) }
                open.onMessage { frame -> receive(frame) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                // Nothing there yet, or it went away. Both mean: wait and retry.
            }
            transport = null
            if (_state.value.kicked) return
            _state.update { it.copy(connection = Connection.OFFLINE) }
            delay(retryDelays[attempt.coerceAtMost(retryDelays.lastIndex)])
            attempt++
        }
    }

    private fun receive(frame: String) {
        val message = try {
            ProtocolJson.decodeFromString<ServerMessage>(frame)
        } catch (failure: Exception) {
            return
        }
        when (message) {
            is StateMessage -> {
                // Keep the id the table gave us: the next connection uses it.
                hello = hello?.copy(playerId = message.you)
                _state.update {
                    it.copy(
                        table = message.state,
                        you = message.you,
                        undoDepth = message.undoDepth,
                        error = null,
                    )
                }
            }

            is ErrorMessage -> _state.update { it.copy(error = message.code) }

            // Rejoining would only be thrown out again, so the loop stops.
            is Kicked -> _state.update { it.copy(kicked = true, error = "kicked") }
        }
    }
}
