package io.github.victormico.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Poker. The app deals nothing and never decides who won; what it owes the
 * table is the arithmetic humans get wrong at two in the morning — what a call
 * costs, and who can win how much when somebody is all-in for less.
 *
 * Whose turn it is is deliberately not modelled: at a real table that is
 * settled by the people sitting at it, and software that disagreed with them
 * would only get in the way.
 */
class PokerTest {

    @Test
    fun `a new hand moves the button and posts the blinds`() {
        val table = pokerTable()

        val state = table.accept(StartHand(ANNA))

        assertEquals(0, state.button)
        // Button at seat 0, small blind at seat 1, big blind at seat 2.
        assertEquals(100, state.player(ANNA)!!.stack)
        assertEquals(95, state.player(BRU)!!.stack)
        assertEquals(90, state.player(CARME)!!.stack)
        assertEquals(15, state.pot(MAIN_POT)!!.amount)
        assertEquals(10, state.currentBet)
        assertEquals(10, state.toCall(ANNA))
        assertEquals(5, state.toCall(BRU))
        assertEquals(0, state.toCall(CARME))
        assertTrue(state.balanced)
    }

    @Test
    fun `the button goes round`() {
        val table = pokerTable()

        assertEquals(0, table.accept(StartHand(ANNA)).button)
        assertEquals(1, table.accept(StartHand(ANNA)).button)
        assertEquals(2, table.accept(StartHand(ANNA)).button)
        assertEquals(0, table.accept(StartHand(ANNA)).button)
    }

    @Test
    fun `heads-up, the button posts the small blind`() {
        val table = pokerTable()
        table.accept(StandUp(CARME))

        val state = table.accept(StartHand(ANNA))

        assertEquals(0, state.button)
        assertEquals(95, state.player(ANNA)!!.stack)
        assertEquals(90, state.player(BRU)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a blind is capped by the stack behind it`() {
        val table = pokerTable(defaultBuyIn = 3)

        val state = table.accept(StartHand(ANNA))

        assertEquals(0, state.player(BRU)!!.stack)
        assertEquals(3, state.player(BRU)!!.committed)
        assertEquals(0, state.player(CARME)!!.stack)
        assertEquals(3, state.player(CARME)!!.committed)
        assertTrue(state.balanced)
    }

    @Test
    fun `a bet is also a contribution to the hand, and what the next call has to match`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))

        val state = table.accept(PlaceBet(ANNA, 30))

        assertEquals(30, state.player(ANNA)!!.roundBet)
        assertEquals(30, state.player(ANNA)!!.committed)
        assertEquals(30, state.currentBet)
        assertEquals(25, state.toCall(BRU))
        assertEquals(20, state.toCall(CARME))
        assertTrue(state.balanced)
    }

    @Test
    fun `closing a round forgets the bets but not what went in`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 10))
        table.accept(PlaceBet(BRU, 5))

        val state = table.accept(CloseRound(ANNA))

        assertEquals(0, state.currentBet)
        assertEquals(0, state.player(ANNA)!!.roundBet)
        assertEquals(10, state.player(ANNA)!!.committed)
        assertEquals(10, state.player(BRU)!!.committed)
        assertEquals(0, state.toCall(ANNA))
        assertTrue(state.balanced)
    }

    @Test
    fun `folding leaves the chips in the pot`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))
        val potBefore = table.snapshot().pot(MAIN_POT)!!.amount

        val state = table.accept(Fold(CARME))

        assertTrue(state.player(CARME)!!.folded)
        assertEquals(potBefore, state.pot(MAIN_POT)!!.amount)
        assertEquals(10, state.player(CARME)!!.committed)
        assertTrue(state.balanced)
    }

    @Test
    fun `an all-in for less can only win what it matched`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        table.accept(Rebuy(ANNA, 100))
        table.accept(Rebuy(BRU, 100))
        table.accept(Rebuy(CARME, 30))
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(CARME, 30)) // all in
        table.accept(PlaceBet(ANNA, 100))
        table.accept(PlaceBet(BRU, 100))

        val state = table.accept(SplitPots(ANNA))

        assertEquals(2, state.pots.size)
        val main = state.pots[0]
        val side = state.pots[1]
        assertEquals(90, main.amount)
        assertEquals(setOf(ANNA, BRU, CARME), main.eligible!!.toSet())
        assertEquals(140, side.amount)
        assertEquals(setOf(ANNA, BRU), side.eligible!!.toSet())
        assertEquals(230, state.pots.sumOf { it.amount })
        assertTrue(state.balanced)
    }

    @Test
    fun `chips nobody could call come back to whoever put them in`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        table.accept(Rebuy(ANNA, 100))
        table.accept(Rebuy(BRU, 40))
        table.accept(Rebuy(CARME, 40))
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 100))
        table.accept(PlaceBet(BRU, 40))
        table.accept(Fold(CARME))

        val state = table.accept(SplitPots(ANNA))

        // Both matched 40; the 60 above that was never called, so it is Anna's.
        assertEquals(80, state.pots[0].amount)
        assertEquals(setOf(ANNA, BRU), state.pots[0].eligible!!.toSet())
        assertEquals(60, state.pots[1].amount)
        assertEquals(listOf(ANNA), state.pots[1].eligible)
        assertTrue(state.balanced)
    }

    @Test
    fun `a folded player's chips stay in the pot the others play for`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        table.accept(Rebuy(ANNA, 100))
        table.accept(Rebuy(BRU, 100))
        table.accept(Rebuy(CARME, 20))
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(CARME, 20))
        table.accept(Fold(CARME))
        table.accept(PlaceBet(ANNA, 60))
        table.accept(PlaceBet(BRU, 60))

        // The two still in matched each other, so there are no layers to cut:
        // one pot of 140, Carme's 20 included, for the host to award.
        assertEquals(RuleError.NOTHING_TO_SPLIT, table.reject(SplitPots(ANNA)))
        val state = table.snapshot()
        assertEquals(140, state.pots.sumOf { it.amount })
        assertEquals(20, state.player(CARME)!!.committed)
        assertTrue(state.balanced)
    }

