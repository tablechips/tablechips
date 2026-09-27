package io.github.victormico.tablechips.app

import io.github.victormico.tablechips.core.TableEvent
import android.content.Context
import io.github.victormico.tablechips.protocol.LedgerStore
import io.github.victormico.tablechips.protocol.ProtocolJson
import java.io.File

/**
 * The ledger, on the host's own storage.
 *
 * A game of cards lasts longer than Android lets an app live: the screen goes
 * off, something else needs memory, the battery dies. Whoever was up two
 * hundred chips will not accept "the app restarted" as an answer, so every
 * accepted move is written here before anybody is told about it.
 *
 * Written whole each time, through a temporary file and a rename, because half
 * a ledger is worse than none: the rename either happened or it did not.
 */
class FileLedgerStore(private val file: File) : LedgerStore {

    private val temporary = File(file.parentFile, file.name + ".new")

    override fun load(): List<TableEvent>? {
        if (!file.exists()) return null
        return try {
            val events = ProtocolJson.decodeFromString<List<TableEvent>>(file.readText())
            events.ifEmpty { null }
        } catch (failure: Exception) {
            // A ledger we cannot read is not a table we can restore. Better to
            // start clean than to pretend: the chips would be wrong.
            clear()
            null
        }
    }

    override fun save(events: List<TableEvent>) {
        try {
            file.parentFile?.mkdirs()
            temporary.writeText(ProtocolJson.encodeToString(events))
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        } catch (failure: Exception) {
            // Out of space or no permission. The table keeps playing; it just
            // will not survive being killed, and that is not worth stopping for.
        }
    }

    override fun clear() {
        file.delete()
        temporary.delete()
    }
}

/** The one ledger file this app keeps, in storage only it can read. */
fun ledgerStore(context: Context): LedgerStore =
    FileLedgerStore(File(context.applicationContext.filesDir, "table-ledger.json"))
