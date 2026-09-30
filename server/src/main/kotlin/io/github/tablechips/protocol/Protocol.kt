package io.github.tablechips.protocol

import io.github.tablechips.core.MAIN_POT
import io.github.tablechips.core.PlayerId
import io.github.tablechips.core.PotId
import io.github.tablechips.core.HandOutcome
import io.github.tablechips.core.RuleError
import io.github.tablechips.core.TableConfig
import io.github.tablechips.core.TableState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Version carried by every frame. Bump it whenever an old client would
 * misunderstand a new server: guests keep the web client the host serves, but
 * the app on someone's phone can be any age.
 */
const val PROTOCOL_VERSION: Int = 1

/** The one JSON configuration both ends use. */
val ProtocolJson: Json = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

// ---------------------------------------------------------------- client -> server

@Serializable
sealed interface ClientMessage {
    val v: Int
}

/**
 * First frame of every connection. A client that already has a [playerId] sends
 * it to get its seat back after a reconnection; one that does not gets a fresh
 * id back in the first state frame.
 */
@Serializable
@SerialName("join")
data class Join(
    val name: String,
    val playerId: PlayerId? = null,
    /** Room code read off the QR, when there is one. Checked, not trusted. */
    val room: String? = null,
    override val v: Int = PROTOCOL_VERSION,
) : ClientMessage

@Serializable
@SerialName("sit")
data class Sit(
    val seat: Int? = null,
    val buyIn: Long? = null,
    override val v: Int = PROTOCOL_VERSION,
) : ClientMessage

@Serializable
@SerialName("leave")
data class Leave(override val v: Int = PROTOCOL_VERSION) : ClientMessage

@Serializable
@SerialName("action")
data class Action(
    val action: PlayerAction,
    override val v: Int = PROTOCOL_VERSION,
) : ClientMessage

@Serializable
@SerialName("hostCommand")
data class HostCommandMessage(
    val command: HostCommand,
    override val v: Int = PROTOCOL_VERSION,
) : ClientMessage

/**
 * What a player may do on their own behalf. There is no actor field on the
 * wire: the server takes it from the connection, so nobody can act as somebody
 * else by editing a frame.
 */
@Serializable
sealed interface PlayerAction

@Serializable
@SerialName("bet")
data class BetAction(val amount: Long, val pot: PotId = MAIN_POT) : PlayerAction

@Serializable
@SerialName("rebuy")
data class RebuyAction(val amount: Long) : PlayerAction

@Serializable
@SerialName("transfer")
data class TransferAction(val to: PlayerId, val amount: Long) : PlayerAction

@Serializable
@SerialName("stand_up")
data object StandUpAction : PlayerAction

@Serializable
@SerialName("rename")
data class RenameAction(val name: String) : PlayerAction

/** Bank games: chips up for the hand about to be dealt. */
@Serializable
@SerialName("stake")
data class StakeAction(val amount: Long) : PlayerAction

/** Bank games: take the bank for yourself, while nobody holds it. */
@Serializable
@SerialName("take_bank")
data object TakeBankAction : PlayerAction

/** Blackjack: the same chips again on one of your hands. */
@Serializable
@SerialName("double")
data class DoubleAction(val hand: Int = 0) : PlayerAction

/** Blackjack: split one of your hands in two, the new one staked like the first. */
@Serializable
@SerialName("split")
data class SplitAction(val hand: Int = 0) : PlayerAction

/** Bank games: take an unsettled stake back. */
@Serializable
@SerialName("cancel_stake")
data object CancelStakeAction : PlayerAction

/** Poker: out of this hand. */
@Serializable
@SerialName("fold")
data object FoldAction : PlayerAction

/** Only accepted from the player the ledger marks as host. */
@Serializable
sealed interface HostCommand

@Serializable
@SerialName("award_pot")
data class AwardPotCommand(
    val to: PlayerId,
    val pot: PotId = MAIN_POT,
    val amount: Long? = null,
) : HostCommand

/** A tie: the pot shared evenly between the winners. */
@Serializable
@SerialName("share_pot")
data class SharePotCommand(
    val winners: List<PlayerId>,
    val pot: PotId = MAIN_POT,
) : HostCommand

