package io.github.victormico.tablechips.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.victormico.tablechips.app.ui.Caption
import io.github.victormico.tablechips.app.ui.Card
import io.github.victormico.tablechips.app.ui.ClickableSurface
import io.github.victormico.tablechips.app.ui.Frame
import io.github.victormico.tablechips.app.ui.HelpButton
import io.github.victormico.tablechips.app.ui.Mark
import io.github.victormico.tablechips.app.ui.MenuButton
import io.github.victormico.tablechips.app.ui.Note
import io.github.victormico.tablechips.app.ui.PlayerRow
import io.github.victormico.tablechips.app.ui.PrimaryButton
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips
import io.github.victormico.tablechips.core.GameMode
import io.github.victormico.tablechips.core.HandOutcome
import io.github.victormico.tablechips.core.Player
import io.github.victormico.tablechips.core.chipsIn
import io.github.victormico.tablechips.core.Pot
import io.github.victormico.tablechips.core.PotId
import io.github.victormico.tablechips.protocol.ClientState
import io.github.victormico.tablechips.protocol.Connection

/**
 * The screen a player lives on: what you have, what is in the middle, who else
 * is here, and the two or three things you can do about it.
 */
@Composable
fun TableScreen(
    state: ClientState,
    selectedPot: PotId,
    onSelectPot: (PotId) -> Unit,
    undoable: String?,
    onUndo: () -> Unit,
    onMenu: () -> Unit,
    onRules: () -> Unit = {},
    onBet: () -> Unit,
    onRebuy: () -> Unit,
    /** Sits down with the table's buy-in, or back down with the chips kept. */
    onSit: () -> Unit,
    /** Sits down with an amount other than the table's buy-in. */
    onSitOther: () -> Unit = {},
    onStake: () -> Unit = {},
    /** Bank games: the same stake as last time, in one tap. */
    onStakeAgain: (Long) -> Unit = {},
    onCancelStake: () -> Unit = {},
    onTakeBank: () -> Unit = {},
    /** Blackjack: the same chips again on one hand, or that hand split in two. */
    onDouble: (Int) -> Unit = {},
    onSplit: (Int) -> Unit = {},
    /** The bank, or the host: how a hand ended, or how every hand left ended. */
    onSettle: (Player, HandOutcome, Int?) -> Unit = { _, _, _ -> },
    onSettleAll: (HandOutcome) -> Unit = {},
    onFundHouse: (Long) -> Unit = {},
    onTopUp: (Long) -> Unit = {},
    onCall: (Long) -> Unit = {},
    onRaise: (Long) -> Unit = {},
    onFold: () -> Unit = {},
    /** Host, poker: the street is over. */
    onCloseRound: () -> Unit = {},
    /** Host, poker: say who won. Opens the host panel at the pot. */
    onAwardPot: () -> Unit = {},
    /** Host, poker: deal a hand when none is being played. */
    onNewHand: () -> Unit = {},
) {
    val table = state.table ?: return
    val me = state.me

    Frame(
        header = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Refugi.side, 14.dp, Refugi.side, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Mark(18.dp)
                TcText(stringResource(R.string.table_name, table.roomCode), Type.title)
                Box(Modifier.weight(1f))
                if (state.isHost) {
                    TcText(stringResource(R.string.host_role).uppercase(), Type.caption, color = Refugi.gain)
                }
                HelpButton(stringResource(R.string.rules_title), onRules)
                MenuButton(onMenu)
            }
        },
        actions = {
            if (state.seated) {
                when {
                    table.config.mode.isBankGame -> BankActions(
                        state, onStake, onStakeAgain, onCancelStake, onTakeBank, onDouble, onSplit,
                    )
                    table.config.mode == GameMode.POKER -> PokerActions(state, onBet, onCall, onRaise, onFold)
                    else -> PrimaryButton(stringResource(R.string.action_bet), onBet)
                }
                // Standing up is in the menu: next to call and fold, one stray
                // tap would take a player out of the hand and off the table.
                SecondaryButton(
                    stringResource(R.string.action_rebuy), onRebuy, Modifier.fillMaxWidth(),
                )
            } else {
                // One tap sits down with what the table deals; anything else is
                // the exception, and one tap further.
                val back = (me?.stack ?: 0L) > 0
                PrimaryButton(
                    label = stringResource(if (back) R.string.action_sit_back else R.string.sit_confirm),
                    value = chips(if (back) me!!.stack else table.config.defaultBuyIn),
                    onClick = onSit,
                )
                if (!back) {
                    SecondaryButton(
                        stringResource(R.string.action_sit_other), onSitOther, Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        if (state.connection != Connection.ONLINE) Note(stringResource(R.string.net_offline))

        if (me != null && state.seated) {
            Card(Modifier.fillMaxWidth(), padding = 0.dp) {
                Column(Modifier.padding(20.dp, 18.dp, 20.dp, 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Caption(stringResource(R.string.player_stack))
                        if (me.net != 0L) {
                            TcText(
                                (if (me.net > 0) "+" else "") + chips(me.net),
                                Type.chips.copy(fontSize = 13.sp),
                                color = if (me.net > 0) Refugi.gain else Refugi.loss,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Figure(chips(me.stack), Type.stack, modifier = Modifier.testTag("stack-figure"))
                    Spacer(Modifier.height(14.dp))
                    ChipStacks(me.stack)
                }
            }
        }

        // A game with a bank has no pot at all: what is on the table is what
        // you have up for this hand, and who you are playing it against.
        if (table.config.mode.isBankGame) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A bank game has no pot, but chips left in one from another
                // game are still chips: never hide them.
                if (table.pots.sumOf { it.amount } > 0) {
                    Box(Modifier.weight(1f)) {
                        PotCard(table.pots.first(), stringResource(R.string.amount_pot), false) {}
                    }
                }
                if (me != null && state.seated && table.banker != me.id) {
                    Box(Modifier.weight(1f)) {
                        MetricCard(
                            label = stringResource(R.string.bank_your_stake),
                            value = chips(me.stake),
                            selected = me.stake > 0,
                        )
                    }
                }
                // What the bank pays from: the house's chips at blackjack, the
                // banker's stack at set i mig. The number the table watches.
                val banker = table.bankerPlayer
                Box(Modifier.weight(1f)) {
                    when {
                        table.config.mode == GameMode.BLACKJACK ->
                            MetricCard(stringResource(R.string.bank_house), chips(table.house))
                        banker != null ->
                            MetricCard(stringResource(R.string.bank_holder, banker.name), chips(banker.stack))
                        else -> MetricCard(
                            label = stringResource(R.string.bank_title),
                            value = stringResource(R.string.bank_none),
                            small = true,
                        )
                    }
                }
            }
        } else if (table.pots.size == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    PotCard(table.pots.first(), stringResource(R.string.amount_pot), false) {}
                }
                val owed = me?.let { table.toCall(it.id) } ?: 0
                if (table.config.mode == GameMode.POKER && state.seated && owed > 0) {
                    Box(Modifier.weight(1f)) {
                        MetricCard(stringResource(R.string.poker_to_call), chips(owed), selected = true)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                table.pots.forEach { pot ->
                    Box(Modifier.weight(1f)) {
                        PotCard(pot, potName(pot), pot.id == selectedPot) { onSelectPot(pot.id) }
                    }
                }
            }
        }

        if (state.isHost && table.config.mode == GameMode.POKER) {
            HostHand(state, onCloseRound, onAwardPot, onNewHand)
        }

        val runsBank = state.isHost || (table.banker != null && table.banker == state.you)
        if (table.config.mode.isBankGame && runsBank &&
            (table.config.mode == GameMode.BLACKJACK || table.banker != null)
        ) {
            BankHands(state, onSettle, onSettleAll, onFundHouse, onTopUp)
        }

        if (state.isHost && undoable != null && state.undoDepth > 0) {
            UndoCard(undoable, stringResource(R.string.undo_always), onUndo)
        }

        val seated = table.players.filter { it.seat != null }.sortedBy { it.seat }
        // At a poker table what matters about the others is what they have
        // pushed forward this round, not what they have behind: that is the
        // number the row shows, in the brass of chips in play.
        val poker = table.config.mode == GameMode.POKER
        val bank = table.config.mode.isBankGame
        Row(verticalAlignment = Alignment.Bottom) {
            Caption(stringResource(R.string.player_at_table))
            Box(Modifier.weight(1f))
            if (poker || bank) {
                Caption(stringResource(R.string.poker_bet_column))
            } else {
                TcText(chips(seated.size.toLong()), Type.caption, color = Refugi.text2)
            }
        }
        seated.forEach { player ->
            PlayerRow(
                name = player.name,
                // Against the bank, what matters is what each player has up
                // and, once settled, what their hand came to.
                chips = when {
                    bank -> when {
                        player.id == table.banker -> "\u2014"
                        player.stake > 0 -> player.hands.ifEmpty { listOf(player.stake) }.joinToString(" + ") { chips(it) }
                        player.settled.isNotEmpty() -> signedChips(player.settled.sumOf { it.delta })
                        else -> "\u2014"
                    }
                    !poker -> chips(player.stack)
                    player.roundBet > 0 -> chips(player.roundBet)
                    else -> "\u2014"
                },
                chipsColor = when {
                    bank -> {
                        val result = player.settled.sumOf { it.delta }
                        when {
                            player.id == table.banker -> Refugi.text2
                            player.stake > 0 -> Refugi.accent
                            player.settled.isEmpty() || result == 0L -> Refugi.text2
                            result > 0 -> Refugi.gain
                            else -> Refugi.loss
                        }
                    }
                    poker && player.roundBet > 0 -> Refugi.accent
                    poker -> Refugi.text2
                    else -> Refugi.text
                },
                dot = when {
                    player.id == state.you -> Refugi.accent
                    player.connected -> Refugi.gain
                    else -> Refugi.line
                },
                strong = player.id == state.you,
                dim = !player.connected || (poker && player.folded),
                tags = buildList {
                    if (player.isHost) add(stringResource(R.string.host_role))
                    if (table.banker == player.id) {
                        add(
                            stringResource(
                                if (table.config.mode == GameMode.BLACKJACK) R.string.poker_button else R.string.bank_title,
                            ),
                        )
                    }
                    if (table.config.mode == GameMode.POKER) {
                        if (table.button == player.seat) add(stringResource(R.string.poker_button))
                        if (player.folded) add(stringResource(R.string.poker_folded_tag))
                        else if (player.stack == 0L && player.committed > 0) {
                            add(stringResource(R.string.poker_all_in))
                        }
                    }
                    if (!player.connected) add(stringResource(R.string.player_gone))
                },
            )
        }
        val free = table.config.seatCount - seated.size
        if (free > 0) {
            TcText(
                pluralStringResource(R.plurals.table_free_seats_n, free, chips(free.toLong())),
                Type.body,
                color = Refugi.text2,
            )
        }
        table.players.filter { it.seat == null }.forEach { player ->
            PlayerRow(
                name = player.name,
                chips = "",
                dot = if (player.id == state.you) Refugi.accent else Refugi.line,
                dim = !player.connected,
                tags = listOf(stringResource(R.string.player_watching)),
            )
        }
    }
}

/**
 * Against the bank: put chips up — the same as last time in one tap — and at
 * blackjack double or split them. The bank itself only waits: it settles the
 * hands from the table.
 */
@Composable
private fun BankActions(
    state: ClientState,
    onStake: () -> Unit,
    onStakeAgain: (Long) -> Unit,
    onCancel: () -> Unit,
    onTakeBank: () -> Unit,
    onDouble: (Int) -> Unit,
    onSplit: (Int) -> Unit,
) {
    val table = state.table ?: return
    val me = state.me ?: return
    val blackjack = table.config.mode == GameMode.BLACKJACK
    when {
        table.banker == me.id ->
            Note(stringResource(if (blackjack) R.string.bank_dealing else R.string.bank_you_sub))
        // Nothing can be staked until somebody banks — at blackjack, deals for
        // the house — and anybody seated may.
        table.banker == null ->
            PrimaryButton(stringResource(if (blackjack) R.string.bank_deal else R.string.bank_take), onTakeBank)
        me.stake > 0 -> {
            if (blackjack) {
                val hands = me.hands.ifEmpty { listOf(me.stake) }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (hands.size == 1) {
                        SecondaryButton(
                            stringResource(R.string.bank_double), { onDouble(0) }, Modifier.weight(1f),
                            enabled = me.stack >= hands[0],
                        )
                        SecondaryButton(
                            stringResource(R.string.bank_split), { onSplit(0) }, Modifier.weight(1f),
                            enabled = me.stack >= hands[0],
                        )
                    } else {
                        hands.forEachIndexed { index, amount ->
                            SecondaryButton(
                                stringResource(R.string.bank_double_hand, chips(index + 1L)),
                                { onDouble(index) },
                                Modifier.weight(1f),
                                enabled = me.stack >= amount,
                                style = Type.secondary.copy(fontSize = 13.sp),
                            )
                        }
                    }
                }
            } else {
                PrimaryButton(stringResource(R.string.bank_stake), onStake)
            }
            SecondaryButton(
                label = stringResource(R.string.bank_cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                warn = true,
            )
        }
        else -> {
            // Most hands are staked like the last one: that is one tap.
            val again = me.lastStake in 1..me.stack
            if (again) {
                PrimaryButton(
                    label = stringResource(R.string.bank_stake_again),
                    value = chips(me.lastStake),
                    onClick = { onStakeAgain(me.lastStake) },
                )
                SecondaryButton(stringResource(R.string.action_sit_other), onStake, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(stringResource(R.string.bank_stake), onStake)
            }
        }
    }
}

/**
 * Call, raise, fold. What a call costs is the number the table argues about, so
 * it is written on the button.
 */
@Composable
private fun PokerActions(
    state: ClientState,
    onBet: () -> Unit,
    onCall: (Long) -> Unit,
    onRaise: (Long) -> Unit,
    onFold: () -> Unit,
) {
    val table = state.table ?: return
    val me = state.me ?: return
    if (me.folded) {
        Note(stringResource(R.string.poker_folded))
        return
    }
    val owed = minOf(table.toCall(me.id), me.stack)
    if (owed > 0) {
        PrimaryButton(
            label = stringResource(R.string.poker_call, chips(owed)),
            onClick = { onCall(owed) },
            enabled = me.stack > 0,
        )
    } else {
        PrimaryButton(stringResource(R.string.poker_bet), onBet)
    }
    // With nothing to call there is nothing to raise either: the big button is
    // already the bet, and offering the same thing twice only confuses. Nor is
    // there a raise for somebody whose whole stack does not cover the call.
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (owed > 0 && me.stack > owed) {
            SecondaryButton(
                label = stringResource(R.string.poker_raise),
                onClick = { onRaise(owed) },
                modifier = Modifier.weight(1f),
            )
        }
        SecondaryButton(
            label = stringResource(R.string.poker_fold),
            onClick = onFold,
            modifier = Modifier.weight(1f),
            danger = true,
        )
    }
}

/**
 * What the host does between the players' moves, where the host already is:
 * closing a street, saying who won, dealing when no hand is on. Each shows only
 * when it would do something.
 */
@Composable
private fun HostHand(
    state: ClientState,
    onCloseRound: () -> Unit,
    onAwardPot: () -> Unit,
    onNewHand: () -> Unit,
) {
    val table = state.table ?: return
    val bets = table.players.any { it.roundBet > 0 }
    val pot = table.pots.sumOf { it.amount }
    val canDeal = table.players.count { it.seat != null && it.stack > 0 } >= 2
    if (!bets && pot == 0L && !canDeal) return
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (bets) {
            SecondaryButton(
                stringResource(R.string.poker_close_round), onCloseRound, Modifier.weight(1f),
                height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
            )
        }
        if (pot > 0) {
            SecondaryButton(
                stringResource(R.string.poker_award), onAwardPot, Modifier.weight(1f),
                height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
            )
        } else if (canDeal) {
            SecondaryButton(
                stringResource(R.string.poker_new_hand), onNewHand, Modifier.weight(1f),
                height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
            )
        }
    }
}

/** A labelled number, or a labelled name, in the shape of the pot card. */
@Composable
private fun MetricCard(
    label: String,
    value: String,
    selected: Boolean = false,
    small: Boolean = false,
) {
    Card(
        Modifier.fillMaxWidth(),
        padding = 0.dp,
        radius = 14.dp,
        background = if (selected) Refugi.surfaceHigh else Refugi.surface,
        border = if (selected) Refugi.accent else Refugi.line,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 16.dp)) {
            Caption(label)
            Spacer(Modifier.height(7.dp))
            if (small) {
                TcText(
                    value,
                    Type.nameStrong.copy(fontSize = 19.sp),
                    maxLines = 1,
                    color = if (selected) Refugi.accent else Refugi.text,
                )
            } else {
                Figure(value, Type.metric, color = if (selected) Refugi.accent else Refugi.text)
            }
        }
    }
}

