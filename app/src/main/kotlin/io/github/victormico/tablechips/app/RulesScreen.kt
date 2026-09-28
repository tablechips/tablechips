package io.github.victormico.tablechips.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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
        // Near the top on purpose: it is what gets looked up mid-hand, when
        // two players disagree on whose hand is better.
        if (config.mode == GameMode.POKER) HandRankings()
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

/** One poker hand: its name, what it is, and an example to recognise it by. */
private class PokerHand(val name: Int, val description: Int, val cards: String)

/** Highest first. The example cards read the same in every language. */
private val HANDS = listOf(
    PokerHand(R.string.hand_royal_flush, R.string.hand_royal_flush_desc, "A♠ K♠ Q♠ J♠ 10♠"),
    PokerHand(R.string.hand_straight_flush, R.string.hand_straight_flush_desc, "9♥ 8♥ 7♥ 6♥ 5♥"),
    PokerHand(R.string.hand_four_kind, R.string.hand_four_kind_desc, "Q♣ Q♦ Q♥ Q♠ 7♦"),
    PokerHand(R.string.hand_full_house, R.string.hand_full_house_desc, "8♠ 8♥ 8♦ K♣ K♥"),
    PokerHand(R.string.hand_flush, R.string.hand_flush_desc, "K♦ 10♦ 7♦ 4♦ 2♦"),
    PokerHand(R.string.hand_straight, R.string.hand_straight_desc, "7♣ 6♦ 5♠ 4♥ 3♣"),
    PokerHand(R.string.hand_three_kind, R.string.hand_three_kind_desc, "9♠ 9♥ 9♦ K♣ 4♠"),
    PokerHand(R.string.hand_two_pair, R.string.hand_two_pair_desc, "J♥ J♣ 4♦ 4♠ A♥"),
    PokerHand(R.string.hand_pair, R.string.hand_pair_desc, "10♠ 10♦ K♥ 6♣ 3♦"),
    PokerHand(R.string.hand_high_card, R.string.hand_high_card_desc, "A♣ J♦ 8♥ 6♠ 2♣"),
)

/**
 * Which hand beats which, and the ties that start arguments. The app never
 * sees the cards, but the question comes up at every table, and a hotspot has
 * no internet to look it up on.
 */
@Composable
private fun HandRankings() {
    Caption(stringResource(R.string.hands_title))
    Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
        Column(Modifier.padding(14.dp, 10.dp)) {
            TcText(stringResource(R.string.hands_sub), Type.body.copy(fontSize = 13.sp), color = Refugi.text2)
            HANDS.forEachIndexed { index, hand ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TcText(
                        (index + 1).toString(),
                        Type.chips.copy(fontSize = 14.sp),
                        color = Refugi.text2,
                        modifier = Modifier.width(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        TcText(stringResource(hand.name), Type.nameStrong.copy(fontSize = 15.sp))
                        Cards(hand.cards)
                        TcText(
                            stringResource(hand.description),
                            Type.body.copy(fontSize = 13.sp),
                            color = Refugi.text2,
                        )
                    }
                }
            }
        }
    }
    Caption(stringResource(R.string.hands_ties))
    Card(Modifier.fillMaxWidth(), padding = 0.dp, radius = 11.dp) {
        Column(Modifier.padding(14.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.hands_note_1, R.string.hands_note_2, R.string.hands_note_3,
                R.string.hands_note_4, R.string.hands_note_5,
            ).forEach { note ->
                TcText(stringResource(note), Type.name.copy(fontSize = 14.sp, lineHeight = 20.sp))
            }
        }
    }
}

/** Example cards, hearts and diamonds in red as on any deck. */
@Composable
private fun Cards(cards: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        cards.split(" ").forEach { card ->
            val red = card.endsWith("\u2665") || card.endsWith("\u2666")
            TcText(card, Type.chips.copy(fontSize = 15.sp), color = if (red) Refugi.loss else Refugi.text)
        }
    }
}
