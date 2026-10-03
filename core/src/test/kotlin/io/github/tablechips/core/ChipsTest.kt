package io.github.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The stack as chips. The same cases are checked against the web client's
 * copy of this function, so both screens draw the same stack.
 */
class ChipsTest {

    private fun counts(amount: Long): List<Long> = chipsIn(amount).map { it.count }

    @Test
    fun `a fresh deal is five of each`() {
        assertEquals(905, STANDARD_BUY_IN)
        assertEquals(listOf(5L, 5, 5, 5, 5), counts(905))
        assertEquals(STANDARD_BUY_IN, TableConfig().defaultBuyIn)
    }

    @Test
    fun `betting from a fresh deal looks like the chips that were pushed`() {
        // 105 is a 100 and a 5 pushed forward.
        assertEquals(listOf(4L, 5, 5, 4, 5), counts(800))
    }

    @Test
    fun `small chips never pile up past five, the rest goes into the biggest`() {
        assertEquals(listOf(6L, 5, 5, 4, 5), counts(1000))
        assertEquals(listOf(96L, 5, 5, 4, 5), counts(10_000))
    }

    @Test
    fun `small stacks use the chips that make them`() {
        assertEquals(listOf(0L, 0, 0, 0, 0), counts(0))
        assertEquals(listOf(0L, 0, 0, 0, 3), counts(3))
        assertEquals(listOf(0L, 0, 0, 2, 2), counts(12))
        assertEquals(listOf(0L, 0, 3, 4, 5), counts(100))
    }

    @Test
    fun `every breakdown adds up to the stack`() {
        (0L..3_000L).forEach { amount ->
            val chips = chipsIn(amount)
            assertEquals(amount, chips.sumOf { it.value * it.count }, "for $amount")
            // Only the biggest chip ever goes past five.
            chips.drop(1).forEach { assertTrue(it.count <= CHIPS_PER_DENOMINATION, "for $amount: $chips") }
        }
    }

    @Test
    fun `each value divides the next, or a stack could not be broken down`() {
        DENOMINATIONS.zipWithNext().forEach { (bigger, smaller) ->
            assertEquals(0, bigger % smaller, "$bigger over $smaller")
        }
    }

    @Test
    fun `a negative stack is a bug, not a picture`() {
        assertFailsWith<IllegalArgumentException> { chipsIn(-1) }
    }

    @Test
    fun `a bet goes in as the fewest chips`() {
        assertEquals(listOf(1L, 1, 0, 0, 0), fewestChips(150).map { it.count })
        assertEquals(listOf(9L, 0, 0, 1, 0), fewestChips(905).map { it.count })
        assertEquals(listOf(0L, 0, 1, 1, 3), fewestChips(33).map { it.count })
        (0L..3_000L).forEach { assertEquals(it, fewestChips(it).total(), "for $it") }
    }
}
