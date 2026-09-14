package io.github.victormico.tablechips.server

import io.github.victormico.tablechips.core.AdjustStack
import io.github.victormico.tablechips.core.AwardPot
import io.github.victormico.tablechips.core.CommandResult
import io.github.victormico.tablechips.core.CreatePot
import io.github.victormico.tablechips.core.JoinTable
import io.github.victormico.tablechips.core.LeaveTable
import io.github.victormico.tablechips.core.PlaceBet
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Rebuy
import io.github.victormico.tablechips.core.Rename
import io.github.victormico.tablechips.core.SetConfig
import io.github.victormico.tablechips.core.SitDown
import io.github.victormico.tablechips.core.StandUp
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableCommand
import io.github.victormico.tablechips.core.TransferChips
import io.github.victormico.tablechips.core.UndoLast
import io.github.victormico.tablechips.core.newPlayerId
import io.github.victormico.tablechips.protocol.Action
import io.github.victormico.tablechips.protocol.AdjustStackCommand
import io.github.victormico.tablechips.protocol.AwardPotCommand
import io.github.victormico.tablechips.protocol.BetAction
import io.github.victormico.tablechips.protocol.ClientMessage
import io.github.victormico.tablechips.protocol.CreatePotCommand
import io.github.victormico.tablechips.protocol.ErrorMessage
import io.github.victormico.tablechips.protocol.HostCommandMessage
import io.github.victormico.tablechips.protocol.Join
import io.github.victormico.tablechips.protocol.Leave
import io.github.victormico.tablechips.protocol.PROTOCOL_VERSION
import io.github.victormico.tablechips.protocol.ProtocolError
import io.github.victormico.tablechips.protocol.ProtocolJson
import io.github.victormico.tablechips.protocol.RebuyAction
import io.github.victormico.tablechips.protocol.RenameAction
import io.github.victormico.tablechips.protocol.ServerMessage
import io.github.victormico.tablechips.protocol.SetConfigCommand
import io.github.victormico.tablechips.protocol.Sit
import io.github.victormico.tablechips.protocol.StandUpAction
import io.github.victormico.tablechips.protocol.StateMessage
import io.github.victormico.tablechips.protocol.Transport
import io.github.victormico.tablechips.protocol.TransferAction
import io.github.victormico.tablechips.protocol.UndoCommand
import io.github.victormico.tablechips.core.TableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The authoritative side of the table. It owns the ledger, turns frames into
 * commands, and pushes the whole state to everyone after every change.
 *
 * The host's own player interface talks to this through localhost like anybody
 * else, so there is a single code path and no special case for the host.
 */
