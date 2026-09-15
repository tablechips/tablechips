package io.github.victormico.tablechips.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
        else -> R.string.error_no_table
    },
)

/** A pot's own name, or the one this language gives the main pot. */
@Composable
fun potName(pot: Pot): String =
    pot.name ?: if (pot.id.value == "main") stringResource(R.string.pot_main) else pot.id.value

/** One line of the activity log, with the actor in front of it. */
@Composable
fun logLine(entry: LogEntry, table: TableState): String {
    val who = entry.actor?.let { id -> table.players.firstOrNull { it.id == id } }?.name
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
        "log.player_stood_up" -> stringResource(R.string.log_player_stood_up)
        "log.player_left" -> stringResource(R.string.log_player_left)
        "log.rebuy" -> stringResource(R.string.log_rebuy, amount)
        "log.bet" -> stringResource(R.string.log_bet, amount, pot)
        "log.pot_awarded" -> stringResource(R.string.log_pot_awarded, amount, pot)
        "log.pot_created" -> stringResource(R.string.log_pot_created, pot)
        "log.transfer" -> stringResource(R.string.log_transfer, amount)
        "log.stack_added" -> stringResource(R.string.log_stack_added, amount)
        "log.stack_removed" -> stringResource(R.string.log_stack_removed, amount)
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
        "log.bet", "log.stack_removed", "log.transfer" -> -amount
        "log.rebuy", "log.pot_awarded", "log.stack_added" -> amount
        else -> 0
    }
}

val Player.net: Long get() = stack - boughtIn
