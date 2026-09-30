package io.github.tablechips.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tablechips.app.ui.BackHeader
import io.github.tablechips.app.ui.Caption
import io.github.tablechips.app.ui.Card
import io.github.tablechips.app.ui.ClickableSurface
import io.github.tablechips.app.ui.Frame
import io.github.tablechips.app.ui.Pills
import io.github.tablechips.app.ui.Refugi
import io.github.tablechips.app.ui.SecondaryButton
import io.github.tablechips.app.ui.TcText
import io.github.tablechips.app.ui.Type
import io.github.tablechips.app.ui.chips
import io.github.tablechips.core.GameMode
import io.github.tablechips.core.Payout
import io.github.tablechips.core.Player
import io.github.tablechips.core.PlayerId
import io.github.tablechips.core.PotId
import io.github.tablechips.protocol.ClientState

/**
 * What only the host may do: hand the pot over, correct a stack, change the
 * buy-in, open another pot. Every one of these is a ledger entry, so every one
 * of them can be undone.
 */
@Composable
fun HostPanelScreen(
    state: ClientState,
    selectedPot: PotId,
    onSelectPot: (PotId) -> Unit = {},
    pendingSplit: Long?,
    onAward: (Player, Long?) -> Unit,
    /** A tie: the pot shared evenly between these players. */
    onShare: (List<Player>) -> Unit = {},
    onSplit: () -> Unit,
    onNewPot: () -> Unit,
    onGive: (Player) -> Unit,
    onTake: (Player) -> Unit,
    onBuyIn: () -> Unit,
    onSeats: () -> Unit,
    onBanker: (Player?) -> Unit,
    onMode: (GameMode) -> Unit,
    onNaturalPays: (Payout) -> Unit,
    onNaturalTakesBank: (Boolean) -> Unit = {},
    onNewHand: () -> Unit,
    onCloseRound: () -> Unit,
    onSplitPots: () -> Unit,
    onBlinds: () -> Unit,
    onLog: () -> Unit,
    onClose: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val table = state.table ?: return
    val pot = table.pots.firstOrNull { it.id == selectedPot } ?: table.pots.first()
    val seated = table.players.filter { it.seat != null }.sortedBy { it.seat }
    val eligible = when {
        pot.eligible != null -> pot.eligible!!.mapNotNull { id -> table.players.firstOrNull { it.id == id } }
        table.config.mode == GameMode.POKER -> seated.filterNot { it.folded }
        else -> seated
    }
    val amount = pendingSplit ?: pot.amount
    // Picking the players who tied, rather than the one who won.
    var tying by remember(pot.id) { mutableStateOf(false) }
    var tied by remember(pot.id) { mutableStateOf(emptySet<PlayerId>()) }

    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.host_panel_title),
                subtitle = {
                    TcText(
                        stringResource(
                            R.string.host_panel_sub,
                            chips(seated.size.toLong()),
                            chips(table.pots.sumOf { it.amount }),
                        ),
                        Type.body,
                        color = Refugi.text2,
                    )
                },
                onBack = onBack,
            )
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SecondaryButton(stringResource(R.string.log_short), onLog, Modifier.weight(1f))
                if (onClose != null) {
                    SecondaryButton(
                        stringResource(R.string.host_stop), onClose, Modifier.weight(1f),
                        danger = true,
                    )
                } else {
                    SecondaryButton(stringResource(R.string.log_back), onBack, Modifier.weight(1f))
                }
            }
        },
    ) {
        Card(Modifier.fillMaxWidth(), padding = 0.dp) {
            Column(Modifier.padding(18.dp, 16.dp, 18.dp, 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Caption(
                        if (pendingSplit == null) stringResource(R.string.host_panel_award)
                        else stringResource(R.string.host_panel_award_part, chips(pendingSplit)),
                    )
                    Figure(chips(pot.amount), Type.metric.copy(fontSize = 28.sp), color = Refugi.accent)
                }
                // More than one pot: which one is being given is picked here,
                // where it is given, not back on the table.
                if (table.pots.size > 1) {
                    Spacer(Modifier.height(11.dp))
                    Pills(
                        options = table.pots.map { it.id to potName(it) + " \u00b7 " + chips(it.amount) },
                        selected = pot.id,
                        onSelect = onSelectPot,
                    )
                }
                Spacer(Modifier.height(13.dp))
                if (tying) {
                    TcText(stringResource(R.string.host_panel_tie_hint), Type.body, color = Refugi.text2)
                    Spacer(Modifier.height(9.dp))
                }
                eligible.forEach { player ->
                    val picked = player.id in tied
                    ClickableSurface(
                        onClick = {
                            if (tying) tied = if (picked) tied - player.id else tied + player.id
                            else onAward(player, pendingSplit)
                        },
                        modifier = Modifier.fillMaxWidth().height(58.dp).padding(bottom = 7.dp),
                        enabled = amount > 0,
                        fill = if (picked) Refugi.surface else Refugi.surfaceHigh,
                        border = if (picked) Refugi.accent else Refugi.lineAccent,
                        radius = 11.dp,
                        padding = 15.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TcText(
                                if (tying) (if (picked) "\u2713 " else "") + player.name
                                else stringResource(R.string.host_panel_wins, player.name),
                                Type.secondary,
                                maxLines = 1,
                                color = if (picked) Refugi.accent else Refugi.text,
                            )
                            if (!tying) TcText("+" + chips(amount), Type.chips, color = Refugi.gain)
                        }
                    }
                }
                if (tying) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SecondaryButton(
                            stringResource(R.string.common_cancel),
                            { tying = false; tied = emptySet() },
                            Modifier.weight(1f),
                            height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
                        )
                        SecondaryButton(
                            stringResource(R.string.host_panel_tie_confirm, chips(tied.size.toLong())),
                            {
                                onShare(eligible.filter { it.id in tied })
                                tying = false
                                tied = emptySet()
                            },
                            Modifier.weight(1f),
                            enabled = tied.size >= 2,
                            height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SecondaryButton(
                            stringResource(R.string.host_panel_split), onSplit, Modifier.weight(1f),
                            height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
                        )
                        if (eligible.size >= 2 && pendingSplit == null) {
                            SecondaryButton(
                                stringResource(R.string.host_panel_tie), { tying = true }, Modifier.weight(1f),
                                enabled = pot.amount > 0,
                                height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
                            )
                        }
                        SecondaryButton(
                            stringResource(R.string.host_panel_new_pot), onNewPot, Modifier.weight(1f),
                            height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
                        )
                    }
                }
            }
        }

        // The hand is run from here as often as anything else, so it comes
        // before the corrections.
        if (table.config.mode == GameMode.POKER) {
            PokerBlock(table.config.smallBlind, table.config.bigBlind, onNewHand, onCloseRound, onSplitPots, onBlinds)
        }

        Caption(stringResource(R.string.host_panel_players))
        table.players.forEach { player ->
            val broke = player.seat != null && player.stack == 0L
            Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp, 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        TcText(player.name, Type.nameStrong, maxLines = 1)
                        TcText(
                            if (broke) chips(0) + " · " + stringResource(R.string.host_panel_no_chips)
                            else chips(player.stack),
                            Type.chips.copy(fontSize = 13.sp),
                            color = if (broke) Refugi.loss else Refugi.text2,
                        )
                    }
                    SecondaryButton(
                        label = stringResource(R.string.host_panel_give),
                        onClick = { onGive(player) },
                        height = 48.dp,
                        warn = broke,
                        style = Type.secondary.copy(fontSize = 13.sp),
                    )
                    SecondaryButton(
                        label = stringResource(R.string.host_panel_take),
                        onClick = { onTake(player) },
                        height = 48.dp,
                        danger = true,
                        style = Type.secondary.copy(fontSize = 13.sp),
                    )
                }
            }
        }

        // The hands themselves are settled on the table, where the bank is;
        // here is only who holds it.
        if (table.config.mode.isBankGame) {
            Caption(stringResource(R.string.bank_title))
            BankerRow(state, onBanker)
        }

        Caption(stringResource(R.string.host_panel_seats))
        SecondaryButton(
            label = stringResource(R.string.seat_action),
            onClick = onSeats,
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
            style = Type.secondary.copy(fontSize = 15.sp),
        )

        Caption(stringResource(R.string.game_title))
        Pills(
            options = GameMode.entries.map { it to modeName(it) },
            selected = table.config.mode,
            onSelect = onMode,
        )
        if (table.config.mode.isBankGame) {
            Caption(stringResource(R.string.bank_pays))
            Pills(
                options = listOf(Payout(1, 1), Payout(3, 2), Payout(2, 1))
                    .map { it to it.numerator.toString() + ":" + it.denominator },
                selected = table.config.naturalPays,
                onSelect = onNaturalPays,
            )
        }
        if (table.config.mode == GameMode.SEVEN_HALF) {
            Caption(stringResource(R.string.bank_natural_takes))
            Pills(
                options = listOf(true to stringResource(R.string.common_yes), false to stringResource(R.string.common_no)),
                selected = table.config.naturalTakesBank,
                onSelect = onNaturalTakesBank,
            )
        }

        Caption(stringResource(R.string.host_panel_buy_in))
        SecondaryButton(
            label = stringResource(R.string.host_panel_buy_in_now, chips(table.config.defaultBuyIn)),
            onClick = onBuyIn,
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
            style = Type.secondary.copy(fontSize = 15.sp),
        )
        Box(Modifier.height(4.dp))
    }
}

