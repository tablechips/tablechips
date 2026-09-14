package io.github.victormico.tablechips.server

import io.github.victormico.tablechips.core.MAX_SEATS
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.protocol.Action
import io.github.victormico.tablechips.protocol.ClientMessage
import io.github.victormico.tablechips.protocol.AwardPotCommand
import io.github.victormico.tablechips.protocol.BetAction
import io.github.victormico.tablechips.protocol.ErrorMessage
import io.github.victormico.tablechips.protocol.HostCommandMessage
import io.github.victormico.tablechips.protocol.Join
import io.github.victormico.tablechips.protocol.Leave
import io.github.victormico.tablechips.protocol.Sit
import io.github.victormico.tablechips.protocol.UndoCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TableHostTest {

    private fun host(): TableHost {
        var next = 0
        return TableHost(
            table = Table("TEST", TableConfig(defaultBuyIn = 100), clock = { 0 }),
            newId = { PlayerId("p" + (++next)) },
        )
    }

    @Test
    fun `joining answers with the whole state and the id of the sender`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)

        play(anna, Join(name = "Anna"))
        runCurrent()

        val state = anna.lastState()
        assertEquals(PlayerId("p1"), state.you)
        assertEquals("Anna", state.state.player(PlayerId("p1"))!!.name)
        assertTrue(state.state.player(PlayerId("p1"))!!.connected)
        anna.close()
    }

    @Test
    fun `two clients see the same table`() = runTest {
        val host = host()
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)

        play(anna, Join(name = "Anna"))
        play(bru, Join(name = "Bru"))
        play(anna, Sit())
        play(bru, Sit())
        runCurrent()

        val fromAnna = anna.lastState()
        val fromBru = bru.lastState()
        assertEquals(fromAnna.state, fromBru.state)
        assertEquals(2, fromAnna.state.players.size)
        assertEquals(PlayerId("p1"), fromAnna.you)
        assertEquals(PlayerId("p2"), fromBru.you)
        anna.close()
        bru.close()
    }

    @Test
    fun `every change reaches everyone, not just whoever caused it`() = runTest {
        val host = host()
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)
        play(anna, Join(name = "Anna"))
        play(bru, Join(name = "Bru"))
        play(anna, Sit())
        play(bru, Sit())
        runCurrent()

        val before = bru.received.size
        play(anna, Action(BetAction(amount = 25)))
        runCurrent()

        assertTrue(bru.received.size > before)
        assertEquals(25, bru.lastState().state.pots.first().amount)
        anna.close()
        bru.close()
    }

    @Test
    fun `a returning client gets its seat back with the id it kept`() = runTest {
        val host = host()
        val first = TestTransport()
        connect(host, first)
        play(first, Join(name = "Anna"))
        play(first, Sit(seat = 4, buyIn = 300))
        runCurrent()
        val id = first.lastState().you
        first.close()
        runCurrent()

        val second = TestTransport()
        connect(host, second)
        play(second, Join(name = "Anna", playerId = id))
        runCurrent()

        val player = second.lastState().state.player(id)
        assertNotNull(player)
        assertEquals(4, player.seat)
        assertEquals(300, player.stack)
        assertTrue(player.connected)
        second.close()
    }

    @Test
    fun `a disconnection shows up as absence, not as a change to the ledger`() = runTest {
        val host = host()
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)
        play(anna, Join(name = "Anna"))
        play(bru, Join(name = "Bru"))
        play(bru, Sit())
        runCurrent()
        val depth = host.table.undoDepth

        bru.close()
        runCurrent()

        val state = anna.lastState().state
        assertFalse(state.player(PlayerId("p2"))!!.connected)
        assertEquals(0, state.player(PlayerId("p2"))!!.seat)
        assertEquals(depth, host.table.undoDepth)
        anna.close()
    }

    @Test
    fun `nobody can act before joining`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)

        play(anna, Sit())
        runCurrent()

        assertEquals("not_joined", (anna.lastMessage() as ErrorMessage).code)
        anna.close()
    }

    @Test
    fun `a broken frame is answered, not fatal`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)

        anna.sendRaw("{not json at all")
        runCurrent()
        assertEquals("bad_message", (anna.lastMessage() as ErrorMessage).code)

        play(anna, Join(name = "Anna"))
        runCurrent()
        assertIs<io.github.victormico.tablechips.protocol.StateMessage>(anna.lastMessage())
        anna.close()
    }

    @Test
    fun `a frame from another protocol version is refused`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)

        play(anna, Join(name = "Anna", v = 99))
        runCurrent()

        assertEquals("unsupported_version", (anna.lastMessage() as ErrorMessage).code)
        anna.close()
    }

    @Test
    fun `a code from another table is refused`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)

        play(anna, Join(name = "Anna", room = "ZZZZ"))
        runCurrent()

        assertEquals("wrong_room", (anna.lastMessage() as ErrorMessage).code)
        anna.close()
    }

    @Test
    fun `the room code is accepted in any case`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)

        play(anna, Join(name = "Anna", room = "test"))
        runCurrent()

        assertIs<io.github.victormico.tablechips.protocol.StateMessage>(anna.lastMessage())
        anna.close()
    }

    @Test
    fun `a rule refusal comes back as a code and changes nothing`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)
        play(anna, Join(name = "Anna"))
        play(anna, Sit())
        runCurrent()
        val before = anna.lastState().state

        play(anna, Action(BetAction(amount = 10_000)))
        runCurrent()

        assertEquals("insufficient_chips", (anna.lastMessage() as ErrorMessage).code)
        assertEquals(before, host.table.snapshot())
        anna.close()
    }

    @Test
    fun `host commands are refused to the other players`() = runTest {
        val host = host()
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)
        play(anna, Join(name = "Anna"))
        play(bru, Join(name = "Bru"))
        play(anna, Sit())
        play(bru, Sit())
        play(anna, Action(BetAction(amount = 40)))
        runCurrent()

        play(bru, HostCommandMessage(AwardPotCommand(to = PlayerId("p2"))))
        runCurrent()
        assertEquals("not_host", (bru.lastMessage() as ErrorMessage).code)

        play(anna, HostCommandMessage(AwardPotCommand(to = PlayerId("p2"))))
        runCurrent()
        assertEquals(140, bru.lastState().state.player(PlayerId("p2"))!!.stack)
        anna.close()
        bru.close()
    }

    @Test
    fun `undo travels to every client`() = runTest {
        val host = host()
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)
        play(anna, Join(name = "Anna"))
        play(bru, Join(name = "Bru"))
        play(anna, Sit())
        play(bru, Sit())
        play(anna, Action(BetAction(amount = 40)))
        runCurrent()
        assertEquals(60, bru.lastState().state.player(PlayerId("p1"))!!.stack)

        play(anna, HostCommandMessage(UndoCommand))
        runCurrent()

        assertEquals(100, bru.lastState().state.player(PlayerId("p1"))!!.stack)
        assertEquals(0, bru.lastState().state.pots.first().amount)
        anna.close()
        bru.close()
    }

    @Test
    fun `leaving frees the id so the same device joins as somebody new`() = runTest {
        val host = host()
        val anna = TestTransport()
        connect(host, anna)
        play(anna, Join(name = "Anna"))
        play(anna, Sit())
        play(anna, Leave())
        runCurrent()

        play(anna, Sit())
        runCurrent()

        assertEquals("not_joined", (anna.lastMessage() as ErrorMessage).code)
        assertTrue(host.table.snapshot().players.isEmpty())
        anna.close()
    }

    @Test
    fun `ten players fit at the table`() = runTest {
        val host = host()
        val clients = List(MAX_SEATS) { TestTransport() }
        clients.forEach { connect(host, it) }
        clients.forEachIndexed { index, client ->
            play(client, Join(name = "P$index"))
            play(client, Sit())
        }
        runCurrent()

        val state = clients.last().lastState().state
        assertEquals(MAX_SEATS, state.players.count { it.seat != null })
        assertTrue(state.freeSeats.isEmpty())
        assertTrue(state.balanced)
        clients.forEach { it.close() }
    }

    /**
     * Sends one frame and lets the server finish with it. Clients at a table act
     * one after another; a test that queues frames from two connections at once
     * is testing the dispatcher, not the protocol.
     */
    private suspend fun TestScope.play(transport: TestTransport, message: ClientMessage) {
        transport.sendToServer(message)
        runCurrent()
    }
}
