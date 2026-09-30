package io.github.victormico.tablechips.server

import io.github.victormico.tablechips.core.GameMode
import io.github.victormico.tablechips.core.HandOutcome
import io.github.victormico.tablechips.core.MAIN_POT
import io.github.victormico.tablechips.core.Payout
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.protocol.Action
import io.github.victormico.tablechips.protocol.BetAction
import io.github.victormico.tablechips.protocol.CancelStakeAction
import io.github.victormico.tablechips.protocol.ClientMessage
import io.github.victormico.tablechips.protocol.CloseRoundCommand
import io.github.victormico.tablechips.protocol.DoubleAction
import io.github.victormico.tablechips.protocol.ErrorMessage
import io.github.victormico.tablechips.protocol.FoldAction
import io.github.victormico.tablechips.protocol.FundHouseCommand
import io.github.victormico.tablechips.protocol.HostCommandMessage
import io.github.victormico.tablechips.protocol.Join
import io.github.victormico.tablechips.protocol.RebuyAction
import io.github.victormico.tablechips.protocol.SetBankerCommand
import io.github.victormico.tablechips.protocol.SetConfigCommand
import io.github.victormico.tablechips.protocol.SettleAllCommand
import io.github.victormico.tablechips.protocol.SettleCommand
import io.github.victormico.tablechips.protocol.Sit
import io.github.victormico.tablechips.protocol.SplitAction
import io.github.victormico.tablechips.protocol.SplitPotsCommand
import io.github.victormico.tablechips.protocol.StakeAction
import io.github.victormico.tablechips.protocol.StartHandCommand
import io.github.victormico.tablechips.protocol.TakeBankAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The games, over the wire. The rules themselves are proved in `:core`; what
 * this checks is that a phone can actually reach them — every new move has a
 * frame, and the server refuses the ones it should.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModesTest {

    private fun host(mode: GameMode): TableHost {
        var next = 0
        return TableHost(
            table = Table(
                "TEST",
                TableConfig(mode = mode, defaultBuyIn = 100, naturalPays = Payout(3, 2),
                    smallBlind = 5, bigBlind = 10),
                clock = { 0 },
            ),
            newId = { PlayerId("p" + (++next)) },
        )
    }

    private val anna = PlayerId("p1")
    private val bru = PlayerId("p2")
    private val carme = PlayerId("p3")

    @Test
    fun `a whole hand of blackjack goes over the wire`() = runTest {
        val host = host(GameMode.BLACKJACK)
        val a = TestTransport()
        val b = TestTransport()
        val c = TestTransport()
        listOf(a, b, c).forEach { connect(host, it) }
        play(a, Join(name = "Anna"))
        play(a, Sit())
        play(b, Join(name = "Bru"))
        play(b, Sit())
        play(c, Join(name = "Carme"))
        play(c, Sit())

        // Anna deals; the house has chips of its own, and pays from them.
        play(a, HostCommandMessage(SetBankerCommand(anna)))
        play(a, HostCommandMessage(FundHouseCommand(200)))
        play(b, Action(StakeAction(30)))
        play(c, Action(StakeAction(20)))
        play(c, Action(CancelStakeAction))
        play(c, Action(StakeAction(20)))
        play(c, Action(SplitAction()))
        play(c, Action(DoubleAction(hand = 1)))
        assertEquals(listOf(20L, 40L), a.lastState().state.player(carme)!!.hands)
        play(a, HostCommandMessage(SettleCommand(bru, HandOutcome.NATURAL)))
        play(a, HostCommandMessage(SettleCommand(carme, HandOutcome.LOSE, hand = 0)))
        play(a, HostCommandMessage(SettleCommand(carme, HandOutcome.WIN, hand = 0)))

        val state = a.lastState().state
        assertEquals(anna, state.banker)
        assertEquals(70 + 30 + 45, state.player(bru)!!.stack)
        assertEquals(40 + 40 + 40, state.player(carme)!!.stack)
        assertEquals(100, state.player(anna)!!.stack)
        assertEquals(200 - 45 + 20 - 40, state.house)
        assertTrue(state.balanced)
        listOf(a, b, c).forEach { it.close() }
    }

    @Test
    fun `whoever holds the bank settles against it, without being the host`() = runTest {
        val host = host(GameMode.SEVEN_HALF)
        val a = TestTransport()
        val b = TestTransport()
        val c = TestTransport()
        listOf(a, b, c).forEach { connect(host, it) }
        play(a, Join(name = "Anna"))
        play(a, Sit())
        play(b, Join(name = "Bru"))
        play(b, Sit())
        play(c, Join(name = "Carme"))
        play(c, Sit())

        play(b, Action(TakeBankAction))
        play(a, Action(StakeAction(10)))
        play(c, Action(StakeAction(20)))
        play(b, HostCommandMessage(SettleCommand(anna, HandOutcome.LOSE)))
        play(b, HostCommandMessage(SettleAllCommand(HandOutcome.WIN)))

        val state = b.lastState().state
        assertEquals(90, state.player(anna)!!.stack)
        assertEquals(120, state.player(carme)!!.stack)
        assertEquals(90, state.player(bru)!!.stack)
        // Somebody playing against the bank does not settle their own hand.
        play(c, Action(StakeAction(20)))
        play(c, HostCommandMessage(SettleCommand(carme, HandOutcome.WIN)))
        assertEquals("not_host", (c.lastMessage() as ErrorMessage).code)
        listOf(a, b, c).forEach { it.close() }
    }

    @Test
    fun `a player takes a free bank, and only the host takes it away`() = runTest {
        val host = host(GameMode.SEVEN_HALF)
        val a = TestTransport()
        val b = TestTransport()
        listOf(a, b).forEach { connect(host, it) }
        play(a, Join(name = "Anna"))
        play(a, Sit())
        play(b, Join(name = "Bru"))
        play(b, Sit())

        play(b, Action(TakeBankAction))
        assertEquals(bru, a.lastState().state.banker)
        play(b, Action(StakeAction(10)))
        assertEquals("banker_cannot_bet", (b.lastMessage() as ErrorMessage).code)

        // The host hands it over; Bru cannot simply take it back.
        play(a, HostCommandMessage(SetBankerCommand(anna)))
        play(b, Action(TakeBankAction))
        assertEquals("not_host", (b.lastMessage() as ErrorMessage).code)
        assertEquals(anna, a.lastState().state.banker)
        listOf(a, b).forEach { it.close() }
    }

    @Test
    fun `a hand of poker with an all-in goes over the wire`() = runTest {
        val host = host(GameMode.POKER)
        val a = TestTransport()
        val b = TestTransport()
        val c = TestTransport()
        listOf(a, b, c).forEach { connect(host, it) }
        play(a, Join(name = "Anna"))
        play(a, Sit(buyIn = 200))
        play(b, Join(name = "Bru"))
        play(b, Sit(buyIn = 200))
        play(c, Join(name = "Carme"))
        play(c, Sit(buyIn = 30))

        play(a, HostCommandMessage(StartHandCommand))
        // Blinds: Bru 5, Carme 10. Carme is all in for the rest.
        play(c, Action(BetAction(20)))
        play(a, Action(BetAction(100)))
        play(b, Action(BetAction(95)))
        play(a, HostCommandMessage(CloseRoundCommand))
        play(a, HostCommandMessage(SplitPotsCommand))

        val state = a.lastState().state
        assertEquals(0, state.button)
        assertEquals(2, state.pots.size)
        assertEquals(90, state.pots[0].amount)
        assertEquals(140, state.pots[1].amount)
        assertEquals(listOf(anna, bru), state.pots[1].eligible)
        assertEquals(0, state.currentBet)
        assertTrue(state.balanced)
        listOf(a, b, c).forEach { it.close() }
    }

    @Test
    fun `folding travels, and a fold in a game without folding does not`() = runTest {
        val host = host(GameMode.POKER)
        val a = TestTransport()
        val b = TestTransport()
        listOf(a, b).forEach { connect(host, it) }
        play(a, Join(name = "Anna"))
        play(a, Sit())
        play(b, Join(name = "Bru"))
        play(b, Sit())
        play(a, HostCommandMessage(StartHandCommand))

        // Heads-up, Bru folding hands Anna the pot and deals the next hand at once.
        play(b, Action(FoldAction))
        assertTrue(a.lastState().state.log.any { it.key == "log.player_folded" && it.actor == bru })

        play(a, HostCommandMessage(SetConfigCommand(
            a.lastState().state.config.copy(mode = GameMode.MANUAL),
        )))
        play(b, Action(FoldAction))
        assertEquals("wrong_mode", (b.lastMessage() as ErrorMessage).code)
        listOf(a, b).forEach { it.close() }
    }

    @Test
    fun `the pot of a bank game is never used, and the stakes are on the table`() = runTest {
        val host = host(GameMode.BLACKJACK)
        val a = TestTransport()
        val b = TestTransport()
        listOf(a, b).forEach { connect(host, it) }
        play(a, Join(name = "Anna"))
        play(a, Sit())
        play(b, Join(name = "Bru"))
        play(b, Sit())
        play(a, HostCommandMessage(SetBankerCommand(anna)))
        play(b, Action(RebuyAction(50)))
        play(b, Action(StakeAction(60)))

        val state = b.lastState().state
        assertEquals(0, state.pot(MAIN_POT)!!.amount)
        assertEquals(60, state.player(bru)!!.stake)
        assertEquals(90, state.player(bru)!!.stack)
        assertEquals(250, state.chipsOnTable)
        assertTrue(state.balanced)
        listOf(a, b).forEach { it.close() }
    }

    private suspend fun TestScope.play(transport: TestTransport, message: ClientMessage) {
        transport.sendToServer(message)
        runCurrent()
    }
}