/** Who holds the bank, and a way to hand it to somebody else or to nobody. */
@Composable
private fun BankerRow(state: ClientState, onBanker: (Player?) -> Unit) {
    val table = state.table ?: return
    var picking by remember { mutableStateOf(false) }
    val banker = table.banker?.let { id -> table.players.firstOrNull { it.id == id } }
    Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Column(Modifier.weight(1f)) {
                TcText(
                    stringResource(
                        if (table.config.mode == GameMode.BLACKJACK) R.string.poker_button else R.string.bank_title,
                    ),
                    Type.nameStrong,
                    maxLines = 1,
                )
                TcText(
                    banker?.name ?: stringResource(R.string.bank_none),
                    Type.body.copy(fontSize = 13.sp),
                    color = Refugi.text2,
                )
            }
            SecondaryButton(
                label = stringResource(if (banker == null) R.string.bank_assign else R.string.bank_change),
                onClick = { picking = !picking },
                height = 48.dp,
                style = Type.secondary.copy(fontSize = 13.sp),
            )
        }
    }
    if (picking) {
        table.players.filter { it.seat != null }.sortedBy { it.seat }.forEach { player ->
            Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp, 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        TcText(player.name, Type.nameStrong, maxLines = 1)
                        TcText(chips(player.stack), Type.chips.copy(fontSize = 13.sp), color = Refugi.text2)
                    }
                    SecondaryButton(
                        label = stringResource(R.string.bank_give),
                        onClick = { picking = false; onBanker(player) },
                        height = 48.dp,
                        warn = table.banker == player.id,
                        style = Type.secondary.copy(fontSize = 13.sp),
                    )
                }
            }
        }
        SecondaryButton(
            label = stringResource(R.string.bank_none),
            onClick = { picking = false; onBanker(null) },
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
            style = Type.secondary.copy(fontSize = 14.sp),
        )
    }
}

