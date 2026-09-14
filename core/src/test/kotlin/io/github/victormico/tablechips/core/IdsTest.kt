package io.github.victormico.tablechips.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdsTest {

    @Test
    fun `room codes avoid characters people misread`() {
        val codes = List(500) { newRoomCode(Random(it)) }

        assertTrue(codes.all { it.length == 4 })
        assertTrue(codes.none { code -> code.any { it in "OI01" } })
    }

    @Test
    fun `player ids are long enough not to be guessed`() {
        val id = newPlayerId(Random(7))

        assertEquals(16, id.value.length)
        assertTrue(id.value.all { it.isLetterOrDigit() })
    }

    @Test
    fun `ids from different sources do not repeat in practice`() {
        val ids = List(2_000) { newPlayerId(Random(it.toLong())) }.toSet()

        assertEquals(2_000, ids.size)
    }
}
