package io.github.victormico.tablechips.app

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
import io.github.victormico.tablechips.app.ui.BackHeader
import io.github.victormico.tablechips.app.ui.Caption
import io.github.victormico.tablechips.app.ui.Card
import io.github.victormico.tablechips.app.ui.ClickableSurface
import io.github.victormico.tablechips.app.ui.Frame
import io.github.victormico.tablechips.app.ui.Note
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips
import io.github.victormico.tablechips.core.GameMode
import io.github.victormico.tablechips.core.HandOutcome
import io.github.victormico.tablechips.core.Payout
import io.github.victormico.tablechips.core.Player
import io.github.victormico.tablechips.core.PotId
import io.github.victormico.tablechips.protocol.ClientState

/**
 * What only the host may do: hand the pot over, correct a stack, change the
 * buy-in, open another pot. Every one of these is a ledger entry, so every one
 * of them can be undone.
 */
@Composable
fun HostPanelScreen(
    state: ClientState,
    selectedPot: PotId,
    pendingSplit: Long?,
    onAward: (Player, Long?) -> Unit,
    onSplit: () -> Unit,
    onNewPot: () -> Unit,
    onGive: (Player) -> Unit,
    onTake: (Player) -> Unit,
    onBuyIn: () -> Unit,
    onSeats: () -> Unit,
    onBanker: (Player?) -> Unit,
    onSettle: (Player, HandOutcome) -> Unit,
    onMode: (GameMode) -> Unit,
    onNaturalPays: (Payout) -> Unit,
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
                Spacer(Modifier.height(13.dp))
                eligible.forEach { player ->
                    ClickableSurface(
                        onClick = { onAward(player, pendingSplit) },
                        modifier = Modifier.fillMaxWidth().height(58.dp).padding(bottom = 7.dp),
                        enabled = amount > 0,
                        fill = Refugi.surfaceHigh,
                        border = Refugi.lineAccent,
                        radius = 11.dp,
                        padding = 15.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TcText(
                                stringResource(R.string.host_panel_wins, player.name),
                                Type.secondary,
                                maxLines = 1,
                            )
                            TcText("+" + chips(amount), Type.chips, color = Refugi.gain)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SecondaryButton(
                        stringResource(R.string.host_panel_split), onSplit, Modifier.weight(1f),
                        height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
                    )
                    SecondaryButton(
                        stringResource(R.string.host_panel_new_pot), onNewPot, Modifier.weight(1f),
                        height = 52.dp, style = Type.secondary.copy(fontSize = 14.sp),
                    )
                }
            }
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

        if (table.config.mode.isBankGame) {
            Caption(stringResource(R.string.bank_hands))
            BankerRow(state, onBanker)
            val staked = table.players.filter { it.stake > 0 }
            if (staked.isEmpty()) Note(stringResource(R.string.bank_no_hands))
            staked.forEach { player -> HandCard(player, table.config.mode, onSettle) }
        }

        if (table.config.mode == GameMode.POKER) {
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
                    chips(table.config.smallBlind),
                    chips(table.config.bigBlind),
                ),
                onClick = onBlinds,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
                style = Type.secondary.copy(fontSize = 14.sp),
            )
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
                TcText(stringResource(R.string.bank_title), Type.nameStrong, maxLines = 1)
                TcText(
                    banker?.name ?: stringResource(R.string.bank_none),
                    Type.body.copy(fontSize = 13.sp),
                    color = Refugi.text2,
                )
            }
            SecondaryButton(
                label = stringResource(R.string.seat_hand),
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
                        label = stringResource(R.string.seat_confirm),
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
 * One hand waiting for the host to say how it ended. The app saw no cards, so
 * these four buttons are the whole of its opinion on the matter.
 */
@Composable
private fun HandCard(player: Player, mode: GameMode, onSettle: (Player, HandOutcome) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        padding = 0.dp,
        radius = 11.dp,
        border = Refugi.lineAccent,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp, 11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Column {
                TcText(player.name, Type.nameStrong, maxLines = 1)
                TcText(chips(player.stake), Type.chips.copy(fontSize = 13.sp), color = Refugi.accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryButton(
                    stringResource(R.string.bank_win), { onSettle(player, HandOutcome.WIN) },
                    Modifier.weight(1f), height = 44.dp,
                    style = Type.secondary.copy(fontSize = 13.sp),
                )
                SecondaryButton(
                    naturalName(mode), { onSettle(player, HandOutcome.NATURAL) },
                    Modifier.weight(1f), height = 44.dp,
                    style = Type.secondary.copy(fontSize = 13.sp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryButton(
                    stringResource(R.string.bank_push), { onSettle(player, HandOutcome.PUSH) },
                    Modifier.weight(1f), height = 44.dp,
                    style = Type.secondary.copy(fontSize = 13.sp),
                )
                SecondaryButton(
                    stringResource(R.string.bank_lose), { onSettle(player, HandOutcome.LOSE) },
                    Modifier.weight(1f), height = 44.dp, danger = true,
                    style = Type.secondary.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

/** A row of choices where exactly one is on. */
@Composable
private fun <T> Pills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Refugi.surface, RoundedCornerShape(9.dp))
            .border(BorderStroke(1.dp, Refugi.line), RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            ClickableSurface(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f).height(38.dp),
                fill = if (on) Refugi.accent else Color.Transparent,
                border = Color.Transparent,
                radius = 7.dp,
                padding = 2.dp,
                contentAlignment = Alignment.Center,
            ) {
                TcText(
                    label,
                    Type.secondary.copy(fontSize = 13.sp),
                    color = if (on) Refugi.onAccent else Refugi.text2,
                    maxLines = 1,
                )
            }
        }
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