/**
 * The log settles arguments and takes moves back. Undo has no depth limit: at
 * a real table the mistakes are constant and going back has to be trivial.
 */
@Composable
fun LogScreen(state: ClientState, onUndo: () -> Unit, onBack: () -> Unit) {
    val table = state.table ?: return
    val entries = table.log.reversed()

    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.log_title),
                subtitle = {
                    TcText(
                        stringResource(R.string.log_sub, chips(table.rev)),
                        Type.body, color = Refugi.text2,
                    )
                },
                onBack = onBack,
            )
        },
        actions = {
            SecondaryButton(stringResource(R.string.log_back), onBack, Modifier.fillMaxWidth())
        },
    ) {
        if (state.isHost && state.undoDepth > 0 && entries.isNotEmpty()) {
            UndoCard(
                what = logLine(entries.first(), table),
                hint = stringResource(R.string.undo_always),
                onUndo = onUndo,
            )
        }
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp, 13.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TcText(
                    clockTime(entry.at),
                    Type.caption.copy(fontSize = 12.sp),
                    modifier = Modifier.width(42.dp),
                    color = Refugi.text2,
                )
                TcText(
                    logLine(entry, table),
                    Type.name.copy(fontSize = 14.5.sp, lineHeight = 19.sp),
                    modifier = Modifier.weight(1f),
                )
                val delta = logDelta(entry)
                if (delta == 0L) {
                    TcText("—", Type.body, color = Refugi.text2)
                } else {
                    TcText(
                        (if (delta > 0) "+" else "") + chips(delta),
                        Type.chips.copy(fontSize = 15.sp),
                        color = if (delta > 0) Refugi.gain else Refugi.loss,
                    )
                }
            }
            if (index < entries.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Refugi.surfaceHigh))
            }
        }
    }
}

/** The host's clock, as hours and minutes, which is all a log line needs. */
private fun clockTime(epochMillis: Long): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = epochMillis
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

@Composable
private fun PokerBlock(
    smallBlind: Long,
    bigBlind: Long,
    onNewHand: () -> Unit,
    onCloseRound: () -> Unit,
    onSplitPots: () -> Unit,
    onBlinds: () -> Unit,
) {
    Caption(stringResource(R.string.mode_poker))
    SecondaryButton(
        label = stringResource(R.string.poker_new_hand),
        onClick = onNewHand,
        modifier = Modifier.fillMaxWidth(),
        height = 52.dp,
        style = Type.secondary.copy(fontSize = 15.sp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        SecondaryButton(
            stringResource(R.string.poker_close_round), onCloseRound, Modifier.weight(1f),
            height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
        )
        SecondaryButton(
            stringResource(R.string.poker_split), onSplitPots, Modifier.weight(1f),
            height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
        )
    }
    SecondaryButton(
        label = stringResource(
            R.string.poker_blinds_now,
            chips(smallBlind),
            chips(bigBlind),
        ),
        onClick = onBlinds,
        modifier = Modifier.fillMaxWidth(),
        height = 52.dp,
        style = Type.secondary.copy(fontSize = 14.sp),
    )
}
