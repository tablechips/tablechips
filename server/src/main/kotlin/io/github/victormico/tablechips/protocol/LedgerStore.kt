package io.github.victormico.tablechips.protocol

import io.github.victormico.tablechips.core.TableEvent

/**
 * Somewhere the ledger survives the process that produced it.
 *
 * A game lasts hours and the host's phone is a phone: it runs out of battery,
 * gets killed for memory, is dropped. Everything the table knows is the fold
 * of these events, so keeping them is keeping the game.
 */
interface LedgerStore {
    /** The ledger left behind by a previous run, or null for a fresh table. */
    fun load(): List<TableEvent>?

    fun save(events: List<TableEvent>)

    /** The table is over on purpose; nothing is worth restoring. */
    fun clear()
}

/** What the tests and a desktop run use: nothing outlives the process. */
object NoLedgerStore : LedgerStore {
    override fun load(): List<TableEvent>? = null
    override fun save(events: List<TableEvent>) = Unit
    override fun clear() = Unit
}
