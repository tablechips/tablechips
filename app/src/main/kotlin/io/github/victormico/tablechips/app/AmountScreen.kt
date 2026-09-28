package io.github.victormico.tablechips.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.victormico.tablechips.app.ui.Caption
import io.github.victormico.tablechips.app.ui.Card
import io.github.victormico.tablechips.app.ui.ClickableSurface
import io.github.victormico.tablechips.app.ui.Frame
import io.github.victormico.tablechips.app.ui.BackHeader
import io.github.victormico.tablechips.app.ui.PrimaryButton
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips

/** What the amount screen is being asked for, and what it does with the answer. */
data class AmountRequest(
    val title: String,
    val subtitle: String,
    val confirm: String,
    val initial: Long? = null,
    /** Highest legal value, when there is one: a bet cannot exceed the stack. */
    val max: Long? = null,
    val allowZero: Boolean = false,
    /** The pot, when the shortcuts should offer half of it and all of it. */
    val pot: Long? = null,
    val restLabel: String,
    val rest: (Long) -> Long,
    val onConfirm: (Long) -> Unit,
    /** Where cancelling goes, when it is not back to the table. */
    val onCancel: (() -> Unit)? = null,
)

/**
 * Setting an amount. The figure is always on screen while it is being typed —
 * the keypad gives up its height first — and typing is always available: the
 * quick values only fill the field in, which is the difference between an
 * accelerator and the only way to bet.
 */
@Composable
fun AmountScreen(
    request: AmountRequest,
    typed: String,
    untouched: Boolean,
    onType: (String) -> Unit,
    onSet: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val value = typed.toLongOrNull() ?: 0L
    val rest = request.rest(value)
    val overMax = request.max != null && value > request.max
    val ready = (value > 0 || request.allowZero) && !overMax

    Frame(
        scrolling = false,
        header = {
            BackHeader(
                title = request.title,
                subtitle = { TcText(request.subtitle, Type.body, color = Refugi.text2) },
                onBack = onBack,
            )
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SecondaryButton(
                    label = stringResource(R.string.common_cancel),
                    onClick = onBack,
                    modifier = Modifier.width(112.dp),
                    height = 64.dp,
                )
                PrimaryButton(
                    label = request.confirm,
                    value = chips(value),
                    onClick = { request.onConfirm(value) },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = Refugi.accent,
            padding = 0.dp,
        ) {
            Column(Modifier.padding(20.dp, 18.dp)) {
                Caption(stringResource(R.string.amount_label))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Figure(chips(value), Type.amount, modifier = Modifier.testTag("amount-figure"))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(3.dp, 52.dp).background(Refugi.accent))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Refugi.line))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TcText(request.restLabel, Type.body, color = Refugi.text2)
                    TcText(
                        chips(rest),
                        Type.chips.copy(fontSize = 17.sp),
                        color = if (overMax) Refugi.loss else Refugi.text,
                    )
                }
            }
        }

        Caption(stringResource(R.string.amount_quick))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (request.pot != null) {
                Quick(stringResource(R.string.amount_half_pot), Modifier.weight(1f)) {
                    onSet(request.pot / 2)
                }
                Quick(stringResource(R.string.amount_pot), Modifier.weight(1f)) { onSet(request.pot) }
            } else {
                Quick("+25", Modifier.weight(1f), mono = true) { onSet(value + 25) }
                Quick("+100", Modifier.weight(1f), mono = true) { onSet(value + 100) }
            }
            Quick(
                stringResource(R.string.amount_all),
                Modifier.weight(1f),
                warn = true,
            ) { onSet(request.max ?: request.initial ?: 0) }
        }

        // The keypad is what gives way when the screen is short.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                KeyRow(Modifier.weight(1f)) {
                    row.forEach { digit -> Key(digit, Modifier.weight(1f)) { onType(digit) } }
                }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("00", Modifier.weight(1f), small = true) { onType("00") }
                Key("0", Modifier.weight(1f)) { onType("0") }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ClickableSurface(
                        onClick = { onType("<") },
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("key:back"),
                        border = Refugi.line,
                        padding = 0.dp,
                    ) { Backspace() }
                }
            }
        }
    }
}

@Composable
private fun KeyRow(modifier: Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Key(label: String, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight()) {
        ClickableSurface(
            onClick = onClick,
            // Tagged so a rendering test can press a key without also matching
            // the figure or the confirm button, which carry the same digits.
            modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("key:$label"),
            border = Refugi.line,
            padding = 0.dp,
        ) {
            TcText(label, Type.metric.copy(fontSize = if (small) 20.sp else 26.sp))
        }
    }
}

@Composable
private fun Quick(label: String, modifier: Modifier, mono: Boolean = false, warn: Boolean = false, onClick: () -> Unit) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        border = Refugi.lineStrong,
        radius = 11.dp,
        padding = 6.dp,
    ) {
        TcText(
            label,
            if (mono) Type.chips.copy(fontSize = 16.sp) else Type.secondary.copy(fontSize = 14.sp),
            color = if (warn) Refugi.warn else Refugi.text,
            maxLines = 2,
        )
    }
}

@Composable
private fun Backspace() {
    Box(
        modifier = Modifier.size(22.dp, 15.dp)
            .border(BorderStroke(2.dp, Refugi.text), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(9.dp, 2.dp).rotate(45f).background(Refugi.text))
        Box(Modifier.size(9.dp, 2.dp).rotate(-45f).background(Refugi.text))
    }
}
