package io.github.victormico.tablechips.server

import io.github.victormico.tablechips.core.AdjustStack
import io.github.victormico.tablechips.core.CancelStake
import io.github.victormico.tablechips.core.CloseRound
import io.github.victormico.tablechips.core.Fold
import io.github.victormico.tablechips.core.AwardPot
import io.github.victormico.tablechips.core.CommandResult
import io.github.victormico.tablechips.core.CreatePot
import io.github.victormico.tablechips.core.JoinTable
import io.github.victormico.tablechips.core.KickPlayer
import io.github.victormico.tablechips.core.LeaveTable
import io.github.victormico.tablechips.core.PlaceBet
import io.github.victormico.tablechips.core.PlaceStake
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Rebuy
import io.github.victormico.tablechips.core.Rename
import io.github.victormico.tablechips.core.SetBanker
import io.github.victormico.tablechips.core.SetConfig
import io.github.victormico.tablechips.core.SettleHand
import io.github.victormico.tablechips.core.SitDown
import io.github.victormico.tablechips.core.SplitPots
import io.github.victormico.tablechips.core.StandUp
import io.github.victormico.tablechips.core.StartHand
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableCommand
import io.github.victormico.tablechips.core.TransferChips
import io.github.victormico.tablechips.core.TransferSeat
import io.github.victormico.tablechips.core.UndoLast
import io.github.victormico.tablechips.core.newPlayerId
import io.github.victormico.tablechips.protocol.Action
import io.github.victormico.tablechips.protocol.AdjustStackCommand
import io.github.victormico.tablechips.protocol.AwardPotCommand
import io.github.victormico.tablechips.protocol.BetAction
import io.github.victormico.tablechips.protocol.CancelStakeAction
import io.github.victormico.tablechips.protocol.CloseRoundCommand
import io.github.victormico.tablechips.protocol.FoldAction
import io.github.victormico.tablechips.protocol.ClientMessage
import io.github.victormico.tablechips.protocol.CreatePotCommand
import io.github.victormico.tablechips.protocol.ErrorMessage
import io.github.victormico.tablechips.protocol.HostCommandMessage
import io.github.victormico.tablechips.protocol.Join
import io.github.victormico.tablechips.protocol.KickCommand
import io.github.victormico.tablechips.protocol.Kicked
import io.github.victormico.tablechips.protocol.LedgerStore
import io.github.victormico.tablechips.protocol.NoLedgerStore
import io.github.victormico.tablechips.protocol.Leave
import io.github.victormico.tablechips.protocol.PROTOCOL_VERSION
import io.github.victormico.tablechips.protocol.ProtocolError
import io.github.victormico.tablechips.protocol.ProtocolJson
import io.github.victormico.tablechips.protocol.RebuyAction
import io.github.victormico.tablechips.protocol.RenameAction
import io.github.victormico.tablechips.protocol.ServerMessage
import io.github.victormico.tablechips.protocol.SetBankerCommand
import io.github.victormico.tablechips.protocol.SetConfigCommand
import io.github.victormico.tablechips.protocol.SettleCommand
import io.github.victormico.tablechips.protocol.SplitPotsCommand
import io.github.victormico.tablechips.protocol.StakeAction
import io.github.victormico.tablechips.protocol.StartHandCommand
import io.github.victormico.tablechips.protocol.Sit
import io.github.victormico.tablechips.protocol.StandUpAction
import io.github.victormico.tablechips.protocol.StateMessage
import io.github.victormico.tablechips.protocol.Transport
import io.github.victormico.tablechips.protocol.TransferAction
import io.github.victormico.tablechips.protocol.TransferSeatCommand
import io.github.victormico.tablechips.protocol.UndoCommand
import io.github.victormico.tablechips.core.TableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    private val store: LedgerStore = NoLedgerStore,
) {
    private val mutex = Mutex()
    private val connections = mutableListOf<Connection>()

    private val _state = MutableStateFlow(table.snapshot())

    /** The same state the clients receive, for whoever is watching locally. */
    val state: StateFlow<TableState> = _state.asStateFlow()

    private class Connection(val transport: Transport) {
        var player: PlayerId? = null

        /**
         * Frames waiting to go out, in the order they were produced. A phone
         * whose screen is off stops reading, and its queue must not hold up
         * the rest of the table; once it is this far behind, the oldest frames
         * are worth nothing anyway, because each state supersedes the last.
         */
        val outbox = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    /** Serves one client until it goes away. */
    suspend fun serve(transport: Transport): Unit = coroutineScope {
        val connection = Connection(transport)
        mutex.withLock { connections += connection }
        val writer = launch {
            for (frame in connection.outbox) {
                try {
                    transport.send(frame)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    break // The socket is gone; the reader below will clean up.
                }
            }
        }
        try {
            transport.onMessage { frame -> handle(connection, frame) }
        } finally {
            connection.outbox.close()
            writer.cancel()
            disconnect(connection)
        }
    }

    private suspend fun handle(connection: Connection, frame: String) {
        val message = try {
            ProtocolJson.decodeFromString<ClientMessage>(frame)
        } catch (failure: Exception) {
            connection.queue(ErrorMessage.of(ProtocolError.BAD_MESSAGE))
            return
        }
        if (message.v != PROTOCOL_VERSION) {
            connection.queue(ErrorMessage.of(ProtocolError.UNSUPPORTED_VERSION))
            return
        }
        if (message is Join) {
            join(connection, message)
            return
        }
        val player = connection.player
        if (player == null) {
            connection.queue(ErrorMessage.of(ProtocolError.NOT_JOINED))
            return
        }
        val command = message.toCommand(player)
        if (command == null) {
            connection.queue(ErrorMessage.of(ProtocolError.BAD_MESSAGE))
            return
        }
        execute(connection, command)
    }

    private suspend fun join(connection: Connection, message: Join) {
        val room = message.room
        if (room != null && !room.equals(table.roomCode, ignoreCase = true)) {
            connection.queue(ErrorMessage.of(ProtocolError.WRONG_ROOM))
            return
        }
        // A client that brings an id keeps it, known to the table or not: that
        // is how a seat comes back after a reconnection. One that brings none
        // gets a fresh id and learns it from the state frame.
        val player = message.playerId ?: newId()
        mutex.withLock {
            when (val result = table.execute(JoinTable(player, message.name))) {
                is CommandResult.Rejected -> {
                    connection.queue(ErrorMessage.of(result.error))
                    return
                }

                is CommandResult.Accepted -> {
                    connection.player = player
                    table.setConnected(player, true)
                    broadcastLocked()
                }
            }
        }
    }

    private suspend fun execute(connection: Connection, command: TableCommand) {
        mutex.withLock {
            when (val result = table.execute(command)) {
                is CommandResult.Rejected -> {
                    connection.queue(ErrorMessage.of(result.error))
                    return
                }

                is CommandResult.Accepted -> {
                    // Leaving frees the id: the same device joining again is a
                    // new player, not a ghost holding a seat.
                    if (command is LeaveTable) connection.player = null
                    broadcastLocked()
                    // Told after the state, so the screen it lands on is final.
                    when (command) {
                        is KickPlayer -> evictLocked(command.player, "kicked")
                        is TransferSeat -> evictLocked(command.from, "seat_transferred")
                        else -> Unit
                    }
                }
            }
        }
    }

    private suspend fun disconnect(connection: Connection) {
        mutex.withLock {
            connections -= connection
            val player = connection.player
            // A player with a second device open is still at the table.
            if (player != null && connections.none { it.player == player }) {
                table.setConnected(player, false)
            }
            broadcastLocked()
        }
    }

    /**
     * Hangs up on a player the table no longer holds a place for. Already under
     * the lock: the connections are the table's own list.
     */
    private fun evictLocked(player: PlayerId, reason: String) {
        connections.filter { it.player == player }.forEach { connection ->
            connection.player = null
            connection.queue(Kicked(reason = reason))
        }
    }

    /**
     * Tells everybody the table is over before the server goes down under them.
     *
     * Without this a guest's phone just shows "reconnecting" forever and they
     * have no way to know the game finished rather than the wifi dropped.
     */
    suspend fun closeAll(reason: String = "table_closed") {
        mutex.withLock {
            connections.forEach { it.queue(Kicked(reason = reason)) }
        }
    }

    /**
     * Queues the whole state for everyone, while holding the lock.
     *
     * Queuing rather than sending is what keeps the order: two commands landing
     * at once used to each send their own frames outside the lock, so a client
     * could be handed an older revision after a newer one and show a table that
     * had already moved on.
     */
    private fun broadcastLocked() {
        val state = table.snapshot()
        val undoDepth = table.undoDepth
        _state.value = state
        // Written here, still under the lock, because a move the players saw
        // and a move on disk have to be the same move: a phone can be killed
        // between the two, and then the only record left is this file.
        runCatching { store.save(table.ledger()) }
        connections.forEach { connection ->
            val player = connection.player ?: return@forEach
            connection.queue(StateMessage(state = state, you = player, undoDepth = undoDepth))
        }
    }

    private fun Connection.queue(message: ServerMessage) {
        outbox.trySend(ProtocolJson.encodeToString(message))
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
        is StakeAction -> PlaceStake(actor, action.amount)
        CancelStakeAction -> CancelStake(actor)
        FoldAction -> Fold(actor)
        StandUpAction -> StandUp(actor)
    }

    is HostCommandMessage -> when (val command = command) {
        is AwardPotCommand -> AwardPot(actor, command.to, command.pot, command.amount)
        is AdjustStackCommand -> AdjustStack(actor, command.player, command.delta)
        is CreatePotCommand -> CreatePot(actor, command.name)
        is SetConfigCommand -> SetConfig(actor, command.config)
        is KickCommand -> KickPlayer(actor, command.player)
        is SetBankerCommand -> SetBanker(actor, command.player)
        is SettleCommand -> SettleHand(actor, command.player, command.outcome, command.amount)
        StartHandCommand -> StartHand(actor)
        CloseRoundCommand -> CloseRound(actor)
        SplitPotsCommand -> SplitPots(actor)
        is TransferSeatCommand -> TransferSeat(actor, command.from, command.to)
        UndoCommand -> UndoLast(actor)
    }
}
