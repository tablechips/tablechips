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
    fun `a hand of every mode survives a round trip`() {
        val bank = bankTable(mode = GameMode.SEVEN_HALF, naturalPays = Payout(2, 1))
        bank.accept(PlaceStake(BRU, 30))
        bank.accept(PlaceStake(CARME, 10))
        bank.accept(CancelStake(CARME))
        bank.accept(SettleHand(ANNA, BRU, HandOutcome.NATURAL))
        bank.accept(SetBanker(ANNA, BRU))

        val bankLedger = json.decodeFromString<List<TableEvent>>(json.encodeToString(bank.ledger()))
        assertEquals(bank.ledger(), bankLedger)
        assertEquals(bank.snapshot(), Table.restore(bankLedger).snapshot())

        val poker = pokerTable(defaultBuyIn = 0, smallBlind = 0, bigBlind = 0)
        poker.accept(Rebuy(ANNA, 100))
        poker.accept(Rebuy(BRU, 100))
        poker.accept(Rebuy(CARME, 20))
        poker.accept(StartHand(ANNA))
        poker.accept(PlaceBet(CARME, 20))
        poker.accept(PlaceBet(ANNA, 60))
        poker.accept(PlaceBet(BRU, 60))
        poker.accept(CloseRound(ANNA))
        poker.accept(Fold(BRU))
        poker.accept(SplitPots(ANNA))

        val pokerLedger = json.decodeFromString<List<TableEvent>>(json.encodeToString(poker.ledger()))
        assertEquals(poker.ledger(), pokerLedger)
        assertEquals(poker.snapshot(), Table.restore(pokerLedger).snapshot())
    }

    @Test
    fun `events are tagged with stable names`() {
        val encoded = json.encodeToString<TableEvent>(BetPlaced(ANNA, 10, MAIN_POT, at = 1))

        assertTrue(encoded.contains("\"type\":\"bet\""), encoded)
        assertTrue(encoded.contains("\"player\":\"anna\""), encoded)

        val settled = json.encodeToString<TableEvent>(
            HandSettled(ANNA, BRU, HandOutcome.NATURAL, stake = 10, delta = 15, at = 1),
        )
        assertTrue(settled.contains("\"type\":\"hand_settled\""), settled)
        assertTrue(settled.contains("\"outcome\":\"natural\""), settled)
    }

    @Test
    fun `rule errors travel as codes, not as sentences`() {
        assertEquals("\"insufficient_chips\"", json.encodeToString(RuleError.INSUFFICIENT_CHIPS))
        assertEquals("\"not_host\"", json.encodeToString(RuleError.NOT_HOST))
    }
}
