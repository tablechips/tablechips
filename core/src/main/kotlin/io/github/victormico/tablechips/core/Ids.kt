package io.github.victormico.tablechips.core

import kotlin.random.Random

/**
 * Alphabet for codes a human reads off a screen and types on another device:
 * no O/0, no I/1, no confusing pairs.
 */
private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

/** Four characters is plenty: the code only has to be unique on one hotspot. */
fun newRoomCode(random: Random = Random.Default, length: Int = 4): String =
    buildString { repeat(length) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }

/**
 * A client keeps this for the whole session and reuses it to get its seat back
 * after a reconnection, so it has to be long enough not to collide or be guessed.
 */
fun newPlayerId(random: Random = Random.Default, length: Int = 16): PlayerId =
    PlayerId(buildString { repeat(length) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } })
