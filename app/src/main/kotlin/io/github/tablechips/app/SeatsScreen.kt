package io.github.tablechips.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tablechips.app.ui.BackHeader
import io.github.tablechips.app.ui.Caption
import io.github.tablechips.app.ui.Card
import io.github.tablechips.app.ui.Frame
import io.github.tablechips.app.ui.Note
import io.github.tablechips.app.ui.Refugi
import io.github.tablechips.app.ui.SecondaryButton
import io.github.tablechips.app.ui.StatusDot
import io.github.tablechips.app.ui.TcText
import io.github.tablechips.app.ui.Type
import io.github.tablechips.app.ui.chips
import io.github.tablechips.core.Player
import io.github.tablechips.protocol.ClientState

/**
 * The host's way out of the two situations the rules cannot fix by themselves:
 * a phone that is never coming back, and somebody who should not be at the
 * table any more.
 *
 * Handing a seat over moves the chips with it, so the table stays balanced: it
 * is a change of who holds them, not a payout. Throwing somebody out cashes
 * their stack out, which is what walking away from a table means.
 */
@Composable
fun SeatsScreen(
    state: ClientState,
    onKick: (Player) -> Unit,
    onTransfer: (Player, Player) -> Unit,
    onBack: () -> Unit,
) {
    val table = state.table ?: return
    var giving by remember { mutableStateOf<Player?>(null) }
    val seated = table.players.filter { it.seat != null }.sortedBy { it.seat }
    val standing = table.players.filter { it.seat == null }
    // The list is rebuilt from every state frame, so a player who leaves in the
    // meantime stops being a candidate on their own.
    val chosen = giving?.let { picked -> seated.firstOrNull { it.id == picked.id } }

    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.seat_title),
                subtitle = {
                    TcText(
                        stringResource(R.string.seat_sub),
                        Type.body,
                        color = Refugi.text2,
                    )
                },
                onBack = { if (chosen != null) giving = null else onBack() },
            )
        },
        actions = {
            SecondaryButton(
                label = stringResource(R.string.log_back),
                onClick = { if (chosen != null) giving = null else onBack() },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (chosen == null) {
            Caption(stringResource(R.string.seat_from))
            seated.forEach { player ->
                Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp, 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        StatusDot(if (player.connected) Refugi.gain else Refugi.line)
                        Column(Modifier.weight(1f)) {
                            TcText(player.name, Type.nameStrong, maxLines = 1)
                            TcText(
                                chips(player.stack) +
                                    if (player.connected) "" else " · " + stringResource(R.string.seat_offline),
                                Type.chips.copy(fontSize = 13.sp),
                                color = if (player.connected) Refugi.text2 else Refugi.warn,
                            )
                        }
                        SecondaryButton(
                            label = stringResource(R.string.seat_hand),
                            onClick = { giving = player },
                            height = 48.dp,
                            style = Type.secondary.copy(fontSize = 13.sp),
                        )
                        if (!player.isHost) {
                            SecondaryButton(
                                label = stringResource(R.string.host_panel_kick),
                                onClick = { onKick(player) },
                                height = 48.dp,
                                danger = true,
                                style = Type.secondary.copy(fontSize = 13.sp),
                            )
                        }
                    }
                }
            }
        } else {
            Caption(stringResource(R.string.seat_to, chosen.name))
            if (standing.isEmpty()) {
                Note(stringResource(R.string.seat_none))
            }
            standing.forEach { player ->
                Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp, 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        StatusDot(if (player.connected) Refugi.gain else Refugi.line)
                        Column(Modifier.weight(1f)) {
                            TcText(player.name, Type.nameStrong, maxLines = 1)
                            TcText(
                                chips(chosen.stack),
                                Type.chips.copy(fontSize = 13.sp),
                                color = Refugi.accent,
                            )
                        }
                        SecondaryButton(
                            label = stringResource(R.string.seat_confirm),
                            onClick = {
                                onTransfer(chosen, player)
                                giving = null
                            },
                            height = 48.dp,
                            style = Type.secondary.copy(fontSize = 13.sp),
                        )
                    }
                }
            }
        }
        Box(Modifier.height(4.dp))
    }
}
