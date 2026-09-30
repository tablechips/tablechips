package io.github.tablechips.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Longest player name accepted. Long enough for a nickname, short enough for a seat. */
const val MAX_NAME_LENGTH: Int = 24

/**
 * What a client asks for. The server decides; the client computes nothing that
 * matters. Every command carries the id of whoever sent it, which the server
 * takes from the connection and never from the payload.
 */
@Serializable
sealed interface TableCommand {
    val actor: PlayerId
}

@Serializable
@SerialName("join")
data class JoinTable(override val actor: PlayerId, val name: String) : TableCommand

@Serializable
@SerialName("rename")
data class Rename(override val actor: PlayerId, val name: String) : TableCommand

/** Null seat means "the first free one", null buy-in means the table default. */
@Serializable
@SerialName("sit")
data class SitDown(
    override val actor: PlayerId,
    val seat: Int? = null,
    val buyIn: Long? = null,
) : TableCommand

@Serializable
@SerialName("stand_up")
data class StandUp(override val actor: PlayerId) : TableCommand

@Serializable
@SerialName("leave")
data class LeaveTable(override val actor: PlayerId) : TableCommand

@Serializable
@SerialName("bet")
data class PlaceBet(
    override val actor: PlayerId,
    val amount: Long,
    val pot: PotId = MAIN_POT,
) : TableCommand

@Serializable
@SerialName("rebuy")
data class Rebuy(override val actor: PlayerId, val amount: Long) : TableCommand

@Serializable
@SerialName("transfer")
data class TransferChips(
    override val actor: PlayerId,
    val to: PlayerId,
    val amount: Long,
) : TableCommand

/** Host only. Null amount awards the whole pot. */
@Serializable
@SerialName("award_pot")
data class AwardPot(
    override val actor: PlayerId,
    val to: PlayerId,
    val pot: PotId = MAIN_POT,
    val amount: Long? = null,
) : TableCommand

/**
 * Host only. A tie: the pot shared evenly between [winners]. Chips that do not
 * divide go one each to the winners nearest the dealer's left, as at a real
 * table.
 */
@Serializable
@SerialName("share_pot")
data class SharePot(
    override val actor: PlayerId,
    val winners: List<PlayerId>,
    val pot: PotId = MAIN_POT,
) : TableCommand

@Serializable
@SerialName("create_pot")
data class CreatePot(override val actor: PlayerId, val name: String? = null) : TableCommand

/** Host only. The manual escape hatch for anything the app got wrong. */
@Serializable
@SerialName("adjust_stack")
data class AdjustStack(
    override val actor: PlayerId,
    val player: PlayerId,
    val delta: Long,
) : TableCommand

@Serializable
@SerialName("set_config")
data class SetConfig(override val actor: PlayerId, val config: TableConfig) : TableCommand

/** Host only. Closes somebody's seat and takes their chips out of the game. */
@Serializable
@SerialName("kick")
data class KickPlayer(override val actor: PlayerId, val player: PlayerId) : TableCommand

/**
 * Host only. Hands a seat to somebody who is at the table without one.
 *
 * This is the manual way back for a player whose phone lost its id — private
 * browsing, a cleared browser, a new device mid-game. They join again as
 * themselves, the host taps their name on the empty-looking seat, and their
 * chips are theirs again.
 */
@Serializable
@SerialName("transfer_seat")
data class TransferSeat(
    override val actor: PlayerId,
    val from: PlayerId,
    val to: PlayerId,
) : TableCommand

/** Host only. Undoes the last ledger entry; there is no depth limit. */
@Serializable
@SerialName("undo")
data class UndoLast(override val actor: PlayerId) : TableCommand

/**
 * Why a command was refused. These travel to the client as codes and are
 * translated there: the host and the guests may not share a language.
 */
