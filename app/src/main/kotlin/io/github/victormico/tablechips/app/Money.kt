package io.github.victormico.tablechips.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.victormico.tablechips.core.GameMode
import io.github.victormico.tablechips.core.LogEntry
import io.github.victormico.tablechips.core.Player
import io.github.victormico.tablechips.core.Pot
import io.github.victormico.tablechips.core.TableState
import io.github.victormico.tablechips.app.ui.chips

/**
 * The words the table uses, resolved for the reader.
 *
 * Log lines and refusals travel as codes precisely so that each device can say
 * them in its own language and in the vocabulary of the game being played: the
 * pot of a casino is the "poso" at canari.
 */

/** Turns a protocol error code into something a person can act on. */
@Composable
fun errorText(code: String): String = stringResource(
    when (code) {
        "unknown_player" -> R.string.error_unknown_player
        "unknown_pot" -> R.string.error_unknown_pot
        "name_required" -> R.string.error_name_required
        "name_too_long" -> R.string.error_name_too_long
        "already_seated" -> R.string.error_already_seated
        "not_seated" -> R.string.error_not_seated
        "seat_taken" -> R.string.error_seat_taken
        "seat_out_of_range" -> R.string.error_seat_out_of_range
        "table_full" -> R.string.error_table_full
        "invalid_amount" -> R.string.error_invalid_amount
        "invalid_target" -> R.string.error_invalid_target
        "insufficient_chips" -> R.string.error_insufficient_chips
        "pot_too_small" -> R.string.error_pot_too_small
        "not_host" -> R.string.error_not_host
        "invalid_config" -> R.string.error_invalid_config
        "nothing_to_undo" -> R.string.error_nothing_to_undo
        "bad_message" -> R.string.error_bad_message
        "unsupported_version" -> R.string.error_unsupported_version
        "not_joined" -> R.string.error_not_joined
        "wrong_room" -> R.string.error_wrong_room
        "kicked" -> R.string.error_kicked
        "wrong_mode" -> R.string.error_wrong_mode
        "no_banker" -> R.string.error_no_banker
        "banker_cannot_bet" -> R.string.error_banker_cannot_bet
        "no_stake" -> R.string.error_no_stake
        "nothing_to_split" -> R.string.error_nothing_to_split
        "not_enough_players" -> R.string.error_not_enough_players
        "table_closed" -> R.string.error_table_closed
        "seat_transferred" -> R.string.error_seat_transferred
        else -> R.string.error_no_table
    },
)

/** A pot's own name, or the one this language gives the main pot. */
@Composable
fun potName(pot: Pot): String = when {
    pot.name != null -> pot.name!!
    pot.id.value == "main" -> stringResource(R.string.pot_main)
    pot.id.value.startsWith("side-") ->
        stringResource(R.string.poker_side_pot, pot.id.value.removePrefix("side-"))
    else -> pot.id.value
}

/** What this table calls the game it is playing. */
@Composable
fun modeName(mode: GameMode): String = stringResource(
    when (mode) {
        GameMode.MANUAL -> R.string.mode_manual
        GameMode.SEVEN_HALF -> R.string.mode_seven_half
        GameMode.BLACKJACK -> R.string.mode_blackjack
        GameMode.POKER -> R.string.mode_poker
    },
)

/**
 * What a hand that beats the bank outright is called here. The word is the
 * game's, and it is the one on the button the host presses.
 */
@Composable
fun naturalName(mode: GameMode): String = stringResource(
    when (mode) {
        GameMode.BLACKJACK -> R.string.bank_natural_blackjack
        GameMode.SEVEN_HALF -> R.string.bank_natural_seven_half
        else -> R.string.bank_natural
    },
)

/** One line of the activity log, with the actor in front of it. */
@Composable
fun logLine(entry: LogEntry, table: TableState): String {
    val who = entry.actorName
        ?: entry.actor?.let { id -> table.players.firstOrNull { it.id == id } }?.name
    val amount = entry.args["amount"]?.toLongOrNull()?.let { chips(it) } ?: ""
    val buyIn = entry.args["buyIn"]?.toLongOrNull()?.let { chips(it) } ?: ""
    val pot = entry.args["pot"]?.let { id ->
        table.pots.firstOrNull { it.id.value == id }?.let { potNameOf(it) } ?: id
    } ?: ""
    val text = when (entry.key) {
        "log.table_opened" -> stringResource(R.string.log_table_opened, entry.args["room"].orEmpty())
        "log.config_changed" -> stringResource(R.string.log_config_changed, buyIn)
        "log.player_joined" -> stringResource(R.string.log_player_joined)
        "log.player_renamed" -> stringResource(R.string.log_player_renamed, entry.args["name"].orEmpty())
        "log.player_sat" -> stringResource(R.string.log_player_sat, entry.args["seat"].orEmpty(), amount)
        "log.player_sat_back" -> stringResource(R.string.log_player_sat_back, entry.args["seat"].orEmpty())
        "log.player_stood_up" -> stringResource(R.string.log_player_stood_up)
        "log.player_left" -> stringResource(R.string.log_player_left)
        "log.rebuy" -> stringResource(R.string.log_rebuy, amount)
        "log.bet" -> stringResource(R.string.log_bet, amount, pot)
        "log.pot_awarded" -> stringResource(R.string.log_pot_awarded, amount, pot)
        "log.pot_created" -> stringResource(R.string.log_pot_created, pot)
        "log.transfer" -> stringResource(R.string.log_transfer, amount)
        "log.stack_added" -> stringResource(R.string.log_stack_added, amount)
        "log.stack_removed" -> stringResource(R.string.log_stack_removed, amount)
        "log.player_kicked" -> stringResource(R.string.log_player_kicked)
        "log.banker_changed" -> stringResource(R.string.log_banker_changed)
        "log.banker_cleared" -> stringResource(R.string.log_banker_cleared)
        "log.stake_placed" -> stringResource(R.string.log_stake_placed, amount)
        "log.stake_returned" -> stringResource(R.string.log_stake_returned, amount)
        "log.hand_won" -> stringResource(R.string.log_hand_won, amount)
        "log.hand_lost" -> stringResource(R.string.log_hand_lost, amount)
        "log.hand_push" -> stringResource(R.string.log_hand_push)
        "log.hand_natural" -> stringResource(R.string.log_hand_natural, amount)
        "log.hand_started" -> stringResource(R.string.log_hand_started, entry.args["seat"].orEmpty())
        "log.player_folded" -> stringResource(R.string.log_player_folded)
        "log.round_closed" -> stringResource(R.string.log_round_closed)
        "log.pots_split" -> stringResource(R.string.log_pots_split, entry.args["count"].orEmpty())
        "log.seat_transferred" -> stringResource(R.string.log_seat_transferred)
        else -> entry.key
    }
    return if (who == null) text else "$who $text"
}

@Composable
private fun potNameOf(pot: Pot): String = potName(pot)

/** How many chips a log line moved, from the table's point of view. */
fun logDelta(entry: LogEntry): Long {
    val amount = entry.args["amount"]?.toLongOrNull() ?: return 0
    return when (entry.key) {
        "log.bet", "log.stack_removed", "log.transfer", "log.stake_placed", "log.hand_lost" -> -amount
        "log.rebuy", "log.pot_awarded", "log.stack_added", "log.stake_returned",
        "log.hand_won", "log.hand_natural",
        -> amount
        else -> 0
    }
}

val Player.net: Long get() = stack - boughtIn
