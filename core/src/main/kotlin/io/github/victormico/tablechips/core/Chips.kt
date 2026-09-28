package io.github.victormico.tablechips.core

/**
 * The chips of a standard case, largest first. Each value divides the next
 * one up, which is what lets a stack be broken down without leftovers.
 */
val DENOMINATIONS: List<Long> = listOf(100, 50, 25, 5, 1)

/** A deal gives this many of each chip. */
const val CHIPS_PER_DENOMINATION: Int = 5

/** Five of each chip: what a player is dealt at a real table, 905. */
val STANDARD_BUY_IN: Long = DENOMINATIONS.sum() * CHIPS_PER_DENOMINATION

/** How many chips of one value a stack holds. */
data class ChipCount(val value: Long, val count: Long)

/**
 * A stack as a player at a real table would hold it, largest value first.
 *
 * Not the fewest chips: that would show a fresh 905 as nine 100s and a 5,
 * when what is in front of the player after the deal is five of each. So the
 * small chips come first — never more than five of each, which is what a deal
 * gives — and everything above that goes into the biggest chip.
 *
 * Each small value takes what no bigger chip can make, plus whole groups of
 * itself while it stays within five. That keeps the breakdown stable as the
 * stack moves: betting 105 from 905 gives four 100s, five 50s, five 25s, four
 * 5s and five 1s, which is exactly the chips a player would have pushed.
 *
 * The app counts amounts, not physical chips, so this is a picture of the
 * number and never a claim about what is on the felt.
 */
fun chipsIn(amount: Long): List<ChipCount> {
    require(amount >= 0) { "a stack is never negative" }
    val ascending = DENOMINATIONS.sorted()
    val counts = mutableMapOf<Long, Long>()
    var rest = amount
    ascending.forEachIndexed { index, value ->
        val next = ascending.getOrNull(index + 1)
        val count = if (next == null) {
            rest / value
        } else {
            val group = next / value
            // What the bigger chips cannot make has to be made of this one.
            var taken = (rest % next) / value
            while (taken + group <= CHIPS_PER_DENOMINATION && (taken + group) * value <= rest) {
                taken += group
            }
            taken
        }
        counts[value] = count
        rest -= count * value
    }
    return DENOMINATIONS.map { ChipCount(it, counts.getValue(it)) }
}
