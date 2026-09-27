package io.github.victormico.tablechips.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Upper bound on seats at a table. Ten is a hard product requirement, not a hint. */
const val MAX_SEATS: Int = 10

/** Identifier a client persists locally (DataStore on the app, localStorage on the web). */
@JvmInline
@Serializable
value class PlayerId(val value: String)

/** Identifier of a pot. The main pot of every table is [MAIN_POT]. */
@JvmInline
@Serializable
value class PotId(val value: String)

val MAIN_POT: PotId = PotId("main")

/**
 * Game modes. Only [MANUAL] is implemented; it is also the fallback for any game
 * the app does not model explicitly.
 */
@Serializable
enum class GameMode {
    @SerialName("manual")
    MANUAL,
}

@Serializable
data class TableConfig(
    val mode: GameMode = GameMode.MANUAL,
    val seatCount: Int = MAX_SEATS,
    val defaultBuyIn: Long = 100,
)

@Serializable
data class Player(
    val id: PlayerId,
    val name: String,
    /** Seat index in `0 until config.seatCount`, or null for someone watching. */
    val seat: Int? = null,
    val stack: Long = 0,
    /** Everything this player has put on the table: first buy-in plus rebuys. */
    val boughtIn: Long = 0,
    val isHost: Boolean = false,
    /**
     * Live connection flag. This is presence, not accounting: the ledger never
     * writes it and undo never restores it. [Table.snapshot] fills it in.
     */
    val connected: Boolean = false,
) {
    val seated: Boolean get() = seat != null

    /** Chips won or lost so far. Negative means the player is down. */
    val net: Long get() = stack - boughtIn
}

/**
 * Chips that have entered or left the table. Together with the stacks and the
 * pots it gives the accounting identity the ledger must always satisfy:
 * `chipsOnTable == boughtIn - cashedOut + adjusted`.
 */
@Serializable
data class Bank(
    val boughtIn: Long = 0,
    val cashedOut: Long = 0,
    val adjusted: Long = 0,
)

@Serializable
data class Pot(
    val id: PotId,
    val amount: Long = 0,
    /** Null means the client shows its own localized name for this pot id. */
    val name: String? = null,
)

/**
 * One line of the activity log. Carries an i18n key and arguments, never a
 * rendered sentence: the host and each guest may be reading in a different
 * language, and the vocabulary changes with the game.
 */
@Serializable
data class LogEntry(
    val seq: Long,
    val at: Long,
    val key: String,
    val actor: PlayerId? = null,
    /**
     * The actor's name as it was when this happened. Carried rather than looked
     * up, because a player can leave the table while their line stays in the
     * log: a nameless line settles no argument.
     */
    val actorName: String? = null,
    val args: Map<String, String> = emptyMap(),
)

/**
 * The whole authoritative state of a table. The server sends this in full on
 * every change: with ten players it is a few kB and it removes a whole class of
 * synchronization bugs.
 */
@Serializable
data class TableState(
    val roomCode: String,
    val config: TableConfig = TableConfig(),
    val players: List<Player> = emptyList(),
    val pots: List<Pot> = listOf(Pot(MAIN_POT)),
    val bank: Bank = Bank(),
    /** Number of ledger entries applied. Also the length of the undo stack. */
    val rev: Long = 0,
    val log: List<LogEntry> = emptyList(),
) {
    fun player(id: PlayerId): Player? = players.firstOrNull { it.id == id }

    fun playerAtSeat(seat: Int): Player? = players.firstOrNull { it.seat == seat }

    fun pot(id: PotId): Pot? = pots.firstOrNull { it.id == id }

    val freeSeats: List<Int>
        get() = (0 until config.seatCount).filter { seat -> playerAtSeat(seat) == null }

    /** Chips in play: every stack plus every pot. Must always equal what was bought in. */
    val chipsOnTable: Long
        get() = players.sumOf { it.stack } + pots.sumOf { it.amount }

    /** What the players still at the table have put in. */
    val totalBoughtIn: Long
        get() = players.sumOf { it.boughtIn }

    /** The accounting identity. False here means the ledger has a bug. */
    val balanced: Boolean
        get() = chipsOnTable == bank.boughtIn - bank.cashedOut + bank.adjusted
}
