package io.github.tablechips.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import io.github.tablechips.protocol.qrCode
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    warn: Boolean = false,
    borderless: Boolean = false,
    height: Dp = 60.dp,
    style: TextStyle = Type.secondary,
    enabled: Boolean = true,
) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        // Neither warn nor loss ever fills a button: they are text and border,
        // so the primary action differs in shape and not merely in hue.
        fill = if (borderless) Refugi.bg else if (warn) Refugi.surfaceHigh else Refugi.surface,
        pressedFill = if (warn) Refugi.surface else Refugi.surfaceHigh,
        border = when {
            borderless -> null
            danger -> Refugi.lineDanger
            warn -> Refugi.warn
            else -> Refugi.lineStrong
        },
        contentAlignment = if (subtitle == null) Alignment.Center else Alignment.CenterStart,
        padding = if (subtitle == null) 12.dp else 18.dp,
    ) {
        if (subtitle == null) {
            TcText(
                label, style,
                color = when {
                    !enabled -> Refugi.line
                    danger -> Refugi.loss
                    warn -> Refugi.warn
                    borderless -> Refugi.text2
                    else -> Refugi.text
                },
            )
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

/**
 * The frame every screen shares: a fixed header, content that scrolls, and the
 * actions pinned to the bottom where a thumb reaches them. The bands never
 * move with the content.
 */
@Composable
fun Frame(
    header: @Composable () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    scrolling: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        header()
        Divider()
        val body = Modifier
            .weight(1f)
            .fillMaxWidth()
            .let { if (scrolling) it.verticalScroll(rememberScrollState()) else it }
            .padding(Refugi.side, 16.dp, Refugi.side, 4.dp)
        Column(body, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        Divider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(Refugi.side, 14.dp, Refugi.side, 20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = actions,
        )
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Refugi.surfaceHigh))
}

/** Header of a screen you came into from somewhere else. */
@Composable
fun BackHeader(title: String, subtitle: (@Composable () -> Unit)?, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Refugi.side, 14.dp, Refugi.side, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClickableSurface(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            fill = Refugi.bg,
            border = Refugi.line,
            radius = 10.dp,
            padding = 0.dp,
        ) { BackArrow() }
        Column(Modifier.weight(1f)) {
            TcText(title, Type.title.copy(fontSize = 15.sp, lineHeight = 18.sp))
            subtitle?.invoke()
        }
    }
}

@Composable
private fun BackArrow() {
    Canvas(Modifier.size(12.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(Refugi.text, Offset(size.width, 0f), Offset(0f, size.height / 2), stroke)
        drawLine(Refugi.text, Offset(0f, size.height / 2), Offset(size.width, size.height), stroke)
    }
}

/** The three dots that open the table's menu. */
@Composable
fun MenuButton(onClick: () -> Unit) {
    ClickableSurface(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        fill = Refugi.bg,
        border = Refugi.line,
        radius = 8.dp,
        padding = 0.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
            repeat(3) { Box(Modifier.size(3.dp).background(Refugi.text2, CircleShape)) }
        }
    }
}

/** The rules of the game, one tap from the table, in the shape of the menu button. */
@Composable
fun HelpButton(label: String, onClick: () -> Unit) {
    ClickableSurface(
        onClick = onClick,
        modifier = Modifier.size(32.dp).semantics { contentDescription = label },
        fill = Refugi.bg,
        border = Refugi.line,
        radius = 8.dp,
        padding = 0.dp,
    ) {
        TcText("?", Type.secondary.copy(fontSize = 15.sp), color = Refugi.text2)
    }
}

/** A block of advice that stays put: informative, never a passing toast. */
@Composable
fun Note(text: String, color: Color = Refugi.warn, border: Color = Refugi.lineAccent) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Refugi.surfaceHigh, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
            .padding(13.dp, 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(18.dp).border(BorderStroke(2.dp, color), CircleShape),
            contentAlignment = Alignment.Center,
        ) { TcText("!", Type.caption, color = color) }
        TcText(text, Type.body, color = color)
    }
}

/** A row of the table: who, how much, and whether they are still with us. */
@Composable
fun PlayerRow(
    name: String,
    chips: String,
    dot: Color,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    dim: Boolean = false,
    tags: List<String> = emptyList(),
    chipsColor: Color = Refugi.text,
) {
    Card(
        modifier = modifier.fillMaxWidth().alpha(if (dim) .72f else 1f),
        radius = 11.dp,
        padding = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            StatusDot(dot)
            TcText(
                name,
                if (strong) Type.nameStrong else Type.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            tags.forEach { TcText(it, Type.body, color = Refugi.text2) }
            if (chips.isNotEmpty()) TcText(chips, Type.chips, color = chipsColor)
        }
    }
}

/**
 * A question with two answers, over whatever is underneath. Closing a table or
 * throwing somebody out is not undoable from the other side of the socket, so
 * it gets asked first; everything else does not.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Refugi.bg.copy(alpha = .8f))
            .padding(Refugi.side),
        contentAlignment = Alignment.Center,
    ) {
        Card(Modifier.fillMaxWidth(), padding = 18.dp) {
            TcText(title, Type.primary)
            Box(Modifier.height(8.dp))
            TcText(body, Type.body, color = Refugi.text2)
            Box(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SecondaryButton(cancel, onCancel, Modifier.weight(1f))
                SecondaryButton(confirm, onConfirm, Modifier.weight(1f), danger = true)
            }
        }
    }
}

/**
 * A QR code, drawn rather than rendered to a bitmap. Square modules on the one
 * light surface of the product: a rounded module is a module a cheap camera
 * reads wrong in bad light, and a dark code is a code that will not scan at
 * all across a table at night.
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, quietZone: Int = 3) {
    val matrix = remember(text) { qrCode(text) }
    Canvas(modifier) {
        val side = matrix.size + quietZone * 2
        val module = size.minDimension / side
        val origin = androidx.compose.ui.geometry.Offset(
            (size.width - module * side) / 2f,
            (size.height - module * side) / 2f,
        )
        drawRect(
            color = Refugi.text,
            topLeft = origin,
            size = androidx.compose.ui.geometry.Size(module * side, module * side),
        )
        // A hair of overlap, so no seam of background shows between modules.
        val block = androidx.compose.ui.geometry.Size(module + .5f, module + .5f)
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                drawRect(
                    color = Refugi.bg,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        origin.x + (x + quietZone) * module,
                        origin.y + (y + quietZone) * module,
                    ),
                    size = block,
                )
            }
        }
    }
}

/** A row of choices where exactly one is on. */
@Composable
fun <T> Pills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
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
