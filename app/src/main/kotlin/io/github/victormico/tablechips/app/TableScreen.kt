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
    onBet: () -> Unit,
    onRebuy: () -> Unit,
    onStand: () -> Unit,
    onSit: () -> Unit,
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
                MenuButton(onMenu)
            }
        },
        actions = {
            if (state.seated) {
                PrimaryButton(stringResource(R.string.action_bet), onBet)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SecondaryButton(
                        stringResource(R.string.action_rebuy), onRebuy, Modifier.weight(1f),
                    )
                    SecondaryButton(
                        stringResource(R.string.action_stand), onStand, Modifier.weight(1f),
                    )
                }
            } else {
                PrimaryButton(stringResource(R.string.action_sit), onSit)
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
                    StackBars()
                }
            }
        }

        // One pot is the number itself; several are a choice, and the one your
        // bet goes into is the one wearing the brass border.
        if (table.pots.size == 1) {
            PotCard(table.pots.first(), stringResource(R.string.amount_pot), selected = false) {}
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                table.pots.forEach { pot ->
                    Box(Modifier.weight(1f)) {
                        PotCard(pot, potName(pot), pot.id == selectedPot) { onSelectPot(pot.id) }
                    }
                }
            }
        }

        if (state.isHost && undoable != null && state.undoDepth > 0) {
            UndoCard(undoable, stringResource(R.string.undo_always), onUndo)
        }

        val seated = table.players.filter { it.seat != null }.sortedBy { it.seat }
        Row(verticalAlignment = Alignment.Bottom) {
            Caption(stringResource(R.string.player_at_table))
            Box(Modifier.weight(1f))
            TcText(chips(seated.size.toLong()), Type.caption, color = Refugi.text2)
        }
        seated.forEach { player ->
            PlayerRow(
                name = player.name,
                chips = chips(player.stack),
                dot = when {
                    player.id == state.you -> Refugi.accent
                    player.connected -> Refugi.gain
                    else -> Refugi.line
                },
                strong = player.id == state.you,
                dim = !player.connected,
                tags = buildList {
                    if (player.isHost) add(stringResource(R.string.host_role))
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

/** Chips seen edge on. Informative decoration, not a count. */
@Composable
private fun StackBars() {
    val colours = listOf(Refugi.accent, Refugi.gain, Refugi.gain, Refugi.loss)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colours.forEach { colour ->
            Row(
                modifier = Modifier.weight(1f).height(10.dp)
                    .background(Refugi.surfaceHigh, RoundedCornerShape(3.dp)),
            ) {
                Box(Modifier.size(4.dp, 10.dp).background(colour))
            }
        }
    }
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
