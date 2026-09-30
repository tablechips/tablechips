package io.github.victormico.tablechips.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Upper bound on seats at a table. Ten is a hard product requirement, not a hint. */
const val MAX_SEATS: Int = 10

/** Blackjack: how many hands one stake may be split into. */
const val MAX_HANDS: Int = 4

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
 * The games the app knows how to count chips for. [MANUAL] is the fallback for
 * everything else: a pot, bets into it, and a host who says who takes it.
 *
 * What separates the others is not the cards — the app never sees a card — but
 * the shape of the money. In a bank game every player plays alone against one
 * banker; in poker everybody plays into one pot and somebody may be all-in for
 * less than the rest.
 */
@Serializable
enum class GameMode {
    @SerialName("manual")
    MANUAL,

    @SerialName("seven_half")
    SEVEN_HALF,

    @SerialName("blackjack")
    BLACKJACK,

    @SerialName("poker")
    POKER,
    ;

    /** One banker against each player, one hand at a time. */
    val isBankGame: Boolean get() = this == SEVEN_HALF || this == BLACKJACK
}

/**
 * What the bank pays for a hand that wins more than even money — a blackjack,
 * a set i mig — as a plain ratio, because house rules differ and guessing is
 * worse than asking. Rounding is always down, in the player's disfavour, which
 * is what a table does with an odd chip.
 */
@Serializable
data class Payout(val numerator: Int = 3, val denominator: Int = 2) {
    fun of(amount: Long): Long = amount * numerator / denominator

    val valid: Boolean get() = numerator in 1..10 && denominator in 1..10
}

@Serializable
data class TableConfig(
    val mode: GameMode = GameMode.MANUAL,
    val seatCount: Int = MAX_SEATS,
    /** Five of each chip, as a real table deals. */
    val defaultBuyIn: Long = STANDARD_BUY_IN,
    /** Bank games: what a natural pays. Blackjack is 3:2, set i mig usually 2:1. */
    val naturalPays: Payout = Payout(),
    /** Poker: posted at the start of every hand. Zero means no blinds. */
    val smallBlind: Long = 0,
    val bigBlind: Long = 0,
    /**
     * Set i mig, a house rule: whoever makes an exact set i mig takes the bank
     * once the hand has been settled.
     */
    val naturalTakesBank: Boolean = false,
)

/** What a hand of a bank game ended up being worth to the player. */
@Serializable
enum class HandOutcome {
    @SerialName("win")
    WIN,

    @SerialName("lose")
    LOSE,

    /** Nobody wins: the stake goes back untouched. */
    @SerialName("push")
    PUSH,

    /** A blackjack, a set i mig: paid at [TableConfig.naturalPays]. */
    @SerialName("natural")
    NATURAL,

    /** Blackjack: the player gives the hand up and gets half the stake back. */
    @SerialName("surrender")
    SURRENDER,
}

/** How one hand of a bank game ended, kept on show until the player stakes again. */
@Serializable
data class SettledHand(val outcome: HandOutcome, val delta: Long)

@Serializable
data class Player(
    val id: PlayerId,
    val name: String,
    /** Seat index in `0 until config.seatCount`, or null for someone watching. */
    val seat: Int? = null,
    /**
     * The seat this player last stood up from. A seat is a place at a real
     * table — it decides the order of play and where the dealer button goes —
     * so sitting back down returns the player to it while it is still free.
     */
    val lastSeat: Int? = null,
    val stack: Long = 0,
    /** Everything this player has put on the table: first buy-in plus rebuys. */
    val boughtIn: Long = 0,
    val isHost: Boolean = false,
    /**
     * Bank games: chips put up for the hand being played. They are out of the
     * stack and not yet anybody's, exactly like chips in front of a player at a
     * real table, so they count as being on the table.
     */
    val stake: Long = 0,
    /**
     * Bank games: the stake as the hands it is played in, which is one until a
     * blackjack hand is split. Always adds up to [stake].
     */
    val hands: List<Long> = emptyList(),
    /** Bank games: what this player staked last time, to stake it again in one tap. */
    val lastStake: Long = 0,
    /** Bank games: how this player's hands ended, until they stake again. */
    val settled: List<SettledHand> = emptyList(),
    /**
     * Poker: everything this player has put into the pot during the current
     * hand. Side pots are built from these, so it is what makes an all-in for
     * less than the others come out right.
     */
    val committed: Long = 0,
    /** Poker: put in during the current betting round. What a call has to match. */
    val roundBet: Long = 0,
    /** Poker: out of the hand, still at the table. */
    val folded: Boolean = false,
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
    /**
     * Poker side pots: who is allowed to win this one. Null means everybody,
     * which is every pot in every other mode.
     */
    val eligible: List<PlayerId>? = null,
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
    /**
     * Bank games: who is holding the bank, if anybody is. At blackjack this is
     * whoever deals for the house: the chips are the house's, not theirs.
     */
    val banker: PlayerId? = null,
    /**
     * Blackjack: the house's chips. The house plays against everybody and sits
     * nowhere; it is bought into like a player, and pays and collects every
     * hand. Kept when the game changes, because chips are never dropped.
     */
    val house: Long = 0,
    /** Set i mig with [TableConfig.naturalTakesBank]: who takes the bank once the hand is settled. */
    val pendingBanker: PlayerId? = null,
    /** Poker: the seat with the dealer button, or null before the first hand. */
    val button: Int? = null,
    /** Poker: the highest [Player.roundBet] of the current round; what a call matches. */
    val currentBet: Long = 0,
    /** Number of ledger entries applied. Also the length of the undo stack. */
    val rev: Long = 0,
    val log: List<LogEntry> = emptyList(),
) {
    fun player(id: PlayerId): Player? = players.firstOrNull { it.id == id }

    fun playerAtSeat(seat: Int): Player? = players.firstOrNull { it.seat == seat }

    fun pot(id: PotId): Pot? = pots.firstOrNull { it.id == id }

    val freeSeats: List<Int>
        get() = (0 until config.seatCount).filter { seat -> playerAtSeat(seat) == null }

    /**
     * Chips in play: every stack, every stake waiting to be settled, and every
     * pot. Must always equal what was bought in.
     */
    val chipsOnTable: Long
        get() = players.sumOf { it.stack + it.stake } + pots.sumOf { it.amount } + house

    /**
     * Bank games: the chips the bank pays from and collects into — the house's
     * at blackjack, the banker's stack at set i mig. Null with no bank to pay.
     */
    val bankFunds: Long?
        get() = if (config.mode == GameMode.BLACKJACK) house else bankerPlayer?.stack

    /** Bank games: the player holding the bank, if they are still at the table. */
    val bankerPlayer: Player?
        get() = banker?.let { player(it) }

    /** Poker: what [player] would have to put in to call. */
    fun toCall(id: PlayerId): Long {
        val player = player(id) ?: return 0
        return (currentBet - player.roundBet).coerceAtLeast(0)
    }

    /** What the players still at the table have put in. */
    val totalBoughtIn: Long
        get() = players.sumOf { it.boughtIn }

    /** The accounting identity. False here means the ledger has a bug. */
    val balanced: Boolean
        get() = chipsOnTable == bank.boughtIn - bank.cashedOut + bank.adjusted
}
