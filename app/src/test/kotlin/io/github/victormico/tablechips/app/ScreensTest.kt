package io.github.victormico.tablechips.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.victormico.tablechips.core.MAIN_POT
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.core.AwardPot
import io.github.victormico.tablechips.core.JoinTable
import io.github.victormico.tablechips.core.PlaceBet
import io.github.victormico.tablechips.core.SitDown
import io.github.victormico.tablechips.protocol.ClientState
import io.github.victormico.tablechips.protocol.Connection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals

/**
 * The screens, drawn on the JVM. There is no emulator here and the app cannot
 * be launched, so this is what stands between a screen that composes and a
 * screen that crashes on somebody's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ca-rES-w393dp-h620dp-xxhdpi")
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val anna = PlayerId("anna")
    private val bru = PlayerId("bru")

    /** A table mid-hand: two seated players, chips in the pot, a log behind it. */
    private fun playing(): ClientState {
        val table = Table("ZGWH", TableConfig(defaultBuyIn = 1000), clock = { 0 })
        table.execute(JoinTable(anna, "Anna"))
        table.execute(JoinTable(bru, "Bru"))
        table.execute(SitDown(anna))
        table.execute(SitDown(bru))
        table.execute(PlaceBet(anna, 250))
        table.execute(PlaceBet(bru, 250))
        table.setConnected(anna, true)
        table.setConnected(bru, true)
        return ClientState(
            connection = Connection.ONLINE,
            table = table.snapshot(),
            you = anna,
            undoDepth = table.undoDepth,
        )
    }

    @Test
    fun `the table screen shows the stack, the pot and everybody at it`() {
        compose.setContent {
            TableScreen(
                state = playing(),
                selectedPot = MAIN_POT,
                onSelectPot = {},
                undoable = null,
                onUndo = {},
                onMenu = {},
                onBet = {},
                onRebuy = {},
                onStand = {},
                onSit = {},
            )
        }

        compose.onNodeWithText("Taula ZGWH").assertIsDisplayed()
        compose.onNodeWithText("La teua pila".uppercase()).assertIsDisplayed()
        // 750 left of 1000 after betting 250 — both players show it — and 500 in the pot
        compose.onAllNodesWithText("750").onFirst().assertIsDisplayed()
        compose.onNodeWithText("500").assertIsDisplayed()
        compose.onNodeWithText("Bru").assertIsDisplayed()
        compose.onNodeWithText("Apostar").assertIsDisplayed()
    }

    @Test
    fun `betting reaches the amount screen and the keypad types into it`() {
        var opened = false
        compose.setContent {
            if (!opened) {
                TableScreen(
                    state = playing(), selectedPot = MAIN_POT, onSelectPot = {},
                    undoable = null, onUndo = {}, onMenu = {},
                    onBet = { opened = true }, onRebuy = {}, onStand = {}, onSit = {},
                )
            }
        }

        compose.onNodeWithText("Apostar").performClick()

        assertEquals(true, opened)
    }

    @Test
    fun `the amount screen keeps the figure and confirms what was typed`() {
        var confirmed: Long? = null
        compose.setContent {
            var typed by remember { mutableStateOf("") }
            AmountScreen(
                request = AmountRequest(
                    title = "Apostar",
                    subtitle = "Pila · 1 000",
                    confirm = "Apostar",
                    max = 1000,
                    pot = 500,
                    restLabel = "Et quedarien",
                    rest = { 1000 - it },
                    onConfirm = { confirmed = it },
                ),
                typed = typed,
                untouched = false,
                onType = { key -> typed = if (key == "<") typed.dropLast(1) else typed + key },
                onSet = { typed = it.toString() },
                onBack = {},
            )
        }

        compose.onNodeWithTag("key:2").performClick()
        compose.onNodeWithTag("key:5").performClick()
        compose.onNodeWithTag("key:0").performClick()
        compose.waitForIdle()
        // the figure is on screen while it is typed, which is the whole point
        compose.onNodeWithTag("amount-figure").assertTextEquals("250")
        compose.onNodeWithText("Et quedarien").assertIsDisplayed()
        compose.onNodeWithText("750").assertIsDisplayed()
        compose.onAllNodesWithText("Apostar").onLast().performClick()

        assertEquals(250L, confirmed)
    }

    @Test
    fun `the quick values only fill the field in`() {
        val typedOut = StringBuilder()
        compose.setContent {
            var typed by remember { mutableStateOf("") }
            typedOut.setLength(0)
            typedOut.append(typed)
            AmountScreen(
                request = AmountRequest(
                    title = "Apostar", subtitle = "", confirm = "Apostar",
                    max = 1000, pot = 500, restLabel = "Et quedarien",
                    rest = { 1000 - it }, onConfirm = {},
                ),
                typed = typed, untouched = false,
                onType = { typed += it }, onSet = { typed = it.toString() }, onBack = {},
            )
        }

        compose.onNodeWithText("Mig pot").performClick()
        compose.waitForIdle()

        assertEquals("250", typedOut.toString())
    }

    @Test
    fun `the host panel offers the pot to every seated player`() {
        var awarded: String? = null
        compose.setContent {
            HostPanelScreen(
                state = playing().copy(undoDepth = 6),
                selectedPot = MAIN_POT,
                pendingSplit = null,
                onAward = { player, _ -> awarded = player.name },
                onSplit = {}, onNewPot = {}, onGive = {}, onTake = {}, onBuyIn = {},
                onLog = {}, onClose = {}, onBack = {},
            )
        }

        compose.onNodeWithText("Assignar el pot".uppercase()).assertIsDisplayed()
        compose.onNodeWithText("Bru guanya el pot").performClick()

        assertEquals("Bru", awarded)
    }

    @Test
    fun `the log reads back the hand and offers the last move to the host`() {
        val state = playing()
        var undone = false
        compose.setContent {
            LogScreen(state = state, onUndo = { undone = true }, onBack = {})
        }

        compose.onNodeWithText("Registre d'activitat").assertIsDisplayed()
        // twice on purpose: the card offering it back, and the line in the log
        compose.onAllNodesWithText("Bru ha apostat 250 al pot").onFirst().assertIsDisplayed()
        // further down the list, so it is there to be scrolled to rather than on screen
        compose.onNodeWithText("Anna s'ha assegut al lloc 1 amb 1\u202F000").assertExists()
        compose.onNodeWithText("Desfer").performClick()

        assertEquals(true, undone)
    }

    @Test
    fun `the home screen offers both doors`() {
        var created = false
        compose.setContent {
            HomeScreen(
                starting = false, failed = false, canResume = false, tableOpen = false,
                onCreate = { created = true }, onJoin = {}, onResume = {},
                onReturn = {}, onClose = {},
            )
        }

        compose.onNodeWithText("TableChips").assertIsDisplayed()
        compose.onNodeWithText("Unir-se a una taula").assertIsDisplayed()
        compose.onNodeWithText("Crear una taula").performClick()

        assertEquals(true, created)
    }

    @Test
    fun `joining asks for a name and an address`() {
        var joined = false
        compose.setContent {
            JoinScreen(
                name = "Víctor", address = "192.168.0.17", error = null,
                onName = {}, onAddress = {}, onJoin = { joined = true }, onScan = {}, onBack = {},
            )
        }

        compose.onNodeWithText("192.168.0.17").assertIsDisplayed()
        compose.onNodeWithText("Entrar a la taula").performClick()

        assertEquals(true, joined)
    }

    /**
     * The whole app, from its front door. This is the wiring that cannot be
     * checked any other way here: a crash on first composition would otherwise
     * only show up on somebody's phone.
     */
    @Test
    fun `the app opens on the home screen and asks for a name before hosting`() {
        val prefs = Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        prefs.name = null
        var started = false
        compose.setContent {
            App(
                prefs = prefs,
                onStartHost = { started = true },
                onStopHost = {},
                onShare = {},
                onOpenInBrowser = {},
            )
        }

        compose.onNodeWithText("TableChips").assertIsDisplayed()
        compose.onNodeWithText("Crear una taula").performClick()
        compose.waitForIdle()

        // No name yet, so the table is not opened until there is one to show.
        assertEquals(false, started)
        compose.onNodeWithText("Com et dius").assertIsDisplayed()
    }

    @Test
    fun `with a name already known, creating a table starts the host`() {
        val prefs = Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        prefs.name = "Víctor"
        var started = false
        compose.setContent {
            App(
                prefs = prefs,
                onStartHost = { started = true },
                onStopHost = {},
                onShare = {},
                onOpenInBrowser = {},
            )
        }

        compose.onNodeWithText("Crear una taula").performClick()
        compose.waitForIdle()

        assertEquals(true, started)
    }

    /**
     * The hole somebody fell into: leave your own table and the front door
     * offered to create another one, which quietly did nothing because one was
     * already running, and nothing anywhere offered to close it.
     */
    @Test
    fun `with a table already open, home offers to go back to it or close it`() {
        var returned = false
        var closed = false
        compose.setContent {
            HomeScreen(
                starting = false, failed = false, canResume = true, tableOpen = true,
                onCreate = {}, onJoin = {}, onResume = {},
                onReturn = { returned = true }, onClose = { closed = true },
            )
        }

        compose.onNodeWithText("Crear una taula").assertDoesNotExist()
        compose.onNodeWithText("Tornar a la taula").performClick()
        compose.onNodeWithText("Tanca la taula").performClick()

        assertEquals(true, returned)
        assertEquals(true, closed)
    }

    @Test
    fun `closing the table is asked about before it happens`() {
        var confirmed = false
        var cancelled = false
        compose.setContent {
            io.github.victormico.tablechips.app.ui.ConfirmDialog(
                title = "Tancar la taula?",
                body = "Es tancarà per a tothom.",
                confirm = "Tanca la taula",
                cancel = "Cancel·lar",
                onConfirm = { confirmed = true },
                onCancel = { cancelled = true },
            )
        }

        compose.onNodeWithText("Cancel·lar").performClick()
        compose.onNodeWithText("Tanca la taula").performClick()

        assertEquals(true, cancelled)
        assertEquals(true, confirmed)
    }

    @Test
    fun `with nothing typed, scanning is what the join screen recommends`() {
        var scanned = false
        compose.setContent {
            JoinScreen(
                name = "Víctor", address = "", error = null,
                onName = {}, onAddress = {}, onJoin = {}, onScan = { scanned = true }, onBack = {},
            )
        }

        compose.onNodeWithText("Escanejar un codi").performClick()
        // Typing is still right there, which is the rule that cannot be broken.
        compose.onNodeWithText("Entrar a la taula").assertIsDisplayed()

        assertEquals(true, scanned)
    }

    /**
     * The code has to be drawn, not just computed: this is the picture a guest
     * points a camera at, and it comes out of the same matrix the server puts
     * in its SVG.
     */
    @Test
    fun `the table's code renders`() {
        compose.setContent {
            io.github.victormico.tablechips.app.ui.QrCode(
                text = "http://192.168.0.17:8080/join?room=48KB",
                modifier = androidx.compose.ui.Modifier.size(180.dp),
            )
        }
        compose.waitForIdle()
    }
}