@Serializable
enum class RuleError {
    @SerialName("unknown_player") UNKNOWN_PLAYER,
    @SerialName("unknown_pot") UNKNOWN_POT,
    @SerialName("name_required") NAME_REQUIRED,
    @SerialName("name_too_long") NAME_TOO_LONG,
    @SerialName("already_seated") ALREADY_SEATED,
    @SerialName("not_seated") NOT_SEATED,
    @SerialName("seat_taken") SEAT_TAKEN,
    @SerialName("seat_out_of_range") SEAT_OUT_OF_RANGE,
    @SerialName("table_full") TABLE_FULL,
    @SerialName("invalid_amount") INVALID_AMOUNT,
    @SerialName("invalid_target") INVALID_TARGET,
    @SerialName("insufficient_chips") INSUFFICIENT_CHIPS,
    @SerialName("pot_too_small") POT_TOO_SMALL,
    @SerialName("not_host") NOT_HOST,
    @SerialName("invalid_config") INVALID_CONFIG,
    @SerialName("nothing_to_undo") NOTHING_TO_UNDO,
    @SerialName("not_yourself") NOT_YOURSELF,
    @SerialName("seat_not_free") SEAT_NOT_FREE,
    @SerialName("wrong_mode") WRONG_MODE,
    @SerialName("no_banker") NO_BANKER,
    @SerialName("banker_cannot_bet") BANKER_CANNOT_BET,
    @SerialName("no_stake") NO_STAKE,
    @SerialName("nothing_to_split") NOTHING_TO_SPLIT,
    @SerialName("not_enough_players") NOT_ENOUGH_PLAYERS,
    @SerialName("below_call") BELOW_CALL,
    @SerialName("bank_cannot_pay") BANK_CANNOT_PAY,
    @SerialName("no_such_hand") NO_SUCH_HAND,
}

sealed interface CommandResult {
    data class Accepted(val events: List<TableEvent>, val state: TableState) : CommandResult

    data class Rejected(val error: RuleError) : CommandResult
}

/** Host only. Hands the bank to a seated player, or to nobody with a null player. */
@Serializable
@SerialName("set_banker")
data class SetBanker(override val actor: PlayerId, val player: PlayerId?) : TableCommand

/** Bank games. Chips up for the hand about to be dealt. */
@Serializable
@SerialName("stake")
data class PlaceStake(override val actor: PlayerId, val amount: Long) : TableCommand

/** Bank games. Takes back a stake that has not been settled yet. */
@Serializable
@SerialName("cancel_stake")
data class CancelStake(override val actor: PlayerId) : TableCommand

/**
 * The host or whoever holds the bank. Resolves one player's hand against it.
 *
 * [hand] settles one of the hands a blackjack stake was split into. Without
 * it, a null [amount] settles the whole stake, and an amount settles that part
 * of it.
 */
@Serializable
@SerialName("settle")
data class SettleHand(
    override val actor: PlayerId,
    val player: PlayerId,
    val outcome: HandOutcome,
    val amount: Long? = null,
    val hand: Int? = null,
) : TableCommand

/**
 * The host or whoever holds the bank. Every hand still waiting ends the same
 * way: the bank went bust and pays everybody left, or beat them all. Hands that
 * ended otherwise are settled one by one first.
 */
@Serializable
@SerialName("settle_all")
data class SettleAll(override val actor: PlayerId, val outcome: HandOutcome) : TableCommand

/** Blackjack: chips into the house, by the host or whoever deals for it. */
@Serializable
@SerialName("fund_house")
data class FundHouse(override val actor: PlayerId, val amount: Long) : TableCommand

/** Blackjack: the same chips again on one of the player's hands. */
@Serializable
@SerialName("double_stake")
data class DoubleStake(override val actor: PlayerId, val hand: Int = 0) : TableCommand

/** Blackjack: one hand split in two, the new one staked like the first. */
@Serializable
@SerialName("split_stake")
data class SplitStake(override val actor: PlayerId, val hand: Int = 0) : TableCommand

/** Host only. Poker: moves the button, posts the blinds, clears the last hand. */
@Serializable
@SerialName("start_hand")
data class StartHand(override val actor: PlayerId) : TableCommand

/** Poker. Out of the hand; whatever is already in the pot stays there. */
@Serializable
@SerialName("fold")
data class Fold(override val actor: PlayerId) : TableCommand

/** Host only. Poker: the street is over, so what has been matched is settled. */
@Serializable
@SerialName("close_round")
data class CloseRound(override val actor: PlayerId) : TableCommand

/**
 * Host only. Poker: splits the pot into main and side pots from what each
 * player put in, so an all-in for less than the others can only win its share.
 */
@Serializable
@SerialName("split_pots")
data class SplitPots(override val actor: PlayerId) : TableCommand
