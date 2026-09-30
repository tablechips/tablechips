package io.github.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Standing up is going to the bar, not leaving: the seat is a place at a real
 * table, and the chips are the player's. Coming back gives both back.
 */
class SeatsTest {

    @Test
    fun `sitting back down returns you to your own seat`() {
        val table = seatedTable()
        table.accept(StandUp(ANNA))
        table.accept(StandUp(BRU))

        // Bru is back first and would take the lowest free seat, which is Anna's.
        table.accept(SitDown(BRU))
        val state = table.accept(SitDown(ANNA))

        assertEquals(0, state.player(ANNA)!!.seat)
        assertEquals(1, state.player(BRU)!!.seat)
    }

    @Test
    fun `if somebody took your seat meanwhile you get the first free one`() {
        val table = seatedTable()
        val dani = PlayerId("dani")
        table.accept(JoinTable(dani, "Dani"))
        table.accept(StandUp(BRU))
        table.accept(SitDown(dani, seat = 1))

        val state = table.accept(SitDown(BRU))

        assertEquals(1, state.player(dani)!!.seat)
        assertEquals(3, state.player(BRU)!!.seat)
    }

    @Test
    fun `choosing a seat still wins over the one you had`() {
        val table = seatedTable()
        table.accept(StandUp(BRU))

        assertEquals(5, table.accept(SitDown(BRU, seat = 5)).player(BRU)!!.seat)
    }

    @Test
    fun `coming back with chips does not buy in again`() {
        val table = seatedTable()
        table.accept(PlaceBet(BRU, 30))
        table.accept(StandUp(BRU))

        val state = table.accept(SitDown(BRU))

        assertEquals(70, state.player(BRU)!!.stack)
        assertEquals(100, state.player(BRU)!!.boughtIn)
        assertEquals("log.player_sat_back", state.log.last().key)
        assertTrue(state.balanced)
    }

    @Test
    fun `coming back with nothing left is a buy-in like the first one`() {
        val table = seatedTable()
        table.accept(PlaceBet(BRU, 100))
        table.accept(StandUp(BRU))

        val state = table.accept(SitDown(BRU))

        assertEquals(100, state.player(BRU)!!.stack)
        assertEquals(200, state.player(BRU)!!.boughtIn)
        assertEquals("log.player_sat", state.log.last().key)
    }

    @Test
    fun `the seat is remembered across a restart, because it is part of the fold`() {
        val table = seatedTable()
        table.accept(StandUp(ANNA))

        val restored = Table.restore(table.ledger())

        assertEquals(0, restored.snapshot().player(ANNA)!!.lastSeat)
    }
}