    @Test
    fun `with everybody matched there is nothing to split`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 10))
        table.accept(PlaceBet(BRU, 5))

        // Three players, ten each in: one pot, and the host awards it as always.
        assertEquals(RuleError.NOTHING_TO_SPLIT, table.reject(SplitPots(ANNA)))
        assertEquals(1, table.snapshot().pots.size)
    }

    @Test
    fun `a pot the host has been moving by hand is left alone`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        table.accept(Rebuy(ANNA, 100))
        table.accept(Rebuy(BRU, 100))
        table.accept(Rebuy(CARME, 30))
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(CARME, 30))
        table.accept(PlaceBet(ANNA, 100))
        table.accept(PlaceBet(BRU, 100))
        // The host hands part of it out before splitting: what is in the pot no
        // longer matches what went in, and splitting it would invent chips.
        table.accept(AwardPot(ANNA, ANNA, MAIN_POT, 50))

        assertEquals(RuleError.NOTHING_TO_SPLIT, table.reject(SplitPots(ANNA)))
    }

    @Test
    fun `side pots are awarded like any other pot`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        table.accept(Rebuy(ANNA, 100))
        table.accept(Rebuy(BRU, 100))
        table.accept(Rebuy(CARME, 30))
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(CARME, 30))
        table.accept(PlaceBet(ANNA, 100))
        table.accept(PlaceBet(BRU, 100))
        val pots = table.accept(SplitPots(ANNA)).pots

        table.accept(AwardPot(ANNA, CARME, pots[0].id))
        val state = table.accept(AwardPot(ANNA, BRU, pots[1].id))

        assertEquals(90, state.player(CARME)!!.stack)
        assertEquals(140, state.player(BRU)!!.stack)
        assertEquals(0, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a side pot cannot be given to somebody who did not pay into it`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        table.accept(Rebuy(ANNA, 100))
        table.accept(Rebuy(BRU, 100))
        table.accept(Rebuy(CARME, 30))
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(CARME, 30))
        table.accept(PlaceBet(ANNA, 100))
        table.accept(PlaceBet(BRU, 100))
        val side = table.accept(SplitPots(ANNA)).pots[1]

        // Enforced by the rules, not only hidden by the screens.
        assertEquals(RuleError.INVALID_TARGET, table.reject(AwardPot(ANNA, CARME, side.id)))

        // The way out for a host who disagrees: undo the split, award by hand.
        table.accept(UndoLast(ANNA))
        val state = table.accept(AwardPot(ANNA, CARME, MAIN_POT, 50))
        assertEquals(50, state.player(CARME)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a new hand clears the last one and drops nothing`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 20))
        table.accept(Fold(BRU))

        val state = table.accept(StartHand(ANNA))

        assertFalse(state.player(BRU)!!.folded)
        // The button moved to Bru, so Bru posts nothing this hand.
        assertEquals(0, state.player(BRU)!!.committed)
        assertEquals(1, state.pots.size)
        // 35 left lying from the hand before, plus this hand's blinds.
        assertEquals(35 + 15, state.pot(MAIN_POT)!!.amount)
        assertTrue(state.balanced)
    }

    @Test
    fun `standing up mid-hand is folding, not a way out with the chips`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 20))

        val state = table.accept(StandUp(ANNA))

        assertNull(state.player(ANNA)!!.seat)
        assertTrue(state.player(ANNA)!!.folded)
        assertEquals(35, state.pot(MAIN_POT)!!.amount)
        assertTrue(state.balanced)
    }

    @Test
    fun `only the host runs the hand, and only in a game of poker`() {
        val table = pokerTable()

        assertEquals(RuleError.NOT_HOST, table.reject(StartHand(BRU)))
        assertEquals(RuleError.NOT_HOST, table.reject(CloseRound(BRU)))
        assertEquals(RuleError.NOT_HOST, table.reject(SplitPots(BRU)))

        val manual = seatedTable()
        assertEquals(RuleError.WRONG_MODE, manual.reject(StartHand(ANNA)))
        assertEquals(RuleError.WRONG_MODE, manual.reject(Fold(BRU)))
    }

    @Test
    fun `a hand needs somebody to play it with`() {
        val table = pokerTable()
        table.accept(StandUp(BRU))
        table.accept(StandUp(CARME))

        assertEquals(RuleError.NOT_ENOUGH_PLAYERS, table.reject(StartHand(ANNA)))
    }

    @Test
    fun `everything in a hand is undoable, one entry at a time`() {
        val table = pokerTable()
        val before = table.snapshot()
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 20))
        table.accept(Fold(BRU))

        table.accept(UndoLast(ANNA))
        table.accept(UndoLast(ANNA))
        val state = table.accept(UndoLast(ANNA))

        assertEquals(before.players.toSet(), state.players.toSet())
        assertEquals(0, state.pot(MAIN_POT)!!.amount)
        assertNull(state.button)
        assertTrue(state.balanced)
    }

    @Test
    fun `a bet has to at least match what is owed`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))

        // Anna owes the big blind, 10: 5 is not a bet the game allows.
        assertEquals(RuleError.BELOW_CALL, table.reject(PlaceBet(ANNA, 5)))
        // Calling is exactly the owed amount; raising is anything above it.
        assertEquals(10, table.accept(PlaceBet(ANNA, 10)).player(ANNA)!!.roundBet)
        assertEquals(RuleError.BELOW_CALL, table.reject(PlaceBet(BRU, 4)))
        assertEquals(30, table.accept(PlaceBet(BRU, 25)).player(BRU)!!.roundBet)
    }

    @Test
    fun `all-in for less than the call is the one short bet allowed`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 5, bigBlind = 10)
        table.accept(Rebuy(ANNA, 7))
        table.accept(Rebuy(BRU, 100))
        table.accept(Rebuy(CARME, 100))
        table.accept(StartHand(ANNA))

        val state = table.accept(PlaceBet(ANNA, 7))

        assertEquals(0, state.player(ANNA)!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `outside poker a bet is whatever the player says`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 30))

        assertEquals(1, table.accept(PlaceBet(BRU, 1)).pot(MAIN_POT)!!.amount - 30)
    }

    @Test
    fun `awarding the whole pot deals the next hand`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))            // button 0, blinds 5 and 10
        table.accept(PlaceBet(ANNA, 10))
        table.accept(PlaceBet(BRU, 5))

        val state = table.accept(AwardPot(ANNA, CARME, MAIN_POT))

        // Carme had 90 after her big blind and took the 30. Then the button
        // moved to Bru, so Carme posts the small blind and Anna the big one.
        assertEquals(1, state.button)
        assertEquals(115, state.player(CARME)!!.stack)
        assertEquals(10, state.player(ANNA)!!.roundBet)
        assertEquals(15, state.pot(MAIN_POT)!!.amount)
        assertTrue(state.log.last().key == "log.hand_started")
        assertTrue(state.balanced)
    }

    @Test
    fun `a pot split between winners keeps the hand open until the last of it goes`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 10))
        table.accept(PlaceBet(BRU, 5))

        val half = table.accept(AwardPot(ANNA, ANNA, MAIN_POT, 15))
        assertEquals(0, half.button)
        assertEquals(15, half.pot(MAIN_POT)!!.amount)

        val rest = table.accept(AwardPot(ANNA, BRU, MAIN_POT))
        assertEquals(1, rest.button)
        assertTrue(rest.balanced)
    }

    @Test
    fun `with a side pot still to give, the hand is not over`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        table.accept(Rebuy(ANNA, 100))
        table.accept(Rebuy(BRU, 100))
        table.accept(Rebuy(CARME, 30))
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(CARME, 30))
        table.accept(PlaceBet(ANNA, 100))
        table.accept(PlaceBet(BRU, 100))
        val pots = table.accept(SplitPots(ANNA)).pots

        val main = table.accept(AwardPot(ANNA, CARME, pots[0].id))
        assertEquals(0, main.button)

        val side = table.accept(AwardPot(ANNA, BRU, pots[1].id))
        assertEquals(1, side.button)
        assertEquals(1, side.pots.size)
        assertTrue(side.balanced)
    }

    @Test
    fun `with one player left holding chips, there is no hand to deal`() {
        val table = pokerTable(defaultBuyIn = 0, smallBlind = 5, bigBlind = 10)
        table.accept(Rebuy(ANNA, 5))
        table.accept(Rebuy(BRU, 10))
        table.accept(StandUp(CARME))
        table.accept(StartHand(ANNA))              // both all in on the blinds

        val state = table.accept(AwardPot(ANNA, BRU, MAIN_POT))

        assertEquals(15, state.player(BRU)!!.stack)
        assertEquals(0, state.button)
        assertTrue(state.log.last().key == "log.pot_awarded")
    }

    @Test
    fun `the new hand and the award come back off the ledger one at a time`() {
        val table = pokerTable()
        table.accept(StartHand(ANNA))
        table.accept(PlaceBet(ANNA, 10))
        table.accept(PlaceBet(BRU, 5))
        table.accept(AwardPot(ANNA, CARME, MAIN_POT))

        // Until the ledger groups a command's entries (#5), undo takes back the
        // new hand first and the award second.
        assertEquals(0, table.accept(UndoLast(ANNA)).button)
        assertEquals(30, table.accept(UndoLast(ANNA)).pot(MAIN_POT)!!.amount)
    }
}
