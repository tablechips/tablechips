package io.github.tablechips.server

import io.github.tablechips.core.PlayerId
import io.github.tablechips.core.Table
import io.github.tablechips.core.TableConfig
import io.github.tablechips.protocol.Action
import io.github.tablechips.protocol.BetAction
import io.github.tablechips.protocol.Join
import io.github.tablechips.protocol.ProtocolJson
import io.github.tablechips.protocol.ServerMessage
import io.github.tablechips.protocol.Sit
import io.github.tablechips.protocol.StateMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a client is handed has to be at least as recent as what it was handed
 * before. Two commands landing at once used to each send their own frames
 * outside the lock, so a phone could be told the pot was 40 after being told
 * it was 60 — and then sit there showing the wrong number until somebody moved
 * again.
 */
class OrderingTest {

    @Test
    fun `a client is never handed a revision older than one it already has`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = TableHost(
            table = Table("TEST", TableConfig(defaultBuyIn = 10_000), clock = { 0 }),
            newId = { PlayerId("p" + counter++) },
        )
        val clients = List(4) { TestTransport() }
        clients.forEach { transport -> scope.launch { host.serve(transport) } }

        clients.forEachIndexed { index, client ->
            client.sendToServer(Join(name = "P$index"))
            client.sendToServer(Sit())
        }
        withTimeout(10_000) {
            while (host.table.snapshot().players.count { it.seat != null } < clients.size) delay(10)
        }

        // Everybody moves at once, which is what a table actually does.
        val storm = clients.map { client ->
            scope.launch {
                repeat(40) { client.sendToServer(Action(BetAction(amount = 1))) }
            }
        }
        storm.forEach { it.join() }
        withTimeout(10_000) {
            while (host.table.snapshot().pots.first().amount < 160L) delay(10)
        }
        delay(200)

        clients.forEach { client ->
            val revisions = client.received
                .map { ProtocolJson.decodeFromString<ServerMessage>(it) }
                .filterIsInstance<StateMessage>()
                .map { it.state.rev }
            assertTrue(revisions.isNotEmpty(), "a seated client is told about the table")
            revisions.zipWithNext().forEach { (earlier, later) ->
                assertTrue(later >= earlier, "went back from $earlier to $later")
            }
            assertEquals(host.table.snapshot().rev, revisions.last())
        }
        scope.cancel()
    }

    private var counter = 0
}
