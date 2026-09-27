package io.github.victormico.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two things a real table needs and a test table never notices: somebody
 * has to be able to be thrown out, and somebody whose phone forgot who they
 * were has to be able to get their chips back.
 */
class RecoveryTest {

    @Test
    fun `the host closes a seat and the chips leave with it`() {
        val table = seatedTable()
        table.accept(PlaceBet(BRU, 30))

        val state = table.accept(KickPlayer(ANNA, BRU))

        assertNull(state.player(BRU))
        assertEquals(70, state.bank.cashedOut)
        assertTrue(state.balanced)
    }

    @Test
    fun `only the host throws anybody out, and never themselves`() {
        val table = seatedTable()

        assertEquals(RuleError.NOT_HOST, table.reject(KickPlayer(BRU, CARME)))
        assertEquals(RuleError.NOT_YOURSELF, table.reject(KickPlayer(ANNA, ANNA)))
        assertEquals(RuleError.INVALID_TARGET, table.reject(KickPlayer(ANNA, PlayerId("ghost"))))
    }

    @Test
    fun `throwing somebody out is undoable like everything else`() {
        val table = seatedTable()
        val before = table.snapshot()

        table.accept(KickPlayer(ANNA, CARME))
        table.accept(UndoLast(ANNA))

        assertEquals(before, table.snapshot())
    }

    /**
     * The seat recovery the plan asks for: a phone in private browsing forgets
     * its id, the player joins again as somebody new, and the host hands them
     * back what was already theirs.
     */
    @Test
    fun `the host hands a seat over to whoever lost it`() {
        val table = seatedTable(defaultBuyIn = 500)
        table.accept(PlaceBet(BRU, 120))
        val lost = PlayerId("bru-again")
        table.accept(JoinTable(lost, "Bru"))

        val state = table.accept(TransferSeat(ANNA, from = BRU, to = lost))

        assertNull(state.player(BRU))
        val recovered = state.player(lost)!!
        assertEquals(1, recovered.seat)
        assertEquals(380, recovered.stack)
        assertEquals(500, recovered.boughtIn)
        // Nothing left the table: the chips did not move, the name over them did.
        assertEquals(0, state.bank.cashedOut)
        assertTrue(state.balanced)
    }

    @Test
    fun `a seat only goes to somebody who has none`() {
        val table = seatedTable()
        val watcher = PlayerId("dani")
        table.accept(JoinTable(watcher, "Dani"))

        assertEquals(RuleError.SEAT_NOT_FREE, table.reject(TransferSeat(ANNA, from = BRU, to = CARME)))
        assertEquals(RuleError.NOT_SEATED, table.reject(TransferSeat(ANNA, from = watcher, to = watcher)).let {
            table.reject(TransferSeat(ANNA, from = watcher, to = BRU))
        })
        assertEquals(RuleError.NOT_HOST, table.reject(TransferSeat(BRU, from = ANNA, to = watcher)))
    }

    @Test
    fun `a host who hands over their own seat hands over the table with it`() {
        val table = seatedTable()
        val newPhone = PlayerId("anna-again")
        table.accept(JoinTable(newPhone, "Anna"))

        val state = table.accept(TransferSeat(ANNA, from = ANNA, to = newPhone))

        assertTrue(state.player(newPhone)!!.isHost, "otherwise the table is left with no host")
        assertNull(state.player(ANNA))
    }

    @Test
    fun `both survive a replay of the ledger`() {
        val table = seatedTable()
        val lost = PlayerId("bru-again")
        table.accept(JoinTable(lost, "Bru"))
        table.accept(PlaceBet(BRU, 25))
        table.accept(TransferSeat(ANNA, from = BRU, to = lost))
        table.accept(KickPlayer(ANNA, CARME))

        assertEquals(table.snapshot(), Table.restore(table.ledger()).snapshot())
    }
}