@Serializable
@SerialName("adjust_stack")
data class AdjustStackCommand(val player: PlayerId, val delta: Long) : HostCommand

@Serializable
@SerialName("create_pot")
data class CreatePotCommand(val name: String? = null) : HostCommand

@Serializable
@SerialName("set_config")
data class SetConfigCommand(val config: TableConfig) : HostCommand

@Serializable
@SerialName("kick")
data class KickCommand(val player: PlayerId) : HostCommand

/** Bank games. A null player means nobody holds the bank. */
@Serializable
@SerialName("set_banker")
data class SetBankerCommand(val player: PlayerId? = null) : HostCommand

/**
 * Bank games, from the host or whoever holds the bank. [hand] settles one hand
 * of a split blackjack stake; otherwise a null amount settles the whole stake.
 */
@Serializable
@SerialName("settle")
data class SettleCommand(
    val player: PlayerId,
    val outcome: HandOutcome,
    val amount: Long? = null,
    val hand: Int? = null,
) : HostCommand

/** Bank games, from the host or the bank: every hand still waiting ends the same way. */
@Serializable
@SerialName("settle_all")
data class SettleAllCommand(val outcome: HandOutcome) : HostCommand

/** Blackjack, from the host or the dealer: chips into the house. */
@Serializable
@SerialName("fund_house")
data class FundHouseCommand(val amount: Long) : HostCommand

/** Poker: move the button, post the blinds, clear the last hand. */
@Serializable
@SerialName("start_hand")
data object StartHandCommand : HostCommand

/** Poker: the street is over. */
@Serializable
@SerialName("close_round")
data object CloseRoundCommand : HostCommand

/** Poker: cut the pot into the pots that can actually be won. */
@Serializable
@SerialName("split_pots")
data object SplitPotsCommand : HostCommand

@Serializable
@SerialName("transfer_seat")
data class TransferSeatCommand(val from: PlayerId, val to: PlayerId) : HostCommand

@Serializable
@SerialName("undo")
data object UndoCommand : HostCommand

// ---------------------------------------------------------------- server -> client

@Serializable
sealed interface ServerMessage {
    val v: Int
}

/**
 * The whole table, every time. Deltas would buy a few hundred bytes and cost a
 * category of bugs nobody can debug at a card table.
 */
@Serializable
@SerialName("state")
data class StateMessage(
    val state: TableState,
    /** Who the receiver is. A client with no stored id learns it here. */
    val you: PlayerId,
    /** How many moves back the host can still undo. */
    val undoDepth: Int,
    override val v: Int = PROTOCOL_VERSION,
) : ServerMessage

/**
 * A refusal, as a code. The text belongs to the client, which knows the
 * reader's language and the vocabulary of the game being played.
 */
@Serializable
@SerialName("error")
data class ErrorMessage(
    val code: String,
    override val v: Int = PROTOCOL_VERSION,
) : ServerMessage {
    companion object {
        fun of(error: RuleError): ErrorMessage = ErrorMessage(error.wireName)

        fun of(error: ProtocolError): ErrorMessage = ErrorMessage(error.wireName)
    }
}

/** Sent before the server drops a connection on purpose. Used from F6 on. */
@Serializable
@SerialName("kicked")
data class Kicked(
    val reason: String,
    override val v: Int = PROTOCOL_VERSION,
) : ServerMessage

/** Failures of the conversation itself, as opposed to failures of a game rule. */
@Serializable
enum class ProtocolError {
    @SerialName("bad_message") BAD_MESSAGE,
    @SerialName("unsupported_version") UNSUPPORTED_VERSION,
    @SerialName("not_joined") NOT_JOINED,
    @SerialName("wrong_room") WRONG_ROOM,
}

/** The name an enum entry travels under, taken from its own serializer. */
val RuleError.wireName: String
    get() = RuleError.serializer().descriptor.getElementName(ordinal)

val ProtocolError.wireName: String
    get() = ProtocolError.serializer().descriptor.getElementName(ordinal)
