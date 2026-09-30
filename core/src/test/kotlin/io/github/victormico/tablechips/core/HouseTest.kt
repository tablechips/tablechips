package io.github.victormico.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blackjack is banked by the house: chips of its own, bought in like anybody's,
 * that sit nowhere and are nobody's. Whoever deals runs it; the dealer's own
 * stack never moves.
 */
class HouseTest {

    /** Anna deals, the house holds 200, Bru and Carme play. */
    private fun blackjack(house: Long = 200): Table = bankTable(mode = GameMode.BLACKJACK).apply {
        if (house > 0) accept(FundHouse(ANNA, house))
    }

    @Test
    fun `the house is bought into, and its chips are on the table`() {
        val table = blackjack(house = 0)

        val state = table.accept(FundHouse(ANNA, 150))

        assertEquals(150, state.house)
        assertEquals(100, state.player(ANNA)!!.stack)
        assertEquals(450, state.bank.boughtIn)
        assertTrue(state.balanced)
    }

    @Test
    fun `the house pays and collects, not the dealer`() {
        val table = blackjack()
        table.accept(PlaceStake(BRU, 30))
        table.accept(PlaceStake(CARME, 20))

        table.accept(SettleHand(ANNA, BRU, HandOutcome.NATURAL))
        val state = table.accept(SettleHand(ANNA, CARME, HandOutcome.LOSE))

        assertEquals(70 + 30 + 45, state.player(BRU)!!.stack)
        assertEquals(80, state.player(CARME)!!.stack)
        assertEquals(200 - 45 + 20, state.house)
        assertEquals(100, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a house that cannot pay says so, and a buy-in lets it`() {
        val table = blackjack(house = 20)
        table.accept(PlaceStake(BRU, 30))

        assertEquals(RuleError.BANK_CANNOT_PAY, table.reject(SettleHand(ANNA, BRU, HandOutcome.WIN)))
        table.accept(FundHouse(ANNA, 100))
        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.WIN))

        assertEquals(90, state.house)
        assertTrue(state.balanced)
    }

    @Test
    fun `doubling puts the same chips on the hand again`() {
        val table = blackjack()
        table.accept(PlaceStake(BRU, 25))

        val doubled = table.accept(DoubleStake(BRU))
        assertEquals(listOf(50L), doubled.player(BRU)!!.hands)
        assertEquals(50, doubled.player(BRU)!!.stake)
        assertEquals(50, doubled.player(BRU)!!.stack)

        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.WIN))
        assertEquals(150, state.player(BRU)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a split hand is two hands, each settled on its own`() {
        val table = blackjack()
        table.accept(PlaceStake(BRU, 20))

        val split = table.accept(SplitStake(BRU))
        assertEquals(listOf(20L, 20L), split.player(BRU)!!.hands)
        // The second hand is doubled: 20 and 40 up, 40 left.
        table.accept(DoubleStake(BRU, hand = 1))

        table.accept(SettleHand(ANNA, BRU, HandOutcome.PUSH, hand = 0))
        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.WIN, hand = 0))

        assertEquals(emptyList(), state.player(BRU)!!.hands)
        assertEquals(40 + 20 + 40 + 40, state.player(BRU)!!.stack)
        assertEquals(
            listOf(SettledHand(HandOutcome.PUSH, 0), SettledHand(HandOutcome.WIN, 40)),
            state.player(BRU)!!.settled,
        )
        assertTrue(state.balanced)
    }

    @Test
    fun `a hand can only be doubled or split with the chips to do it`() {
        val table = blackjack()
        table.accept(PlaceStake(BRU, 60))

        assertEquals(RuleError.INSUFFICIENT_CHIPS, table.reject(DoubleStake(BRU)))
        assertEquals(RuleError.NO_SUCH_HAND, table.reject(SplitStake(BRU, hand = 3)))
        assertEquals(RuleError.NO_SUCH_HAND, table.reject(DoubleStake(CARME)))
    }

