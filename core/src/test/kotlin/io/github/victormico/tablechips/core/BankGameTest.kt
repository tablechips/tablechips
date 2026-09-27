package io.github.victormico.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 7½ and blackjack: everybody plays alone against one banker. The app never
 * sees a card, so what it has to get right is the money — the stake out of the
 * stack, and the payout out of the bank.
 */
class BankGameTest {

    @Test
    fun `a stake leaves the stack without being anybody's yet`() {
        val table = bankTable()

        val state = table.accept(PlaceStake(BRU, 30))

        assertEquals(70, state.player(BRU)!!.stack)
        assertEquals(30, state.player(BRU)!!.stake)
        // The bank has not touched it: it is still on the table.
        assertEquals(100, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `losing gives the stake to the bank`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 30))

        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.LOSE))

        assertEquals(70, state.player(BRU)!!.stack)
        assertEquals(0, state.player(BRU)!!.stake)
        assertEquals(130, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `winning is paid by the bank, and a blackjack is paid three to two`() {
        val table = bankTable(naturalPays = Payout(3, 2))
        table.accept(PlaceStake(BRU, 30))
        table.accept(PlaceStake(CARME, 20))

        table.accept(SettleHand(ANNA, BRU, HandOutcome.WIN))
        val state = table.accept(SettleHand(ANNA, CARME, HandOutcome.NATURAL))

        assertEquals(130, state.player(BRU)!!.stack)
        assertEquals(130, state.player(CARME)!!.stack) // 80 + 20 back + 30
        assertEquals(100 - 30 - 30, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `an odd chip rounds the payout down, in the bank's favour`() {
        val table = bankTable(naturalPays = Payout(3, 2))
        table.accept(PlaceStake(BRU, 25))

        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.NATURAL))

        // 25 back plus 37, not 37.5: the table has no half chips.
        assertEquals(75 + 25 + 37, state.player(BRU)!!.stack)
        assertEquals(100 - 37, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a push gives the stake back and moves nothing else`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 30))

        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.PUSH))

        assertEquals(100, state.player(BRU)!!.stack)
        assertEquals(100, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a split hand is two outcomes over one pile of chips`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 40))

        table.accept(SettleHand(ANNA, BRU, HandOutcome.WIN, amount = 20))
        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.LOSE, amount = 20))

        // One half won 20, the other lost its 20: back where it started.
        assertEquals(100, state.player(BRU)!!.stack)
        assertEquals(0, state.player(BRU)!!.stake)
        assertEquals(100, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `the bank cannot pay what it does not have`() {
        val table = bankTable(defaultBuyIn = 20)
        table.accept(Rebuy(BRU, 80))
        table.accept(PlaceStake(BRU, 60))

        assertEquals(
            RuleError.INSUFFICIENT_CHIPS,
            table.reject(SettleHand(ANNA, BRU, HandOutcome.WIN)),
        )
        // Losing is always payable: the chips are already on the table.
        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.LOSE))
        assertEquals(80, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `the banker does not play against themselves`() {
        val table = bankTable()

        assertEquals(RuleError.BANKER_CANNOT_BET, table.reject(PlaceStake(ANNA, 10)))
        assertEquals(
            RuleError.BANKER_CANNOT_BET,
            table.reject(SettleHand(ANNA, ANNA, HandOutcome.WIN)),
        )
    }

    @Test
    fun `without a banker there is nothing to play against`() {
        val table = seatedTable()
        table.accept(SetConfig(ANNA, table.snapshot().config.copy(mode = GameMode.SEVEN_HALF)))

        assertEquals(RuleError.NO_BANKER, table.reject(PlaceStake(BRU, 10)))
    }

    @Test
    fun `the bank changes hands, and whoever takes it gets their own stake back`() {
        val table = bankTable(mode = GameMode.SEVEN_HALF, naturalPays = Payout(2, 1))
        table.accept(PlaceStake(BRU, 30))

        val state = table.accept(SetBanker(ANNA, BRU))

        assertEquals(BRU, state.banker)
        assertEquals(100, state.player(BRU)!!.stack)
        assertEquals(0, state.player(BRU)!!.stake)
        assertTrue(state.balanced)
    }

    @Test
    fun `set i mig pays a natural double, because the house rule says so`() {
        val table = bankTable(mode = GameMode.SEVEN_HALF, naturalPays = Payout(2, 1))
        table.accept(PlaceStake(BRU, 30))

        val state = table.accept(SettleHand(ANNA, BRU, HandOutcome.NATURAL))

        assertEquals(70 + 30 + 60, state.player(BRU)!!.stack)
        assertEquals(40, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a stake can be taken back before it is settled, and only by its owner`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 30))

        assertEquals(RuleError.NO_STAKE, table.reject(CancelStake(CARME)))
        val state = table.accept(CancelStake(BRU))

        assertEquals(100, state.player(BRU)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `nobody stands up holding chips that are up for a hand`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 30))

        val state = table.accept(StandUp(BRU))

        assertNull(state.player(BRU)!!.seat)
        assertEquals(100, state.player(BRU)!!.stack)
        assertEquals(0, state.player(BRU)!!.stake)
        assertTrue(state.balanced)
    }

    @Test
    fun `a banker who stands up hands the bank back to nobody`() {
        val table = bankTable()

        val state = table.accept(StandUp(ANNA))

        assertNull(state.banker)
    }

    @Test
    fun `leaving with a stake up takes those chips out of the game too`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 30))

        val state = table.accept(LeaveTable(BRU))

        assertNull(state.player(BRU))
        assertEquals(100, state.bank.cashedOut)
        assertTrue(state.balanced)
    }

    @Test
    fun `settling is a ledger entry like any other, so it goes back`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 30))
        val before = table.snapshot()

        table.accept(SettleHand(ANNA, BRU, HandOutcome.LOSE))
        val state = table.accept(UndoLast(ANNA))

        assertEquals(before.players.toSet(), state.players.toSet())
        assertTrue(state.balanced)
    }

    @Test
    fun `a manual table has no bank at all`() {
        val table = seatedTable()

        assertEquals(RuleError.WRONG_MODE, table.reject(PlaceStake(BRU, 10)))
        assertEquals(RuleError.WRONG_MODE, table.reject(SetBanker(ANNA, ANNA)))
    }

    @Test
    fun `the game does not change under chips that are up for a hand`() {
        val table = bankTable()
        table.accept(PlaceStake(BRU, 30))

        assertEquals(
            RuleError.INVALID_CONFIG,
            table.reject(SetConfig(ANNA, table.snapshot().config.copy(mode = GameMode.POKER))),
        )
    }
}