class TableHost(
    val table: Table,
    private val newId: () -> PlayerId = { newPlayerId() },
) {
    private val mutex = Mutex()
    private val connections = mutableListOf<Connection>()

    private val _state = MutableStateFlow(table.snapshot())

    /** The same state the clients receive, for whoever is watching locally. */
    val state: StateFlow<TableState> = _state.asStateFlow()

    private class Connection(val transport: Transport) {
        var player: PlayerId? = null
    }

    /** Serves one client until it goes away. */
    suspend fun serve(transport: Transport) {
        val connection = Connection(transport)
        mutex.withLock { connections += connection }
        try {
            transport.onMessage { frame -> handle(connection, frame) }
        } finally {
            disconnect(connection)
        }
    }

    private suspend fun handle(connection: Connection, frame: String) {
        val message = try {
            ProtocolJson.decodeFromString<ClientMessage>(frame)
        } catch (failure: Exception) {
            connection.transport.trySend(ErrorMessage.of(ProtocolError.BAD_MESSAGE))
            return
        }
        if (message.v != PROTOCOL_VERSION) {
            connection.transport.trySend(ErrorMessage.of(ProtocolError.UNSUPPORTED_VERSION))
            return
        }
        if (message is Join) {
            join(connection, message)
            return
        }
        val player = connection.player
        if (player == null) {
            connection.transport.trySend(ErrorMessage.of(ProtocolError.NOT_JOINED))
            return
        }
        val command = message.toCommand(player)
        if (command == null) {
            connection.transport.trySend(ErrorMessage.of(ProtocolError.BAD_MESSAGE))
            return
        }
        execute(connection, command)
    }

    private suspend fun join(connection: Connection, message: Join) {
        val room = message.room
        if (room != null && !room.equals(table.roomCode, ignoreCase = true)) {
            connection.transport.trySend(ErrorMessage.of(ProtocolError.WRONG_ROOM))
            return
        }
        // A client that brings an id keeps it, known to the table or not: that
        // is how a seat comes back after a reconnection. One that brings none
        // gets a fresh id and learns it from the state frame.
        val player = message.playerId ?: newId()
        val broadcast = mutex.withLock {
            when (val result = table.execute(JoinTable(player, message.name))) {
                is CommandResult.Rejected -> {
                    connection.transport.trySend(ErrorMessage.of(result.error))
                    return
                }

                is CommandResult.Accepted -> {
                    connection.player = player
                    table.setConnected(player, true)
                    prepareBroadcast()
                }
            }
        }
        broadcast.deliver()
    }

    private suspend fun execute(connection: Connection, command: TableCommand) {
        val broadcast = mutex.withLock {
            when (val result = table.execute(command)) {
                is CommandResult.Rejected -> {
                    connection.transport.trySend(ErrorMessage.of(result.error))
                    return
                }

                is CommandResult.Accepted -> {
                    // Leaving frees the id: the same device joining again is a
                    // new player, not a ghost holding a seat.
                    if (command is LeaveTable) connection.player = null
                    prepareBroadcast()
                }
            }
        }
        broadcast.deliver()
    }

    private suspend fun disconnect(connection: Connection) {
        val broadcast = mutex.withLock {
            connections -= connection
            val player = connection.player
            // A player with a second device open is still at the table.
            if (player != null && connections.none { it.player == player }) {
                table.setConnected(player, false)
            }
            prepareBroadcast()
        }
        broadcast.deliver()
    }

    /**
     * Builds one frame per connection while holding the lock, so everybody sees
     * the same revision, and sends them outside it, so one slow phone cannot
     * hold up the table.
     */
    private fun prepareBroadcast(): Broadcast {
        val state = table.snapshot()
        val undoDepth = table.undoDepth
        _state.value = state
        val frames = connections.mapNotNull { connection ->
            val player = connection.player ?: return@mapNotNull null
            connection.transport to ProtocolJson.encodeToString<ServerMessage>(
                StateMessage(state = state, you = player, undoDepth = undoDepth),
            )
        }
        return Broadcast(frames)
    }

    private class Broadcast(private val frames: List<Pair<Transport, String>>) {
        suspend fun deliver() {
            frames.forEach { (transport, frame) ->
                try {
                    transport.send(frame)
                } catch (failure: Exception) {
                    // The reader loop of that connection will notice and clean up.
                }
            }
        }
    }

    private suspend fun Transport.trySend(message: ServerMessage) {
        try {
            send(ProtocolJson.encodeToString(message))
        } catch (failure: Exception) {
            // Same as above: a dead connection is not this call's problem.
        }
    }
}

/** Maps a frame to a ledger command, adding the actor the connection is bound to. */
internal fun ClientMessage.toCommand(actor: PlayerId): TableCommand? = when (this) {
    is Join -> null // handled before this point
    is Sit -> SitDown(actor, seat, buyIn)
    is Leave -> LeaveTable(actor)
    is Action -> when (val action = action) {
        is BetAction -> PlaceBet(actor, action.amount, action.pot)
        is RebuyAction -> Rebuy(actor, action.amount)
        is TransferAction -> TransferChips(actor, action.to, action.amount)
        is RenameAction -> Rename(actor, action.name)
        StandUpAction -> StandUp(actor)
    }

    is HostCommandMessage -> when (val command = command) {
        is AwardPotCommand -> AwardPot(actor, command.to, command.pot, command.amount)
        is AdjustStackCommand -> AdjustStack(actor, command.player, command.delta)
        is CreatePotCommand -> CreatePot(actor, command.name)
        is SetConfigCommand -> SetConfig(actor, command.config)
        UndoCommand -> UndoLast(actor)
    }
}
