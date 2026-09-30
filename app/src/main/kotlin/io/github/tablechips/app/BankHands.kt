package io.github.tablechips.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tablechips.app.ui.Caption
import io.github.tablechips.app.ui.Card
import io.github.tablechips.app.ui.Note
import io.github.tablechips.app.ui.Refugi
import io.github.tablechips.app.ui.SecondaryButton
import io.github.tablechips.app.ui.TcText
import io.github.tablechips.app.ui.Type
import io.github.tablechips.app.ui.chips
import io.github.tablechips.core.GameMode
import io.github.tablechips.core.HandOutcome
import io.github.tablechips.core.Player
import io.github.tablechips.core.TableState
import io.github.tablechips.protocol.ClientState

/**
 * What a hand of the bank's is worth to the player, as the rules will pay it:
 * checked here first so a bank that cannot pay is told how short it is before
 * anything is sent.
 */
fun payout(table: TableState, outcome: HandOutcome, stake: Long): Long = when (outcome) {
    HandOutcome.WIN -> stake
    HandOutcome.LOSE -> -stake
    HandOutcome.PUSH -> 0
    HandOutcome.NATURAL -> table.config.naturalPays.of(stake)
    HandOutcome.SURRENDER -> -(stake - stake / 2)
}

/**
 * The bank's side of the hand, on the table, for whoever holds the bank and
 * for the host: every stake still waiting with the ways it can end, the hands
 * already settled with how they ended, and the two outcomes that can apply to
 * everybody at once. The app never sees the cards: this is somebody saying
 * what happened.
 */
@Composable
fun BankHands(
    state: ClientState,
    onSettle: (Player, HandOutcome, Int?) -> Unit,
    onSettleAll: (HandOutcome) -> Unit,
    onFundHouse: (Long) -> Unit,
    onTopUp: (Long) -> Unit,
) {
    val table = state.table ?: return
    val mode = table.config.mode
    val funds = table.bankFunds ?: 0
    val topUp = table.config.defaultBuyIn.coerceAtLeast(1)
    var short by remember { mutableStateOf<Long?>(null) }

    fun settle(player: Player, outcome: HandOutcome, hand: Int?) {
        val stake = if (hand == null) player.stake else player.hands[hand]
        val need = payout(table, outcome, stake) - funds
        if (need > 0) short = need else { short = null; onSettle(player, outcome, hand) }
    }

    val bySeat = compareBy<Player> { it.seat ?: Int.MAX_VALUE }
    val waiting = table.players.filter { it.stake > 0 && it.id != table.banker }.sortedWith(bySeat)
    val done = table.players
        .filter { it.stake == 0L && it.settled.isNotEmpty() && it.id != table.banker }
        .sortedWith(bySeat)

    short?.let { need ->
        val amount = maxOf(topUp, need)
        Note(stringResource(R.string.bank_short, chips(need)))
        when {
            mode == GameMode.BLACKJACK -> SecondaryButton(
                stringResource(R.string.bank_fund, chips(amount)),
                { short = null; onFundHouse(amount) },
                Modifier.fillMaxWidth(),
                height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
            )
            table.banker == state.you -> SecondaryButton(
                stringResource(R.string.bank_top_up, chips(amount)),
                { short = null; onTopUp(amount) },
                Modifier.fillMaxWidth(),
                height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
            )
            else -> TcText(
                stringResource(R.string.bank_short_other, table.bankerPlayer?.name.orEmpty()),
                Type.body,
                color = Refugi.text2,
            )
        }
    }
    if (short == null && mode == GameMode.BLACKJACK && table.house == 0L) {
        SecondaryButton(
            stringResource(R.string.bank_fund, chips(topUp)),
            { onFundHouse(topUp) },
            Modifier.fillMaxWidth(),
            height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
        )
    }

    if (waiting.isNotEmpty() || done.isNotEmpty()) Caption(stringResource(R.string.bank_hands))
    waiting.forEach { player -> WaitingHand(player, mode, ::settle) }
    // Settled hands stay where they were, with how they ended, until the player
    // stakes again: nothing slides under a finger about to tap.
    done.forEach { player -> SettledHandCard(player, mode) }

    if (waiting.size >= 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SecondaryButton(
                stringResource(R.string.bank_bust),
                {
                    val need = waiting.sumOf { payout(table, HandOutcome.WIN, it.stake) } - funds
                    if (need > 0) short = need else { short = null; onSettleAll(HandOutcome.WIN) }
                },
                Modifier.weight(1f),
                height = 52.dp, style = Type.secondary.copy(fontSize = 13.sp),
            )
            SecondaryButton(
                stringResource(R.string.bank_sweep),
                { short = null; onSettleAll(HandOutcome.LOSE) },
                Modifier.weight(1f),
                danger = true,
                height = 52.dp, style = Type.secondary.copy(fontSize = 13.sp),
            )
        }
        TcText(stringResource(R.string.bank_rest_hint), Type.body.copy(fontSize = 13.sp), color = Refugi.text2)
    }
}

