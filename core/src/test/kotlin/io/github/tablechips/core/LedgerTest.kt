package io.github.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerTest {

    @Test
    fun `the first player to join is the host`() {
        val table = newTable()
        table.accept(JoinTable(ANNA, "Anna"))
        val state = table.accept(JoinTable(BRU, "Bru"))

        assertTrue(state.player(ANNA)!!.isHost)
        assertFalse(state.player(BRU)!!.isHost)
    }

    @Test
    fun `sitting down takes the first free seat and the default buy-in`() {
        val table = newTable(defaultBuyIn = 250)
        table.accept(JoinTable(ANNA, "Anna"))
        val state = table.accept(SitDown(ANNA))

        val anna = state.player(ANNA)!!
        assertEquals(0, anna.seat)
        assertEquals(250, anna.stack)
        assertEquals(250, anna.boughtIn)
        assertEquals(250, state.bank.boughtIn)
        assertTrue(state.balanced)
    }

    @Test
    fun `a bet moves chips from the stack to the pot and back on award`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 30))
        var state = table.accept(PlaceBet(BRU, 30))

        assertEquals(70, state.player(ANNA)!!.stack)
        assertEquals(60, state.pot(MAIN_POT)!!.amount)
        assertTrue(state.balanced)

        state = table.accept(AwardPot(actor = ANNA, to = BRU))

        assertEquals(0, state.pot(MAIN_POT)!!.amount)
        assertEquals(130, state.player(BRU)!!.stack)
        assertEquals(300, state.chipsOnTable)
        assertTrue(state.balanced)
    }

    @Test
    fun `chips are conserved by every accounting move`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 40))
        table.accept(PlaceBet(BRU, 40))
        table.accept(PlaceBet(CARME, 40))
        table.accept(AwardPot(actor = ANNA, to = CARME, amount = 60))
        table.accept(AwardPot(actor = ANNA, to = ANNA))
        table.accept(TransferChips(CARME, BRU, 10))
        table.accept(Rebuy(BRU, 100))
        val state = table.accept(AdjustStack(actor = ANNA, player = ANNA, delta = -5))

        assertTrue(state.balanced)
        assertEquals(400 - 5, state.chipsOnTable)
    }

    @Test
    fun `leaving cashes the remaining stack out of the table`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 20))
        val state = table.accept(LeaveTable(BRU))

        assertNull(state.player(BRU))
        assertEquals(100, state.bank.cashedOut)
        assertTrue(state.balanced)
    }

    @Test
    fun `standing up frees the seat but keeps the chips`() {
        val table = seatedTable()
        val state = table.accept(StandUp(BRU))

        assertNull(state.player(BRU)!!.seat)
        assertEquals(100, state.player(BRU)!!.stack)
        assertTrue(1 in state.freeSeats)
    }

    @Test
    fun `undo goes back one move at a time with no depth limit`() {
        val table = seatedTable()
        repeat(20) { table.accept(PlaceBet(ANNA, 1)) }

        assertEquals(80, table.snapshot().player(ANNA)!!.stack)

        repeat(20) { table.accept(UndoLast(ANNA)) }

        val state = table.snapshot()
        assertEquals(100, state.player(ANNA)!!.stack)
        assertEquals(0, state.pot(MAIN_POT)!!.amount)
        assertTrue(state.balanced)
    }

    @Test
    fun `undo stops at the opening of the table`() {
        val table = newTable()
        table.accept(JoinTable(ANNA, "Anna"))
        table.accept(UndoLast(ANNA))

        // Undoing the join removed the host, so nobody may undo any further.
        assertEquals(RuleError.NOT_HOST, table.reject(UndoLast(ANNA)))
        assertEquals(0, table.undoDepth)
        assertEquals("TEST", table.snapshot().roomCode)
    }

    @Test
    fun `only the host may undo`() {
        val table = seatedTable()
        table.accept(PlaceBet(BRU, 10))

        assertEquals(RuleError.NOT_HOST, table.reject(UndoLast(BRU)))
        assertEquals(90, table.snapshot().player(BRU)!!.stack)
    }

    @Test
    fun `a rejoin with the same name does not move the revision`() {
        val table = newTable()
        val joined = table.accept(JoinTable(ANNA, "Anna"))
        val rejoined = table.accept(JoinTable(ANNA, "Anna"))

        assertEquals(joined.rev, rejoined.rev)
        assertEquals(1, table.snapshot().players.size)
    }

    @Test
    fun `a rejoin under a different name renames instead of duplicating`() {
        val table = newTable()
        table.accept(JoinTable(ANNA, "Anna"))
        val state = table.accept(JoinTable(ANNA, "Anna M."))

        assertEquals(1, state.players.size)
        assertEquals("Anna M.", state.player(ANNA)!!.name)
    }

    @Test
    fun `presence is not part of the ledger and survives undo`() {
        val table = seatedTable()
        table.setConnected(ANNA, true)
        table.accept(PlaceBet(ANNA, 10))

        val depth = table.undoDepth
        table.setConnected(BRU, true)
        table.setConnected(BRU, false)

        assertEquals(depth, table.undoDepth)
        assertTrue(table.snapshot().player(ANNA)!!.connected)
        assertFalse(table.snapshot().player(BRU)!!.connected)

        table.accept(UndoLast(ANNA))
        assertTrue(table.snapshot().player(ANNA)!!.connected)
    }

    @Test
    fun `replaying the ledger rebuilds exactly the same table`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 25))
        table.accept(PlaceBet(BRU, 25))
        table.accept(AwardPot(actor = ANNA, to = BRU))
        table.accept(Rebuy(CARME, 50))
        table.accept(StandUp(CARME))

        val restored = Table.restore(table.ledger())

        assertEquals(table.snapshot(), restored.snapshot())
        assertEquals(table.undoDepth, restored.undoDepth)
    }

    @Test
    fun `the activity log keeps keys and arguments, never sentences`() {
        val table = newTable()
        table.accept(JoinTable(ANNA, "Anna"))
        val state = table.accept(SitDown(ANNA, seat = 3, buyIn = 40))

        val last = state.log.last()
        assertEquals("log.player_sat", last.key)
        assertEquals(ANNA, last.actor)
        assertEquals("4", last.args["seat"])
        assertEquals("40", last.args["amount"])
        assertEquals(state.rev, last.seq)
    }

    @Test
    fun `the log window travels bounded while the ledger keeps everything`() {
        val table = seatedTable(defaultBuyIn = 1_000)
        repeat(LOG_WINDOW + 20) { table.accept(PlaceBet(ANNA, 1)) }

        val state = table.snapshot()
        assertEquals(LOG_WINDOW, state.log.size)
        assertEquals(state.rev, state.log.last().seq)
        // Every ledger entry moved the revision once, the opening included.
        assertEquals(state.rev.toInt(), table.ledger().size)
        assertEquals(state.rev.toInt() - 1, table.undoDepth)
    }
}
