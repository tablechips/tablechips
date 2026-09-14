package io.github.victormico.tablechips.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulesTest {

    @Test
    fun `a name is required and bounded`() {
        val table = newTable()

        assertEquals(RuleError.NAME_REQUIRED, table.reject(JoinTable(ANNA, "   ")))
        assertEquals(RuleError.NAME_TOO_LONG, table.reject(JoinTable(ANNA, "x".repeat(MAX_NAME_LENGTH + 1))))
        assertEquals("Anna", table.accept(JoinTable(ANNA, "  Anna  ")).player(ANNA)!!.name)
    }

    @Test
    fun `unknown players cannot do anything`() {
        val table = seatedTable()

        assertEquals(RuleError.UNKNOWN_PLAYER, table.reject(SitDown(PlayerId("ghost"))))
        assertEquals(RuleError.UNKNOWN_PLAYER, table.reject(PlaceBet(PlayerId("ghost"), 10)))
        assertEquals(RuleError.UNKNOWN_PLAYER, table.reject(StandUp(PlayerId("ghost"))))
    }

    @Test
    fun `seats are exclusive and bounded`() {
        val table = newTable(seatCount = 3)
        table.accept(JoinTable(ANNA, "Anna"))
        table.accept(JoinTable(BRU, "Bru"))
        table.accept(SitDown(ANNA, seat = 1))

        assertEquals(RuleError.SEAT_TAKEN, table.reject(SitDown(BRU, seat = 1)))
        assertEquals(RuleError.SEAT_OUT_OF_RANGE, table.reject(SitDown(BRU, seat = 3)))
        assertEquals(RuleError.SEAT_OUT_OF_RANGE, table.reject(SitDown(BRU, seat = -1)))
        assertEquals(RuleError.ALREADY_SEATED, table.reject(SitDown(ANNA, seat = 2)))
    }

    @Test
    fun `a full table refuses one more player`() {
        val table = newTable(seatCount = 1)
        table.accept(JoinTable(ANNA, "Anna"))
        table.accept(JoinTable(BRU, "Bru"))
        table.accept(SitDown(ANNA))

        assertEquals(RuleError.TABLE_FULL, table.reject(SitDown(BRU)))
    }

    @Test
    fun `nobody bets more chips than they have`() {
        val table = seatedTable(defaultBuyIn = 50)

        assertEquals(RuleError.INSUFFICIENT_CHIPS, table.reject(PlaceBet(ANNA, 51)))
        assertEquals(RuleError.INVALID_AMOUNT, table.reject(PlaceBet(ANNA, 0)))
        assertEquals(RuleError.INVALID_AMOUNT, table.reject(PlaceBet(ANNA, -10)))
        assertEquals(50, table.snapshot().player(ANNA)!!.stack)
    }

    @Test
    fun `betting requires a seat`() {
        val table = newTable()
        table.accept(JoinTable(ANNA, "Anna"))

        assertEquals(RuleError.NOT_SEATED, table.reject(PlaceBet(ANNA, 10)))
        assertEquals(RuleError.NOT_SEATED, table.reject(Rebuy(ANNA, 10)))
        assertEquals(RuleError.NOT_SEATED, table.reject(StandUp(ANNA)))
    }

    @Test
    fun `a pot cannot pay more than it holds`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 10))

        assertEquals(RuleError.POT_TOO_SMALL, table.reject(AwardPot(actor = ANNA, to = BRU, amount = 11)))
        assertEquals(RuleError.UNKNOWN_POT, table.reject(PlaceBet(ANNA, 5, PotId("nope"))))
        assertEquals(RuleError.INVALID_TARGET, table.reject(AwardPot(actor = ANNA, to = PlayerId("ghost"))))
    }

    @Test
    fun `an empty pot is not awarded`() {
        val table = seatedTable()

        assertEquals(RuleError.INVALID_AMOUNT, table.reject(AwardPot(actor = ANNA, to = BRU)))
    }

    @Test
    fun `host-only commands are refused to everyone else`() {
        val table = seatedTable()

        assertEquals(RuleError.NOT_HOST, table.reject(AwardPot(actor = BRU, to = BRU)))
        assertEquals(RuleError.NOT_HOST, table.reject(AdjustStack(actor = BRU, player = BRU, delta = 100)))
        assertEquals(RuleError.NOT_HOST, table.reject(CreatePot(actor = BRU)))
        assertEquals(RuleError.NOT_HOST, table.reject(SetConfig(BRU, TableConfig())))
    }

    @Test
    fun `a host correction never leaves a negative stack`() {
        val table = seatedTable()

        assertEquals(RuleError.INSUFFICIENT_CHIPS, table.reject(AdjustStack(ANNA, BRU, -101)))
        assertEquals(RuleError.INVALID_AMOUNT, table.reject(AdjustStack(ANNA, BRU, 0)))
        assertEquals(100 - 100, table.accept(AdjustStack(ANNA, BRU, -100)).player(BRU)!!.stack)
    }

    @Test
    fun `chips only move between different players`() {
        val table = seatedTable()

        assertEquals(RuleError.INVALID_TARGET, table.reject(TransferChips(ANNA, ANNA, 10)))
        assertEquals(RuleError.INVALID_TARGET, table.reject(TransferChips(ANNA, PlayerId("ghost"), 10)))
        assertEquals(RuleError.INSUFFICIENT_CHIPS, table.reject(TransferChips(ANNA, BRU, 101)))
    }

    @Test
    fun `extra pots are created and used independently`() {
        val table = seatedTable()
        val created = table.accept(CreatePot(actor = ANNA, name = "poso"))
        val side = created.pots.last()

        assertEquals("poso", side.name)
        val state = table.accept(PlaceBet(BRU, 20, side.id))

        assertEquals(20, state.pot(side.id)!!.amount)
        assertEquals(0, state.pot(MAIN_POT)!!.amount)
        assertTrue(state.balanced)
    }

    @Test
    fun `shrinking the table stands up whoever loses their seat`() {
        val table = seatedTable()
        val state = table.accept(SetConfig(ANNA, TableConfig(seatCount = 2)))

        assertEquals(2, state.config.seatCount)
        assertEquals(null, state.player(CARME)!!.seat)
        assertEquals(100, state.player(CARME)!!.stack)
        assertEquals(RuleError.INVALID_CONFIG, table.reject(SetConfig(ANNA, TableConfig(seatCount = MAX_SEATS + 1))))
        assertEquals(RuleError.INVALID_CONFIG, table.reject(SetConfig(ANNA, TableConfig(defaultBuyIn = -1))))
    }

    @Test
    fun `the buy-in can be changed at any time`() {
        val table = seatedTable(defaultBuyIn = 100)
        table.accept(SetConfig(ANNA, TableConfig(defaultBuyIn = 500)))
        table.accept(JoinTable(PlayerId("dani"), "Dani"))
        val state = table.accept(SitDown(PlayerId("dani")))

        assertEquals(500, state.player(PlayerId("dani"))!!.stack)
        assertTrue(state.balanced)
    }

    @Test
    fun `a rejected command changes nothing at all`() {
        val table = seatedTable()
        val before = table.snapshot()
        table.reject(PlaceBet(ANNA, 1000))

        assertEquals(before, table.snapshot())
        assertEquals(before.rev, table.snapshot().rev)
    }
}