/** One stake waiting to be settled: a row of outcomes for each hand it is played in. */
@Composable
private fun WaitingHand(player: Player, mode: GameMode, onSettle: (Player, HandOutcome, Int?) -> Unit) {
    val hands = player.hands.ifEmpty { listOf(player.stake) }
    val outcomes = buildList {
        add(HandOutcome.WIN to stringResource(R.string.bank_win))
        add(HandOutcome.NATURAL to naturalName(mode))
        add(HandOutcome.PUSH to stringResource(R.string.bank_push))
        add(HandOutcome.LOSE to stringResource(R.string.bank_lose))
        if (mode == GameMode.BLACKJACK) add(HandOutcome.SURRENDER to stringResource(R.string.bank_surrender))
    }
    Card(Modifier.fillMaxWidth().testTag("hand:" + player.name), padding = 0.dp, radius = 11.dp, border = Refugi.lineAccent) {
        Column(Modifier.fillMaxWidth().padding(12.dp, 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hands.forEachIndexed { index, amount ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TcText(if (index == 0) player.name else "", Type.nameStrong, maxLines = 1)
                    TcText(
                        (if (hands.size > 1) stringResource(R.string.bank_hand, chips(index + 1L)) + " · " else "") +
                            chips(amount),
                        Type.chips.copy(fontSize = 14.sp),
                        color = Refugi.accent,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    outcomes.forEach { (outcome, label) ->
                        SecondaryButton(
                            label,
                            { onSettle(player, outcome, if (hands.size > 1) index else null) },
                            Modifier.weight(1f),
                            height = 44.dp,
                            danger = outcome == HandOutcome.LOSE || outcome == HandOutcome.SURRENDER,
                            style = Type.secondary.copy(fontSize = if (outcomes.size > 4) 11.sp else 13.sp),
                        )
                    }
                }
            }
        }
    }
}

/** A settled stake, with how each of its hands ended. */
@Composable
private fun SettledHandCard(player: Player, mode: GameMode) {
    val labels = mapOf(
        HandOutcome.WIN to stringResource(R.string.bank_win),
        HandOutcome.NATURAL to naturalName(mode),
        HandOutcome.PUSH to stringResource(R.string.bank_push),
        HandOutcome.LOSE to stringResource(R.string.bank_lose),
        HandOutcome.SURRENDER to stringResource(R.string.bank_surrender),
    )
    Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp, background = Refugi.bg) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp, 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TcText(player.name, Type.nameStrong, maxLines = 1, color = Refugi.text2)
            TcText(
                player.settled.joinToString(" · ") { labels.getValue(it.outcome) + " " + signedChips(it.delta) },
                Type.chips.copy(fontSize = 14.sp),
                color = Refugi.text2,
            )
        }
    }
}

/** A change in chips with its sign, and ±0 for nothing. */
fun signedChips(delta: Long): String = when {
    delta > 0 -> "+" + chips(delta)
    delta < 0 -> "−" + chips(-delta)
    else -> "±0"
}
