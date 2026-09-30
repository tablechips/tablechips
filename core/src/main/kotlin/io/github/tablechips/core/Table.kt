package io.github.tablechips.core

/**
 * A live table: the chip ledger plus who is currently connected.
 *
 * The accounting state is the fold of the ledger, so undo is a truncation and
 * restoring after the process dies is a replay. Presence is kept apart on
 * purpose: connecting and disconnecting must never land on the undo stack.
 *
 * Not thread-safe. The server owns one table and serializes access to it.
 */
class Table private constructor(
    ledger: List<TableEvent>,
    private val clock: () -> Long,
) {
    private val entries: MutableList<TableEvent> = ledger.toMutableList()
    private val connected: MutableSet<PlayerId> = mutableSetOf()

    /** Accounting state only: [Player.connected] is always false here. */
    private var accounting: TableState = Rules.fold(entries)

    constructor(
        roomCode: String,
        config: TableConfig = TableConfig(),
        clock: () -> Long = { System.currentTimeMillis() },
    ) : this(listOf(TableOpened(roomCode, config, clock())), clock)

    val roomCode: String get() = accounting.roomCode

    /** The full ledger, for persistence. Replay it with [restore]. */
    fun ledger(): List<TableEvent> = entries.toList()

    /** Number of entries that can still be undone. The first one is the opening. */
    val undoDepth: Int get() = (entries.size - 1).coerceAtLeast(0)

    fun execute(command: TableCommand): CommandResult {
        if (command is UndoLast) return undo(command.actor)
        val result = Rules.plan(accounting, command, clock())
        if (result is CommandResult.Accepted) {
            entries += result.events
            accounting = result.state
        }
        return result
    }

    /** Undoes the last ledger entry. Any depth, down to the opening of the table. */
    fun undo(actor: PlayerId): CommandResult {
        if (!Rules.isHost(accounting, actor)) return CommandResult.Rejected(RuleError.NOT_HOST)
        if (undoDepth == 0) return CommandResult.Rejected(RuleError.NOTHING_TO_UNDO)
        entries.removeAt(entries.lastIndex)
        accounting = Rules.fold(entries)
        return CommandResult.Accepted(emptyList(), snapshot())
    }

    fun setConnected(player: PlayerId, isConnected: Boolean) {
        if (isConnected) connected += player else connected -= player
    }

    fun isConnected(player: PlayerId): Boolean = player in connected

    /** The state as clients see it: the ledger fold with presence merged in. */
    fun snapshot(): TableState = accounting.copy(
        players = accounting.players.map { it.copy(connected = it.id in connected) },
    )

    companion object {
        /** Rebuilds a table from a persisted ledger. */
        fun restore(
            ledger: List<TableEvent>,
            clock: () -> Long = { System.currentTimeMillis() },
        ): Table {
            require(ledger.isNotEmpty() && ledger.first() is TableOpened) {
                "a ledger starts with TableOpened"
            }
            return Table(ledger, clock)
        }
    }
}