/** A figure is allowed to be enormous, never wider than the phone. */
@Composable
fun Figure(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Refugi.text) {
    val fit = when {
        text.length <= 7 -> 1f
        text.length <= 9 -> .78f
        text.length <= 11 -> .62f
        text.length <= 13 -> .5f
        else -> .42f
    }
    TcText(
        text,
        style.copy(fontSize = style.fontSize * fit, lineHeight = style.lineHeight * fit),
        modifier = modifier,
        color = color,
        maxLines = 1,
    )
}

/** Most chip edges drawn in one stack; the count underneath is the truth. */
private const val MAX_EDGES = 7

/**
 * The stack as chips seen edge on, one column per value, biggest first: the
 * number above it, drawn the way it would sit on the felt after a real deal.
 * Each column carries its value and how many there are, so the picture never
 * has to be counted to be read.
 */
@Composable
fun ChipStacks(amount: Long) {
    val stacks = chipsIn(amount.coerceAtLeast(0))
    Row(
        modifier = Modifier.fillMaxWidth().testTag("chip-stacks"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        stacks.forEach { (value, count) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.height(40.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                ) {
                    if (count == 0L) {
                        // An empty place, so every value stays where it always is.
                        Box(
                            Modifier.size(34.dp, 4.dp)
                                .border(BorderStroke(1.dp, Refugi.line), RoundedCornerShape(2.dp)),
                        )
                    } else {
                        repeat(minOf(count, MAX_EDGES.toLong()).toInt()) { ChipEdge(value) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                TcText(
                    chips(value),
                    Type.chips.copy(fontSize = 12.sp),
                    color = if (count == 0L) Refugi.line else Refugi.text,
                )
                TcText(
                    "\u00D7" + chips(count),
                    Type.caption.copy(fontSize = 11.sp),
                    color = if (count == 0L) Refugi.line else Refugi.text2,
                )
            }
        }
    }
}

@Composable
private fun ChipEdge(value: Long) {
    Box(Modifier.size(34.dp, 4.dp).background(chipColour(value), RoundedCornerShape(2.dp)))
}

internal fun chipColour(value: Long): Color = when (value) {
    1L -> Refugi.chip1
    5L -> Refugi.chip5
    25L -> Refugi.chip25
    50L -> Refugi.chip50
    else -> Refugi.chip100
}

@Composable
private fun PotCard(pot: Pot, label: String, selected: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 16.dp)) {
            Caption(label)
            Spacer(Modifier.height(7.dp))
            Figure(chips(pot.amount), Type.metric, color = if (selected) Refugi.accent else Refugi.text)
        }
    }
    ClickableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        fill = if (selected) Refugi.surfaceHigh else Refugi.surface,
        border = if (selected) Refugi.accent else Refugi.line,
        radius = 14.dp,
        padding = 0.dp,
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/**
 * The last move, offered back. The window here is a convenience; the log
 * undoes anything, at any depth, which is the promise.
 */
@Composable
fun UndoCard(what: String, hint: String, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Refugi.surfaceHigh, RoundedCornerShape(13.dp))
            .border(BorderStroke(1.dp, Refugi.lineAccent), RoundedCornerShape(13.dp))
            .padding(15.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TcText(what, Type.name.copy(fontSize = 15.sp, lineHeight = 19.sp))
            Spacer(Modifier.height(2.dp))
            TcText(hint, Type.body.copy(fontSize = 12.5.sp), color = Refugi.warn)
        }
        SecondaryButton(
            label = stringResource(R.string.log_undo),
            onClick = onUndo,
            height = 52.dp,
            warn = true,
        )
    }
}
