package io.github.victormico.tablechips.server

import io.github.victormico.tablechips.core.MAIN_POT
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.protocol.Action
import io.github.victormico.tablechips.protocol.AwardPotCommand
import io.github.victormico.tablechips.protocol.BetAction
import io.github.victormico.tablechips.protocol.ClientState
import io.github.victormico.tablechips.protocol.Connection
import io.github.victormico.tablechips.protocol.HostCommandMessage
import io.github.victormico.tablechips.protocol.Sit
import io.github.victormico.tablechips.protocol.TableClient
import io.github.victormico.tablechips.protocol.TableConnection
import io.github.victormico.tablechips.protocol.UndoCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app plays through this client, so it is worth proving against the real
 * server over a real socket rather than against a stub. All of it runs on the
 * JVM: no emulator is involved in checking that the app can play a hand.
 */
class TableClientTest {

    private lateinit var tableHost: TableHost
    private lateinit var server: TableServer
    private lateinit var scope: CoroutineScope
    private val connections = mutableListOf<TableConnection>()
    private var port = 0

    @BeforeTest
    fun start() = runBlocking {
        tableHost = TableHost(Table("ABCD", TableConfig(defaultBuyIn = 100), clock = { 0 }))
        server = TableServer(tableHost, preferredPort = 0)
        port = server.start()
        scope = CoroutineScope(SupervisorJob())
        Unit
    }

    @AfterTest
    fun stop() {
        scope.cancel()
        connections.forEach { it.close() }
        server.stop()
    }

    private fun client(): TableClient {
        val connection = TableConnection(TableConnection.webSocketUrl("127.0.0.1", port))
        connections += connection
        return TableClient(scope, { connection.open() }, retryDelays = listOf(50))
    }

    /** Waits for the client's state to satisfy [predicate], or fails the test. */
    private suspend fun TableClient.await(predicate: (ClientState) -> Boolean): ClientState =
        withTimeout(10_000) { state.first(predicate) }

    @Test
    fun `a client joins and is told who it is`() = runBlocking {
        val anna = client()
        anna.join("Anna")

        val state = anna.await { it.table != null }

        assertEquals(Connection.ONLINE, state.connection)
        assertNotNull(state.you)
        assertEquals("Anna", state.me?.name)
        assertTrue(state.isHost, "the first player through the door runs the table")
    }

    @Test
    fun `two clients play a hand and see the same table`() = runBlocking {
        val anna = client().also { it.join("Anna") }
        val idA = anna.await { it.you != null }.you!!
        val bru = client().also { it.join("Bru") }
        val idB = bru.await { it.you != null }.you!!

        anna.send(Sit())
        bru.send(Sit())
        anna.await { it.seated }
        bru.await { it.seated }

        anna.send(Action(BetAction(amount = 40)))
        bru.send(Action(BetAction(amount = 40)))
        bru.await { it.table!!.pot(MAIN_POT)!!.amount == 80L }

        anna.send(HostCommandMessage(AwardPotCommand(to = idB)))
        val afterAward = bru.await { it.me!!.stack == 140L }

        assertEquals(0, afterAward.table!!.pot(MAIN_POT)!!.amount)
        assertEquals(60, afterAward.table!!.player(idA)!!.stack)
        assertTrue(afterAward.table!!.balanced)
        // Both ends of the socket agree, which is the whole point of full state.
        assertEquals(anna.await { it.table!!.rev == afterAward.table!!.rev }.table, afterAward.table)
    }

    @Test
    fun `the host undoes from the app and every client sees it`() = runBlocking {
        val anna = client().also { it.join("Anna") }
        anna.await { it.you != null }
        val bru = client().also { it.join("Bru") }
        bru.await { it.you != null }
        anna.send(Sit())
        anna.await { it.seated }
        anna.send(Action(BetAction(amount = 25)))
        bru.await { it.table!!.pot(MAIN_POT)!!.amount == 25L }

        anna.send(HostCommandMessage(UndoCommand))

        val state = bru.await { it.table!!.pot(MAIN_POT)!!.amount == 0L }
        assertEquals(100, state.table!!.players.first { it.name == "Anna" }.stack)
    }

