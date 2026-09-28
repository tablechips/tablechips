package io.github.victormico.tablechips.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.victormico.tablechips.app.ui.BackHeader
import io.github.victormico.tablechips.app.ui.Caption
import io.github.victormico.tablechips.app.ui.Card
import io.github.victormico.tablechips.app.ui.Frame
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips
import io.github.victormico.tablechips.core.GameMode
import io.github.victormico.tablechips.core.TableConfig

/**
 * How the game on this table is played, what the app does about it, and —
 * most of all — what it leaves to the people sitting there: whose turn it is
 * and who won are theirs, and that decision is nowhere else in the app.
 *
 * It shows this table's own numbers, not generic ones: the house rules are half
 * the argument at a real table.
 */
@Composable
fun RulesScreen(config: TableConfig, onBack: () -> Unit) {
    val texts = rulesFor(config.mode)
    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.rules_title),
                subtitle = { TcText(modeName(config.mode), Type.body, color = Refugi.text2) },
                onBack = onBack,
            )
        },
        actions = {
            SecondaryButton(stringResource(R.string.common_back), onBack, Modifier.fillMaxWidth())
        },
    ) {
        Section(stringResource(R.string.rules_play), stringResource(texts.play))
        Section(stringResource(R.string.rules_count), stringResource(texts.count))
        Section(stringResource(R.string.rules_people), stringResource(texts.people), strong = true)

        Caption(stringResource(R.string.rules_table))
        Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
            Column(Modifier.padding(14.dp, 12.dp)) {
                TableLine(stringResource(R.string.rules_buy_in, chips(config.defaultBuyIn)))
                TableLine(stringResource(R.string.rules_seats, chips(config.seatCount.toLong())))
                if (config.mode.isBankGame) {
                    TableLine(
                        stringResource(
                            R.string.rules_natural,
                            config.naturalPays.numerator.toString() + ":" + config.naturalPays.denominator,
                        ),
                    )
                }
                if (config.mode == GameMode.POKER) {
                    TableLine(
                        if (config.bigBlind > 0) {
                            stringResource(R.string.rules_blinds, chips(config.smallBlind), chips(config.bigBlind))
                        } else {
                            stringResource(R.string.rules_no_blinds)
                        },
                    )
                }
            }
        }
    }
}

private class RuleTexts(val play: Int, val count: Int, val people: Int)

private fun rulesFor(mode: GameMode): RuleTexts = when (mode) {
    GameMode.MANUAL -> RuleTexts(
        R.string.rules_manual_play, R.string.rules_manual_count, R.string.rules_manual_people,
    )
    GameMode.SEVEN_HALF -> RuleTexts(
        R.string.rules_seven_half_play, R.string.rules_seven_half_count, R.string.rules_seven_half_people,
    )
    GameMode.BLACKJACK -> RuleTexts(
        R.string.rules_blackjack_play, R.string.rules_blackjack_count, R.string.rules_blackjack_people,
    )
    GameMode.POKER -> RuleTexts(
        R.string.rules_poker_play, R.string.rules_poker_count, R.string.rules_poker_people,
    )
}

@Composable
private fun Section(title: String, body: String, strong: Boolean = false) {
    Caption(title)
    Card(
        Modifier.fillMaxWidth(),
        padding = 0.dp,
        radius = 11.dp,
        border = if (strong) Refugi.lineAccent else Refugi.line,
    ) {
        TcText(
            body,
            Type.name.copy(fontSize = 15.sp, lineHeight = 21.sp),
            modifier = Modifier.padding(14.dp, 12.dp),
        )
    }
}

@Composable
private fun TableLine(text: String) {
    TcText(text, Type.chips.copy(fontSize = 14.sp, lineHeight = 22.sp))
    Spacer(Modifier.height(2.dp))
}
