package io.github.victormico.tablechips.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The whole component set, built on foundation alone. No component library:
 * a Material button bent out of shape until it is unrecognisable would cost
 * more than drawing the four shapes this product actually uses.
 */

@Composable
fun TcText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Refugi.text,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Refugi.text2) {
    TcText(text.uppercase(), Type.caption, modifier, color)
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    background: Color = Refugi.surface,
    border: Color = Refugi.line,
    radius: Dp = 14.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(background, RoundedCornerShape(radius))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    height: Dp = 64.dp,
) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        enabled = enabled,
        fill = Refugi.accent,
        pressedFill = Refugi.accentHover,
        border = null,
        contentAlignment = if (subtitle == null) Alignment.Center else Alignment.CenterStart,
        padding = 18.dp,
    ) {
        if (subtitle == null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TcText(label, Type.primary, color = Refugi.onAccent)
                value?.let { TcText(it, Type.primaryValue, color = Refugi.onAccent) }
            }
        } else {
            Column {
                TcText(label, Type.primary, color = Refugi.onAccent)
                TcText(subtitle, Type.body, color = Refugi.onAccent.copy(alpha = .72f))
            }
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false,
    height: Dp = 60.dp,
    style: TextStyle = Type.secondary,
) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        fill = Refugi.surface,
        pressedFill = Refugi.surfaceHigh,
        border = if (danger) Refugi.lineDanger else Refugi.lineStrong,
        contentAlignment = if (subtitle == null) Alignment.Center else Alignment.CenterStart,
        padding = if (subtitle == null) 12.dp else 18.dp,
    ) {
        if (subtitle == null) {
            TcText(label, style, color = if (danger) Refugi.loss else Refugi.text)
        } else {
            Column {
                TcText(label, Type.primary, color = Refugi.text)
                TcText(subtitle, Type.body, color = Refugi.text2)
            }
        }
    }
}

@Composable
fun ClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = Refugi.surface,
    pressedFill: Color = Refugi.surfaceHigh,
    border: Color? = Refugi.line,
    radius: Dp = 12.dp,
    padding: Dp = 12.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .background(if (pressed && enabled) pressedFill else fill, shape)
            .let { if (border != null) it.border(BorderStroke(1.dp, border), shape) else it }
            // Every confirmed action buzzes: at a noisy table that is the
            // signal that arrives first. No ripple, no scale, no transition —
            // the pressed fill swaps immediately and costs nothing.
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = padding),
        contentAlignment = contentAlignment,
    ) { content() }
}

/** The brass ring with a diamond set in it: the mark of the house. */
@Composable
fun Mark(size: Dp, modifier: Modifier = Modifier) {
    val ring = size * 26f / 48f
    val stroke = size * 4f / 48f
    val diamond = size * 9f / 48f
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(ring)
                .border(BorderStroke(stroke, Refugi.accent), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(diamond).rotate(45f).background(Refugi.accent))
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

@Composable
fun RowScope.Spacer() {
    Box(Modifier.weight(1f))
}
