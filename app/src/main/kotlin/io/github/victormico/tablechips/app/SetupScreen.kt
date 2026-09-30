package io.github.victormico.tablechips.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.victormico.tablechips.app.ui.BackHeader
import io.github.victormico.tablechips.app.ui.Caption
import io.github.victormico.tablechips.app.ui.Frame
import io.github.victormico.tablechips.app.ui.Note
import io.github.victormico.tablechips.app.ui.Pills
import io.github.victormico.tablechips.app.ui.PrimaryButton
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips
import io.github.victormico.tablechips.core.GameMode
import io.github.victormico.tablechips.core.MAX_SEATS
import io.github.victormico.tablechips.core.Payout
import io.github.victormico.tablechips.core.TableConfig

/** Fewer than two is not a table. */
const val MIN_SEATS: Int = 2

/**
 * The questions worth answering before anybody sits down: which game, what a
 * seat costs, how many seats. Everything has a sensible answer already, so the
 * big button works without touching anything, and nothing here is final — the
 * host panel changes all of it mid-game.
 */
@Composable
fun SetupScreen(
    config: TableConfig,
    starting: Boolean,
    failed: Boolean = false,
    /** The host's own name at the table: the first thing the others will see. */
    name: String,
    onName: (String) -> Unit,
    onConfig: (TableConfig) -> Unit,
    onBuyIn: () -> Unit,
    onBlinds: () -> Unit,
    onRules: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.setup_title),
                subtitle = { TcText(stringResource(R.string.setup_sub), Type.body, color = Refugi.text2) },
                onBack = onBack,
            )
        },
        actions = {
            PrimaryButton(
                label = stringResource(if (starting) R.string.host_starting else R.string.setup_open),
                onClick = onOpen,
                enabled = !starting && name.isNotBlank(),
            )
        },
    ) {
        if (failed) Note(stringResource(R.string.host_error_start), color = Refugi.loss)
        Caption(stringResource(R.string.join_name))
        Field(name, stringResource(R.string.join_name), onName)

        Caption(stringResource(R.string.game_title))
        Pills(
            options = GameMode.entries.map { it to modeName(it) },
            selected = config.mode,
            onSelect = { mode -> onConfig(config.forMode(mode)) },
        )
        SecondaryButton(
            label = stringResource(R.string.rules_how),
            onClick = onRules,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp,
            borderless = true,
            style = Type.body.copy(fontSize = 14.sp),
        )

        Caption(stringResource(R.string.host_panel_buy_in))
        SecondaryButton(
            label = stringResource(R.string.setup_buy_in_now, chips(config.defaultBuyIn)),
            onClick = onBuyIn,
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
            style = Type.secondary.copy(fontSize = 15.sp),
        )

        Caption(stringResource(R.string.setup_seats))
        Stepper(
            value = config.seatCount,
            range = MIN_SEATS..MAX_SEATS,
            less = stringResource(R.string.setup_fewer_seats),
            more = stringResource(R.string.setup_more_seats),
            onChange = { onConfig(config.copy(seatCount = it)) },
        )

        if (config.mode.isBankGame) {
            Caption(stringResource(R.string.bank_pays))
            Pills(
                options = PAYOUTS.map { it to it.numerator.toString() + ":" + it.denominator },
                selected = config.naturalPays,
                onSelect = { onConfig(config.copy(naturalPays = it)) },
            )
        }
        if (config.mode == GameMode.SEVEN_HALF) {
            Caption(stringResource(R.string.bank_natural_takes))
            Pills(
                options = listOf(true to stringResource(R.string.common_yes), false to stringResource(R.string.common_no)),
                selected = config.naturalTakesBank,
                onSelect = { onConfig(config.copy(naturalTakesBank = it)) },
            )
        }
        if (config.mode == GameMode.POKER) {
            Caption(stringResource(R.string.poker_blinds))
            SecondaryButton(
                label = stringResource(
                    R.string.poker_blinds_now,
                    chips(config.smallBlind),
                    chips(config.bigBlind),
                ),
                onClick = onBlinds,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
                style = Type.secondary.copy(fontSize = 14.sp),
            )
        }
        Box(Modifier.height(4.dp))
    }
}

/** The payouts a natural can be set to: even money, blackjack's, set i mig's. */
val PAYOUTS: List<Payout> = listOf(Payout(1, 1), Payout(3, 2), Payout(2, 1))

/**
 * Big blinds a chip case pays cleanly: the small blind, half of each, is
 * always one or two chips. A plain fiftieth of the buy-in would make 905 into
 * blinds of 9 and 18, which nobody at a real table would ever post.
 */
private val BIG_BLINDS: List<Long> = listOf(2, 10, 20, 50, 100, 200, 500, 1000)

/**
 * Switching game brings that game's usual house rule with it: 3:2 for a
 * blackjack, 2:1 for a set i mig, and for poker blinds of about a fiftieth of
 * the buy-in, rounded down to ones the chips can pay. They are starting
 * points, shown right under the choice.
 */
fun TableConfig.forMode(mode: GameMode): TableConfig = when (mode) {
    GameMode.BLACKJACK -> copy(mode = mode, naturalPays = Payout(3, 2))
    GameMode.SEVEN_HALF -> copy(mode = mode, naturalPays = Payout(2, 1))
    GameMode.POKER -> if (bigBlind > 0) {
        copy(mode = mode)
    } else {
        val big = BIG_BLINDS.lastOrNull { it <= defaultBuyIn / 50 } ?: BIG_BLINDS.first()
        copy(mode = mode, bigBlind = big, smallBlind = big / 2)
    }
    GameMode.MANUAL -> copy(mode = mode)
}

/** A number with a button either side, for the few settings that are small counts. */
@Composable
private fun Stepper(
    value: Int,
    range: IntRange,
    less: String,
    more: String,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecondaryButton(
            label = "−",
            onClick = { onChange((value - 1).coerceIn(range)) },
            modifier = Modifier.width(64.dp).semantics { contentDescription = less },
            height = 52.dp,
            enabled = value > range.first,
            style = Type.secondary.copy(fontSize = 22.sp),
        )
        TcText(
            chips(value.toLong()),
            Type.metric.copy(fontSize = 28.sp, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(
            label = "+",
            onClick = { onChange((value + 1).coerceIn(range)) },
            modifier = Modifier.width(64.dp).semantics { contentDescription = more },
            height = 52.dp,
            enabled = value < range.last,
            style = Type.secondary.copy(fontSize = 22.sp),
        )
    }
}
