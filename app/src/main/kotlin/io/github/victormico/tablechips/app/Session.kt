package io.github.victormico.tablechips.app

import android.content.Context
import androidx.core.content.edit
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.protocol.ClientMessage
import io.github.victormico.tablechips.protocol.ClientState
import io.github.victormico.tablechips.protocol.TableClient
import io.github.victormico.tablechips.protocol.TableConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What this device remembers between sessions: who you are, and where the last
 * table was. Three keys, so plain preferences rather than a datastore. The
 * ledger is a different matter and lives in its own file, next to this one.
 */
class Prefs(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("tablechips", Context.MODE_PRIVATE)

    var name: String?
        get() = store.getString("name", null)
        set(value) = store.edit { putString("name", value) }

    /** Kept for as long as possible: it is what gets your seat back. */
    var playerId: PlayerId?
        get() = store.getString("playerId", null)?.let { PlayerId(it) }
        set(value) = store.edit { putString("playerId", value?.value) }

    var lastAddress: String?
        get() = store.getString("lastAddress", null)
        set(value) = store.edit { putString("lastAddress", value) }

    fun forgetIdentity() {
        store.edit { remove("playerId") }
    }
}

/**
 * The app's seat at a table, wherever that table is.
 *
 * The host's own device is not a special case: it connects to its own server
 * over localhost exactly like a guest connects over the hotspot, so there is
 * one code path and one protocol.
 */
object Session {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = _state.asStateFlow()

    private var client: TableClient? = null
    private var connection: TableConnection? = null
    private var mirror: Job? = null
    private var prefs: Prefs? = null

    /** The address this session is talking to, for the screen to show. */
    var address: String? = null
        private set

    val connected: Boolean get() = client != null

    fun attach(prefs: Prefs) {
        this.prefs = prefs
    }

    fun connect(url: String, name: String) {
        if (address == url && client != null) return
        disconnect()
        val store = prefs
        val opened = TableConnection(url)
        val fresh = TableClient(scope, { opened.open() })
        connection = opened
        client = fresh
        address = url
        mirror = scope.launch {
            fresh.state.collect { state ->
                _state.value = state
                // The id the table hands out is worth more than anything else
                // on this device: without it a dropped phone loses its seat.
                state.you?.let { id -> if (store?.playerId != id) store?.playerId = id }
            }
        }
        store?.name = name
        fresh.join(name, store?.playerId)
    }

    fun disconnect() {
        mirror?.cancel()
        client?.close()
        connection?.close()
        mirror = null
        client = null
        connection = null
        address = null
        _state.value = ClientState()
    }

    /** Leaves the table for good: the seat is cashed out and the id let go. */
    fun leave() {
        val leaving = client
        act(io.github.victormico.tablechips.protocol.Leave())
        prefs?.forgetIdentity()
        scope.launch {
            // Long enough for the farewell to reach the table, and pinned to
            // this connection: sitting down again in the meantime must not be
            // torn down by the goodbye of the previous one.
            kotlinx.coroutines.delay(150)
            if (client === leaving) disconnect()
        }
    }

    fun act(message: ClientMessage) {
        val open = client ?: return
        scope.launch { open.send(message) }
    }
}
