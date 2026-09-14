package io.github.victormico.tablechips.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `the state survives a round trip`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 20))
        table.accept(AwardPot(actor = ANNA, to = BRU))
        val state = table.snapshot()

        val decoded = json.decodeFromString<TableState>(json.encodeToString(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `every ledger entry survives a round trip`() {
        val table = seatedTable()
        table.accept(PlaceBet(ANNA, 20))
        table.accept(AwardPot(actor = ANNA, to = BRU))
        table.accept(Rebuy(CARME, 30))
        table.accept(TransferChips(CARME, ANNA, 10))
        table.accept(AdjustStack(ANNA, ANNA, 5))
        table.accept(CreatePot(ANNA, "poso"))
        table.accept(StandUp(BRU))
        table.accept(LeaveTable(BRU))
        val ledger = table.ledger()

        val decoded = json.decodeFromString<List<TableEvent>>(json.encodeToString(ledger))

        assertEquals(ledger, decoded)
        assertEquals(table.snapshot(), Table.restore(decoded).snapshot())
    }

    @Test
    fun `events are tagged with stable names`() {
        val encoded = json.encodeToString<TableEvent>(BetPlaced(ANNA, 10, MAIN_POT, at = 1))

        assertTrue(encoded.contains("\"type\":\"bet\""), encoded)
        assertTrue(encoded.contains("\"player\":\"anna\""), encoded)
    }

    @Test
    fun `rule errors travel as codes, not as sentences`() {
        assertEquals("\"insufficient_chips\"", json.encodeToString(RuleError.INSUFFICIENT_CHIPS))
        assertEquals("\"not_host\"", json.encodeToString(RuleError.NOT_HOST))
    }
}
