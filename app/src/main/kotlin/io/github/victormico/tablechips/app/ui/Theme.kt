package io.github.victormico.tablechips.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.victormico.tablechips.app.R

/**
 * Direction "Refugi": night at a mountain hut. Warm smoke black, dark wood
 * surfaces and a single metal, matt brass.
 *
 * Dark only. A light mode has not been designed and guessing one would break
 * the identity.
 *
 * The rules that are not up for negotiation: brass fills exactly one action per
 * screen; warn and loss never fill anything, only text and borders, so the
 * primary action and the destructive one differ in shape and not merely in
 * hue; no gradients, no coloured shadows, no glow.
 */
object Refugi {
    val bg = Color(0xFF14110E)
    val surface = Color(0xFF1F1A16)
    val surfaceHigh = Color(0xFF2A231D)
    val line = Color(0xFF3A312A)
    val lineStrong = Color(0xFF4A3E33)
    val lineAccent = Color(0xFF6B5A3E)
    val lineDanger = Color(0xFF5A3B33)
    val text = Color(0xFFF2EAE0)
    val text2 = Color(0xFFA99C8E)
    val accent = Color(0xFFC69A4E)
    val accentHover = Color(0xFFD8AC5E)
    val gain = Color(0xFF86A96B)
    val loss = Color(0xFFCC5B42)
    val warn = Color(0xFFEBC15E)
    val onAccent = Color(0xFF14110E)

    /** Side margin of every screen. */
    val side = 18.dp
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun sans(weight: Int) = Font(
    resId = R.font.instrument_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Interface: labels, buttons, names, running text. */
val Sans = FontFamily(sans(400), sans(500), sans(600), sans(700))

/**
 * Everything that is a number, always. Amounts change constantly and must not
 * wobble in width, hence the tabular figures in every style below.
 */
val Mono = FontFamily(
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

object Type {
    /** The mother figure: the player's own stack. */
    val stack = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 72.sp,
        lineHeight = 72.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR,
    )
    val amount = stack.copy(fontSize = 64.sp, lineHeight = 64.sp)
    val metric = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp,
        lineHeight = 34.sp, fontFeatureSettings = TABULAR,
    )
    val brand = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp,
        lineHeight = 37.sp, letterSpacing = (-0.02).em, textAlign = TextAlign.Center,
    )
    val primary = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp,
    )
    val primaryValue = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
        lineHeight = 21.sp, fontFeatureSettings = TABULAR,
    )
    val secondary = primary.copy(fontSize = 16.sp, lineHeight = 18.sp)
    val name = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 18.sp,
    )
    val nameStrong = name.copy(fontWeight = FontWeight.SemiBold)
    val chips = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        lineHeight = 16.sp, fontFeatureSettings = TABULAR,
    )
    val body = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp,
    )
    val bodyLarge = body.copy(fontSize = 14.sp, lineHeight = 21.sp)
    val title = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp,
    )
    /** Small caps label heading a block. */
    val caption = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp,
        lineHeight = 11.sp, letterSpacing = 0.10.em,
    )
    /** The address a guest types, on the one light surface of the product. */
    val address = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        lineHeight = 20.sp, fontFeatureSettings = TABULAR,
    )
}

/**
 * Chips are whole numbers and the thousands separator is a narrow no-break
 * space in all three languages, so "1 250" has the same width everywhere.
 */
fun chips(value: Long): String {
    val digits = StringBuilder()
    val text = kotlin.math.abs(value).toString()
    text.forEachIndexed { index, digit ->
        if (index > 0 && (text.length - index) % 3 == 0) digits.append(' ')
        digits.append(digit)
    }
    return (if (value < 0) "−" else "") + digits
}
