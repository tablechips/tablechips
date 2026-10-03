package io.github.tablechips.app

import io.github.tablechips.core.JoinTable
import io.github.tablechips.core.PlaceBet
import io.github.tablechips.core.PlayerId
import io.github.tablechips.core.SitDown
import io.github.tablechips.core.Table
import io.github.tablechips.core.TableConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The file the whole of phase 6 rests on. A ledger that cannot be read back is
 * a game lost, so this checks the three things that actually happen on a phone:
 * a normal round trip, a file that was left half written, and a table closed
 * on purpose.
 */
class FileLedgerStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val anna = PlayerId("anna")

    private fun playing(): Table {
        val table = Table("ZGWH", TableConfig(defaultBuyIn = 1000), clock = { 0 })
        table.execute(JoinTable(anna, "Anna"))
        table.execute(SitDown(anna))
        table.execute(PlaceBet(anna, 250))
        return table
    }

    @Test
    fun `a saved ledger comes back as the same table`() {
        val store = FileLedgerStore(File(folder.root, "ledger.json"))
        val table = playing()
        store.save(table.ledger())

        val restored = Table.restore(store.load()!!)

        assertEquals(table.snapshot(), restored.snapshot())
        assertEquals(750, restored.snapshot().player(anna)!!.stack)
        assertTrue(restored.snapshot().balanced)
    }

    @Test
    fun `saving again replaces what was there`() {
        val file = File(folder.root, "ledger.json")
        val store = FileLedgerStore(file)
        val table = playing()
        store.save(table.ledger())
        table.execute(PlaceBet(anna, 100))
        store.save(table.ledger())

        assertEquals(350, Table.restore(store.load()!!).snapshot().pots.first().amount)
        // Nothing left behind but the ledger itself.
        assertEquals(listOf("ledger.json"), folder.root.list()!!.sorted())
    }

    @Test
    fun `a file that cannot be read is thrown away rather than half believed`() {
        val file = File(folder.root, "ledger.json")
        file.writeText("[{\"type\":\"table_opened\"")

        val store = FileLedgerStore(file)

        assertNull(store.load())
        assertTrue(!file.exists())
    }

    @Test
    fun `closing the table leaves nothing to recover`() {
        val file = File(folder.root, "ledger.json")
        val store = FileLedgerStore(file)
        store.save(playing().ledger())

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun `an empty ledger is not a table`() {
        val store = FileLedgerStore(File(folder.root, "ledger.json"))
        store.save(emptyList())
        assertNull(store.load())
    }
}
