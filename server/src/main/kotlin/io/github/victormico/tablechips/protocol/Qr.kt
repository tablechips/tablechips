package io.github.victormico.tablechips.protocol

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * A QR code as its bare modules, with no quiet zone and no scaling: the app
 * draws it on a canvas and the server writes it as SVG, and both want the grid
 * rather than a bitmap.
 */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    /**
     * The code as an SVG, black modules on the one light surface of the
     * product. Squares, not rounded: a rounded module is a module a cheap
     * camera reads wrong in bad light.
     */
    fun toSvg(quietZone: Int = 4, dark: String = "#14110E", light: String = "#F2EAE0"): String {
        val side = size + quietZone * 2
        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $side $side" """)
            append("""shape-rendering="crispEdges" width="100%" height="100%">""")
            append("""<rect width="$side" height="$side" fill="$light"/>""")
            append("""<path fill="$dark" d="""")
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (this@QrMatrix[x, y]) append("M${x + quietZone},${y + quietZone}h1v1h-1z")
                }
            }
            append(""""/></svg>""")
        }
    }
}

/**
 * Medium error correction, as the design asks: enough to survive a thumb over
 * a corner, not so much that the modules shrink on a small screen.
 */
fun qrCode(text: String, correction: ErrorCorrectionLevel = ErrorCorrectionLevel.M): QrMatrix {
    val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
    val encoded = Encoder.encode(text, correction, hints).matrix
        ?: error("the encoder produced no matrix for $text")
    val size = encoded.width
    val modules = BooleanArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) modules[y * size + x] = encoded.get(x, y).toInt() == 1
    }
    return QrMatrix(size, modules)
}