    @Test
    fun `surrendering gives half the stake back, the odd chip to the house`() {
        val table = blackjack()
        table.accept(PlaceStake(BRU, 25))

        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.SURRENDER))

        assertEquals(75 + 12, state.player(BRU)!!.stack)
        assertEquals(213, state.house)
        assertTrue(state.balanced)
    }

    @Test
    fun `doubling and splitting are blackjack`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 20))

        assertEquals(RuleError.WRONG_MODE, table.reject(DoubleStake(BRU)))
        assertEquals(RuleError.WRONG_MODE, table.reject(FundHouse(ANNA, 10)))
    }

    @Test
    fun `a repeat stakes what was staked last time, and a new stake clears the last results`() {
        val table = blackjack()
        table.accept(PlaceStake(BRU, 25))
        table.accept(DoubleStake(BRU))
        val settled = table.accept(SettleHand(ANNA, BRU, HandOutcome.LOSE))
        assertEquals(25, settled.player(BRU)!!.lastStake)
        assertEquals(1, settled.player(BRU)!!.settled.size)

        val state = table.accept(PlaceStake(BRU, 10))

        assertEquals(10, state.player(BRU)!!.lastStake)
        assertEquals(emptyList(), state.player(BRU)!!.settled)
    }
}

/** Running a hand of a bank game from wherever the bank is sitting. */
class BankRunTest {

    @Test
    fun `whoever holds the bank settles hands against it, and nobody else but the host`() {
        val table = bankTable()
        table.accept(SetBanker(ANNA, BRU))
        table.accept(PlaceStake(CARME, 30))

        assertEquals(RuleError.NOT_HOST, table.reject(SettleHand(CARME, CARME, HandOutcome.WIN)))
        val state = table.accept(SettleHand(BRU, CARME, HandOutcome.WIN))

        assertEquals(130, state.player(CARME)!!.stack)
        assertEquals(70, state.player(BRU)!!.stack)
    }

    @Test
    fun `the bank busts and pays everybody left in one go`() {
        val table = bankTable(defaultBuyIn = 200)
        table.accept(PlaceStake(BRU, 30))
        table.accept(PlaceStake(CARME, 20))
        // Carme had already gone over: she is settled on her own first.
        table.accept(SettleHand(ANNA, CARME, HandOutcome.LOSE))

        val state = table.accept(SettleAll(ANNA, HandOutcome.WIN))

        assertEquals(230, state.player(BRU)!!.stack)
        assertEquals(180, state.player(CARME)!!.stack)
        assertEquals(200 + 20 - 30, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `nothing is paid in one go that the bank cannot cover`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 60))
        table.accept(PlaceStake(CARME, 60))

        assertEquals(RuleError.BANK_CANNOT_PAY, table.reject(SettleAll(ANNA, HandOutcome.WIN)))
        assertEquals(RuleError.INVALID_TARGET, table.reject(SettleAll(ANNA, HandOutcome.NATURAL)))
        val state = table.accept(SettleAll(ANNA, HandOutcome.LOSE))
        assertEquals(220, state.player(ANNA)!!.stack)
    }

    @Test
    fun `with the house rule, a set i mig takes the bank once the hand is settled`() {
        val table = bankTable(naturalPays = Payout(2, 1), defaultBuyIn = 200)
        table.accept(SetConfig(ANNA, table.snapshot().config.copy(naturalTakesBank = true)))
        table.accept(PlaceStake(BRU, 10))
        table.accept(PlaceStake(CARME, 10))

        val first = table.accept(SettleHand(ANNA, BRU, HandOutcome.NATURAL))
        // Carme still has to be settled by the bank that is paying.
        assertEquals(ANNA, first.banker)
        assertEquals(BRU, first.pendingBanker)

        val state = table.accept(SettleHand(ANNA, CARME, HandOutcome.WIN))
        assertEquals(BRU, state.banker)
        assertNull(state.pendingBanker)
        assertEquals("log.banker_changed", state.log.last().key)
        assertTrue(state.balanced)
    }

    @Test
    fun `without the house rule, a set i mig is just paid`() {
        val table = bankTable(naturalPays = Payout(2, 1))
        table.accept(PlaceStake(BRU, 10))

        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.NATURAL))

        assertEquals(ANNA, state.banker)
        assertNull(state.pendingBanker)
    }
}
