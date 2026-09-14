package io.github.victormico.tablechips.app

import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.core.TableState
import io.github.victormico.tablechips.core.newRoomCode
import io.github.victormico.tablechips.server.DEFAULT_PORT
import io.github.victormico.tablechips.server.HostAddress
import io.github.victormico.tablechips.server.TableHost
import io.github.victormico.tablechips.server.TableServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val isRunning: Boolean get() = server != null

    suspend fun start(preferredPort: Int = DEFAULT_PORT) {
        if (isRunning) return
        _status.value = _status.value.copy(starting = true, failure = null)
        val roomCode = _status.value.roomCode ?: newRoomCode()
        val host = TableHost(Table(roomCode, TableConfig()))
        val table = TableServer(host, preferredPort = preferredPort)
        try {
            val port = table.start()
            tableHost = host
            server = table
            _table.value = host.table.snapshot()
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

    fun stop() {
        server?.stop()
        server = null
        tableHost = null
        _table.value = null
        _status.value = HostStatus(roomCode = _status.value.roomCode)
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
