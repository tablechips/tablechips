package io.github.victormico.tablechips.app

import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.core.TableEvent
import io.github.victormico.tablechips.core.TableState
import io.github.victormico.tablechips.core.newRoomCode
import io.github.victormico.tablechips.protocol.LedgerStore
import io.github.victormico.tablechips.protocol.NoLedgerStore
import io.github.victormico.tablechips.server.DEFAULT_PORT
import io.github.victormico.tablechips.server.HostAddress
import io.github.victormico.tablechips.server.TableHost
import io.github.victormico.tablechips.server.TableServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay

/** What the host screen and the notification need to know. */
data class HostStatus(
    val running: Boolean = false,
    val starting: Boolean = false,
    val port: Int = DEFAULT_PORT,
    val roomCode: String? = null,
    val addresses: List<HostAddress> = emptyList(),
    val failure: HostFailure? = null,
)

/** Failures the screen has to explain, as codes: the text lives in strings.xml. */
enum class HostFailure {
    COULD_NOT_START,
}

/**
 * The single table this process hosts.
 *
 * It is a singleton because there is exactly one of it: the foreground service
 * keeps the process alive, the activity comes and goes with the screen, and
 * both talk to the same table. The ledger must not die with the activity.
 */
object HostController {

    private val _status = MutableStateFlow(HostStatus())
    val status: StateFlow<HostStatus> = _status.asStateFlow()

    private val _table = MutableStateFlow<TableState?>(null)

    /** The live table, or null while no table is open. */
    val table: StateFlow<TableState?> = _table.asStateFlow()

    private var tableHost: TableHost? = null
    private var server: TableServer? = null
    private var store: LedgerStore = NoLedgerStore

    private val _abandoned = MutableStateFlow<TableState?>(null)

    /**
     * A table a previous run left behind, if any: the process died with a game
     * in progress. Shown on the home screen so it can be picked back up.
     */
    val abandoned: StateFlow<TableState?> = _abandoned.asStateFlow()

    val isRunning: Boolean get() = server != null

    /** Gives the table somewhere to survive. Called once, as the app starts. */
    fun keepLedgerIn(store: LedgerStore) {
        this.store = store
        if (!isRunning) _abandoned.value = saved()?.let { Table.restore(it).snapshot() }
    }

    private fun saved(): List<TableEvent>? = store.load()?.takeIf { it.size > 1 }

    /**
     * Opens the table. With [resume] it replays the ledger of a table that was
     * interrupted, keeping its room code, its chips and its log; the players'
     * phones reconnect on their own and find their seats waiting.
     */
    suspend fun start(preferredPort: Int = DEFAULT_PORT, resume: Boolean = false) {
        if (isRunning) return
        _status.value = _status.value.copy(starting = true, failure = null)
        val ledger = if (resume) saved() else null
        val restored = ledger?.let { runCatching { Table.restore(it) }.getOrNull() }
        val host = TableHost(
            table = restored ?: Table(_status.value.roomCode ?: newRoomCode(), TableConfig()),
            store = store,
        )
        val roomCode = host.table.roomCode
        val table = TableServer(host, preferredPort = preferredPort)
        try {
            val port = table.start()
            tableHost = host
            server = table
            _table.value = host.table.snapshot()
            _abandoned.value = null
            if (restored == null) store.save(host.table.ledger())
            _status.value = HostStatus(
                running = true,
                port = port,
                roomCode = roomCode,
                addresses = table.addresses(),
            )
        } catch (failure: Exception) {
            table.stop()
            _status.value = HostStatus(failure = HostFailure.COULD_NOT_START, roomCode = roomCode)
        }
    }

    /**
     * Ends the table for good: everybody is told before the door shuts, and the
     * saved ledger goes with it. Anything else is [stop], which only lets go of
     * the process and leaves the game recoverable.
     */
    suspend fun close() {
        tableHost?.closeAll()
        // Long enough for the goodbye to reach the phones that are listening.
        if (tableHost != null) delay(200)
        store.clear()
        _abandoned.value = null
        _status.value = _status.value.copy(roomCode = null)
        stop()
    }

    /** Throws away a saved game on purpose, to start a fresh table over it. */
    fun discardSaved() {
        store.clear()
        _abandoned.value = null
        _status.value = _status.value.copy(roomCode = null)
    }

    /**
     * Lets go of the process without ending the game: the ledger stays on disk
     * and the home screen offers to pick the table back up.
     */
    fun stop() {
        server?.stop()
        server = null
        tableHost = null
        _table.value = null
        _status.value = HostStatus(roomCode = _status.value.roomCode)
        _abandoned.value = saved()?.let { Table.restore(it).snapshot() }
    }

    /** The addresses can change under the table: the hotspot goes on and off. */
    fun refreshAddresses() {
        val running = server ?: return
        _status.value = _status.value.copy(addresses = running.addresses())
    }

    /** Collects the table state into [table] until the server stops. */
    suspend fun observeTable() {
        val host = tableHost ?: return
        host.state.collect { state -> _table.value = state }
    }
}