    @Test
    fun `a refusal arrives as a code and does not touch the table`() = runBlocking {
        val anna = client().also { it.join("Anna") }
        anna.await { it.you != null }
        anna.send(Sit())
        val before = anna.await { it.seated }.table

        anna.send(Action(BetAction(amount = 10_000)))

        val refused = anna.await { it.error != null }
        assertEquals("insufficient_chips", refused.error)
        assertEquals(before, refused.table)
    }

    @Test
    fun `a guest is refused the host's commands`() = runBlocking {
        client().also { it.join("Anna") }.await { it.you != null }
        val bru = client().also { it.join("Bru") }
        val idB = bru.await { it.you != null }.you!!
        bru.send(Sit())
        bru.await { it.seated }

        bru.send(HostCommandMessage(AwardPotCommand(to = idB)))

        assertEquals("not_host", bru.await { it.error != null }.error)
    }

    /**
     * The point of keeping the id: a phone that lost the network gets its seat
     * and its chips back, not a new empty one.
     */
    @Test
    fun `a client that comes back with its id gets its seat back`() = runBlocking {
        val first = client().also { it.join("Anna") }
        val id = first.await { it.you != null }.you!!
        first.send(Sit(seat = 4, buyIn = 300))
        first.await { it.seated }
        first.close()

        val again = client().also { it.join("Anna", playerId = id) }
        val state = again.await { it.table != null && it.seated }

        assertEquals(id, state.you)
        assertEquals(4, state.me!!.seat)
        assertEquals(300, state.me!!.stack)
    }

    @Test
    fun `the client waits for a table that is not there yet`() = runBlocking {
        val connection = TableConnection(TableConnection.webSocketUrl("127.0.0.1", 1))
        connections += connection
        val lonely = TableClient(scope, { connection.open() }, retryDelays = listOf(50))
        lonely.join("Anna")

        val state = lonely.await { it.connection == Connection.OFFLINE }

        assertNull(state.table)
        // Still trying: a host who has not opened the table yet is the normal case.
        lonely.await { it.connection == Connection.CONNECTING }
        Unit
    }

    @Test
    fun `an address typed by a person becomes a socket url`() {
        val port = 8080
        assertEquals("ws://192.168.0.17:8080/ws", TableConnection.parse("192.168.0.17", port))
        assertEquals("ws://192.168.0.17:8080/ws", TableConnection.parse("http://192.168.0.17:8080/", port))
        assertEquals("ws://192.168.0.17:9000/ws", TableConnection.parse("192.168.0.17:9000", port))
        assertEquals("ws://192.168.0.17:8080/ws", TableConnection.parse("  192.168.0.17/join?room=AB ", port))
        assertNull(TableConnection.parse("", port))
        assertNull(TableConnection.parse("192.168.0.17:0", port))
        assertNull(TableConnection.parse(":8080", port))
    }

    /**
     * The phase's own bar: eight players at once, which is more than any of the
     * products that synchronise between devices manage.
     */
    @Test
    fun `eight app clients sit at the same table and see the same thing`() = runBlocking {
        val players = (1..8).map { index ->
            client().also { it.join("P$index") }
        }
        players.forEach { it.await { state -> state.you != null } }
        players.forEach { it.send(Sit()) }
        players.forEach { it.await { state -> state.seated } }

        players.forEach { it.send(Action(BetAction(amount = 10))) }
        val last = players.last().await { it.table!!.pot(MAIN_POT)!!.amount == 80L }

        assertEquals(8, last.table!!.players.count { it.seat != null })
        assertEquals(8, last.table!!.players.count { it.connected })
        assertTrue(last.table!!.balanced)
        // Every client is looking at the same revision of the same table.
        players.forEach { client ->
            val seen = client.await { it.table!!.rev == last.table!!.rev }
            assertEquals(last.table, seen.table)
        }
    }
}
