package io.github.victormico.tablechips.server

import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.core.TableEvent
import io.github.victormico.tablechips.protocol.Action
import io.github.victormico.tablechips.protocol.BetAction
import io.github.victormico.tablechips.protocol.ClientMessage
import io.github.victormico.tablechips.protocol.HostCommandMessage
import io.github.victormico.tablechips.protocol.Join
import io.github.victormico.tablechips.protocol.KickCommand
import io.github.victormico.tablechips.protocol.Kicked
import io.github.victormico.tablechips.protocol.LedgerStore
import io.github.victormico.tablechips.protocol.ProtocolJson
import io.github.victormico.tablechips.protocol.Sit
import io.github.victormico.tablechips.protocol.TransferSeatCommand
import io.github.victormico.tablechips.protocol.UndoCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 6, as tests: a table survives the death of the process that was
 * hosting it, and the host has a way out of a phone that never comes back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryTest {

    /** A store with no disk under it, holding what the host wrote last. */
    private class MemoryStore : LedgerStore {
        var ledger: List<TableEvent>? = null
        var saves = 0
        var cleared = false

        override fun load(): List<TableEvent>? = ledger
        override fun save(events: List<TableEvent>) {
            // Serialized, like the real one: a store that keeps the live objects
            // would hide anything the wire format cannot carry.
            ledger = ProtocolJson.decodeFromString(ProtocolJson.encodeToString(events))
            saves++
        }

        override fun clear() {
            ledger = null
            cleared = true
        }
    }

    private fun host(store: LedgerStore, table: Table? = null): TableHost {
        var next = 0
        return TableHost(
            table = table ?: Table("TEST", TableConfig(defaultBuyIn = 100), clock = { 0 }),
            newId = { PlayerId("p" + (++next)) },
            store = store,
        )
    }

    @Test
    fun `every accepted move reaches the store`() = runTest {
        val store = MemoryStore()
        val host = host(store)
        val anna = TestTransport()
        connect(host, anna)

        play(anna, Join(name = "Anna"))
        play(anna, Sit(seat = 1, buyIn = 100))
        play(anna, Action(BetAction(amount = 30)))

        val saved = assertNotNull(store.ledger)
        // Opened, joined, sat, bet.
        assertEquals(4, saved.size)
        assertEquals(30, Table.restore(saved).snapshot().pots.first().amount)
        anna.close()
    }

    @Test
    fun `a table comes back from the store with its chips and its seats`() = runTest {
        val store = MemoryStore()
        val first = host(store)
        val anna = TestTransport()
        val bru = TestTransport()
        connect(first, anna)
        connect(first, bru)

        play(anna, Join(name = "Anna"))
        play(anna, Sit(seat = 1, buyIn = 100))
        play(bru, Join(name = "Bru"))
        play(bru, Sit(seat = 2, buyIn = 100))
        play(anna, Action(BetAction(amount = 40)))
        anna.close()
        bru.close()

        // The process dies here. Nothing is carried over but the store.
        val restored = Table.restore(assertNotNull(store.load()))
        val second = host(store, restored)
        val annaAgain = TestTransport()
        connect(second, annaAgain)

        // A returning phone brings the id it was given, which is its seat.
        play(annaAgain, Join(name = "Anna", playerId = PlayerId("p1")))

        val state = annaAgain.lastState().state
        assertEquals("TEST", state.roomCode)
        assertEquals(1, state.player(PlayerId("p1"))!!.seat)
        assertEquals(60, state.player(PlayerId("p1"))!!.stack)
        assertEquals(100, state.player(PlayerId("p2"))!!.stack)
        assertEquals(40, state.pots.first().amount)
        assertTrue(state.balanced)
        // Bru's phone is not here yet, and the table says so.
        assertEquals(false, state.player(PlayerId("p2"))!!.connected)
        annaAgain.close()
    }

    @Test
    fun `the log survives the restart, and so does undo`() = runTest {
        val store = MemoryStore()
        val first = host(store)
        val anna = TestTransport()
        connect(first, anna)
        play(anna, Join(name = "Anna"))
        play(anna, Sit(seat = 1, buyIn = 100))
        play(anna, Action(BetAction(amount = 40)))
        val before = anna.lastState()
        anna.close()

        val second = host(store, Table.restore(assertNotNull(store.load())))
        val again = TestTransport()
        connect(second, again)
        play(again, Join(name = "Anna", playerId = PlayerId("p1")))
        assertEquals(before.state.log.size, again.lastState().state.log.size)

        // The host of the restored table is still the host, so the bet goes back.
        play(again, HostCommandMessage(UndoCommand))
        assertEquals(100, again.lastState().state.player(PlayerId("p1"))!!.stack)
        again.close()
    }

    @Test
    fun `a kicked player is told, loses the seat, and can be let back in`() = runTest {
        val store = MemoryStore()
        val host = host(store)
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)

        play(anna, Join(name = "Anna"))
        play(anna, Sit(seat = 1, buyIn = 100))
        play(bru, Join(name = "Bru"))
        play(bru, Sit(seat = 2, buyIn = 100))

        play(anna, HostCommandMessage(KickCommand(PlayerId("p2"))))

        val goodbye = bru.lastMessage()
        assertIs<Kicked>(goodbye)
        assertEquals("kicked", goodbye.reason)
        val state = anna.lastState().state
        assertNull(state.player(PlayerId("p2")))
        assertTrue(state.balanced)
        assertEquals(100, state.bank.cashedOut)

        // And it was a ledger entry like any other, so it goes back.
        play(anna, HostCommandMessage(UndoCommand))
        assertEquals(100, anna.lastState().state.player(PlayerId("p2"))!!.stack)
        assertEquals(0, anna.lastState().state.bank.cashedOut)
        anna.close()
        bru.close()
    }

    @Test
    fun `a guest cannot throw anybody out`() = runTest {
        val store = MemoryStore()
        val host = host(store)
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)
        play(anna, Join(name = "Anna"))
        play(bru, Join(name = "Bru"))

        play(bru, HostCommandMessage(KickCommand(PlayerId("p1"))))

        assertEquals("not_host", (bru.lastMessage() as? io.github.victormico.tablechips.protocol.ErrorMessage)?.code)
        assertNotNull(host.table.snapshot().player(PlayerId("p1")))
        anna.close()
        bru.close()
    }

    @Test
    fun `a seat handed over takes its chips to the new phone`() = runTest {
        val store = MemoryStore()
        val host = host(store)
        val anna = TestTransport()
        val bru = TestTransport()
        val carla = TestTransport()
        connect(host, anna)
        connect(host, bru)
        connect(host, carla)

        play(anna, Join(name = "Anna"))
        play(anna, Sit(seat = 1, buyIn = 100))
        play(bru, Join(name = "Bru"))
        play(bru, Sit(seat = 2, buyIn = 100))
        // Bru's phone is dead; the same person joins again from another one.
        play(carla, Join(name = "Bru's other phone"))

        play(anna, HostCommandMessage(TransferSeatCommand(PlayerId("p2"), PlayerId("p3"))))

        val state = anna.lastState().state
        assertNull(state.player(PlayerId("p2")))
        assertEquals(2, state.player(PlayerId("p3"))!!.seat)
        assertEquals(100, state.player(PlayerId("p3"))!!.stack)
        // Nothing left the table: the chips only changed hands.
        assertEquals(0, state.bank.cashedOut)
        assertTrue(state.balanced)
        assertIs<Kicked>(bru.lastMessage())
        anna.close()
        bru.close()
        carla.close()
    }

    @Test
    fun `closing the table says goodbye to everybody`() = runTest {
        val store = MemoryStore()
        val host = host(store)
        val anna = TestTransport()
        val bru = TestTransport()
        connect(host, anna)
        connect(host, bru)
        play(anna, Join(name = "Anna"))
        play(bru, Join(name = "Bru"))

        host.closeAll()
        runCurrent()

        listOf(anna, bru).forEach { transport ->
            val last = transport.lastMessage()
            assertIs<Kicked>(last)
            assertEquals("table_closed", last.reason)
        }
        anna.close()
        bru.close()
    }

    private suspend fun TestScope.play(transport: TestTransport, message: ClientMessage) {
        transport.sendToServer(message)
        runCurrent()
    }
}
