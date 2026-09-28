package io.github.victormico.tablechips.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.victormico.tablechips.core.MAIN_POT
import io.github.victormico.tablechips.core.PlayerId
import io.github.victormico.tablechips.core.Table
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.core.AwardPot
import io.github.victormico.tablechips.core.GameMode
import io.github.victormico.tablechips.core.HandOutcome
import io.github.victormico.tablechips.core.Payout
import io.github.victormico.tablechips.core.PlaceStake
import io.github.victormico.tablechips.core.SetBanker
import io.github.victormico.tablechips.core.StartHand
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
                onSeats = {}, onBanker = {}, onSettle = { _, _ -> }, onMode = {},
                onNaturalPays = {}, onNewHand = {}, onCloseRound = {}, onSplitPots = {},
                onBlinds = {}, onLog = {}, onClose = {}, onBack = {},
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
                abandoned = null,
                onCreate = { created = true }, onJoin = {}, onResume = {},
                onReturn = {}, onRecover = {}, onClose = {},
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
                onStartHost = { _, _ -> started = true },
                onStopHost = {},
                onDiscardSaved = {},
                onShare = {},
                onOpenInBrowser = {},
            )
        }

        compose.onNodeWithText("TableChips").assertIsDisplayed()
        compose.onNodeWithText("Crear una taula").performClick()
        compose.waitForIdle()

        // The name is asked on the setup screen, and without one the table
        // is not opened: there would be nothing to show the others.
        compose.onNodeWithText("Nova taula").assertIsDisplayed()
        compose.onNodeWithText("Obrir la taula").performClick()
        compose.waitForIdle()
        assertEquals(false, started)
    }

    @Test
    fun `creating a table asks how it is set up, then opens it with that`() {
        val prefs = Prefs(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        prefs.name = "Víctor"
        prefs.lastConfig = null
        var opened: Pair<Boolean, TableConfig?>? = null
        compose.setContent {
            App(
                prefs = prefs,
                onStartHost = { resume, config -> opened = resume to config },
                onStopHost = {},
                onDiscardSaved = {},
                onShare = {},
                onOpenInBrowser = {},
            )
        }

        compose.onNodeWithText("Crear una taula").performClick()
        compose.waitForIdle()
        // Nothing opens until the setup has been seen.
        assertEquals(null, opened)
        compose.onNodeWithText("Nova taula").assertIsDisplayed()

        compose.onNodeWithText("Blackjack").performClick()
        compose.onNodeWithText("Obrir la taula").performClick()
        compose.waitForIdle()

        val (resume, config) = opened!!
        assertEquals(false, resume)
        assertEquals(GameMode.BLACKJACK, config!!.mode)
        assertEquals(Payout(3, 2), config.naturalPays)
        // And the next table starts from this one.
        assertEquals(GameMode.BLACKJACK, prefs.lastConfig!!.mode)
    }

    @Test
    fun `the setup shows each game's own setting and nothing else`() {
        var config by mutableStateOf(TableConfig(defaultBuyIn = 1000))
        compose.setContent {
            SetupScreen(
                config = config, starting = false, name = "Víctor", onName = {},
                onConfig = { config = it },
                onBuyIn = {}, onBlinds = {}, onRules = {}, onOpen = {}, onBack = {},
            )
        }

        compose.onNodeWithText("La natural paga".uppercase()).assertDoesNotExist()
        compose.onNodeWithText("Set i mig").performClick()
        compose.onNodeWithText("La natural paga".uppercase()).assertIsDisplayed()
        assertEquals(Payout(2, 1), config.naturalPays)

        compose.onNodeWithText("Pòquer").performClick()
        compose.onNodeWithText("La natural paga".uppercase()).assertDoesNotExist()
        // A fiftieth of the buy-in, as a starting point shown right there.
        compose.onNodeWithText("10 / 20 · toca per canviar-les").performScrollTo().assertIsDisplayed()
        assertEquals(20L, config.bigBlind)

        compose.onNodeWithContentDescription("Un lloc menys").performScrollTo().performClick()
        assertEquals(9, config.seatCount)
    }

    @Test
    fun `the seats stop where a table stops`() {
        var config by mutableStateOf(TableConfig(seatCount = 2))
        compose.setContent {
            SetupScreen(
                config = config, starting = false, name = "Víctor", onName = {},
                onConfig = { config = it },
                onBuyIn = {}, onBlinds = {}, onRules = {}, onOpen = {}, onBack = {},
            )
        }

        compose.onNodeWithContentDescription("Un lloc menys").performScrollTo().performClick()
        assertEquals(2, config.seatCount)
    }

    /**
     * The part that is nowhere else in the app: what the people at the table
     * decide and the app does not. With this table's numbers, not generic ones.
     */
    @Test
    fun `the rules say how the game is counted and what is left to the table`() {
        compose.setContent {
            RulesScreen(
                config = TableConfig(mode = GameMode.BLACKJACK, naturalPays = Payout(3, 2), defaultBuyIn = 500),
                onBack = {},
            )
        }

        compose.onNodeWithText("Regles del joc").assertIsDisplayed()
        compose.onNodeWithText("Blackjack").assertIsDisplayed()
        compose.onNodeWithText("Què decidiu vosaltres".uppercase()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("La natural paga 3:2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Compra per defecte: 500").assertExists()
    }

    @Test
    fun `the table carries a question mark that opens the rules`() {
        var asked = false
        compose.setContent {
            TableScreen(
                state = playing(), selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onRules = { asked = true },
                onBet = {}, onRebuy = {}, onStand = {}, onSit = {},
            )
        }

        compose.onNodeWithContentDescription("Regles del joc").performClick()
        assertEquals(true, asked)
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
                abandoned = null,
                onCreate = {}, onJoin = {}, onResume = {},
                onReturn = { returned = true }, onRecover = {}, onClose = { closed = true },
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

    /**
     * A phone that is never coming back leaves a seat with chips on it. The
     * host has to be able to hand that seat to whoever is holding those chips
     * now, and to throw somebody out; both are two taps and a confirmation.
     */
    @Test
    fun `the seats screen hands a seat to somebody without one`() {
        val table = Table("ZGWH", TableConfig(defaultBuyIn = 1000), clock = { 0 })
        table.execute(JoinTable(anna, "Anna"))
        table.execute(JoinTable(bru, "Bru"))
        table.execute(SitDown(anna))
        table.execute(SitDown(bru))
        val carla = PlayerId("carla")
        table.execute(JoinTable(carla, "Carla"))
        table.setConnected(anna, true)
        table.setConnected(carla, true)
        val state = ClientState(
            connection = Connection.ONLINE,
            table = table.snapshot(),
            you = anna,
            undoDepth = table.undoDepth,
        )
        var handed: Pair<String, String>? = null
        var thrown: String? = null
        compose.setContent {
            SeatsScreen(
                state = state,
                onKick = { player -> thrown = player.name },
                onTransfer = { from, to -> handed = from.name to to.name },
                onBack = {},
            )
        }

        // Bru's phone is gone, and the screen says so before anything is done.
        compose.onNodeWithText("Bru").assertIsDisplayed()
        compose.onAllNodesWithText("Fora").onFirst().performClick()
        assertEquals("Bru", thrown)

        compose.onAllNodesWithText("Passar").onLast().performClick()
        compose.onNodeWithText("Carla").assertIsDisplayed()
        compose.onNodeWithText("Passa'l").performClick()

        assertEquals("Bru" to "Carla", handed)
    }

    /**
     * The process was killed with a game in progress. What the front door offers
     * is that game back, not an empty table over the top of it.
     */
    @Test
    fun `home offers an interrupted game back before anything else`() {
        val table = Table("ZGWH", TableConfig(defaultBuyIn = 1000), clock = { 0 })
        table.execute(JoinTable(anna, "Anna"))
        table.execute(SitDown(anna))
        table.execute(PlaceBet(anna, 250))
        var recovered = false
        var created = false
        compose.setContent {
            HomeScreen(
                starting = false, failed = false, canResume = false, tableOpen = false,
                abandoned = table.snapshot(),
                onCreate = { created = true }, onJoin = {}, onResume = {},
                onReturn = {}, onRecover = { recovered = true }, onClose = {},
            )
        }

        compose.onNodeWithText("Recupera la taula").performClick()
        assertEquals(true, recovered)
        compose.onNodeWithText("Crear-ne una de nova").performClick()
        assertEquals(true, created)
    }

    /** A table playing a game with a bank, one hand up for settling. */
    private fun bankTable(mode: GameMode = GameMode.BLACKJACK): ClientState {
        val table = Table("ZGWH", TableConfig(defaultBuyIn = 1000, mode = mode), clock = { 0 })
        table.execute(JoinTable(anna, "Anna"))
        table.execute(JoinTable(bru, "Bru"))
        table.execute(SitDown(anna))
        table.execute(SitDown(bru))
        table.execute(SetBanker(anna, anna))
        table.execute(PlaceStake(bru, 250))
        table.setConnected(anna, true)
        table.setConnected(bru, true)
        return ClientState(
            connection = Connection.ONLINE,
            table = table.snapshot(),
            you = bru,
            undoDepth = table.undoDepth,
        )
    }

    @Test
    fun `against the bank the screen shows the stake and who holds it`() {
        var staked = false
        compose.setContent {
            TableScreen(
                state = bankTable(), selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {},
                onStand = {}, onSit = {}, onStake = { staked = true },
            )
        }

        compose.onNodeWithText("La teua aposta".uppercase()).assertIsDisplayed()
        compose.onNodeWithText("250").assertIsDisplayed()
        compose.onNodeWithText("La banca".uppercase()).assertIsDisplayed()
        compose.onAllNodesWithText("Anna").onFirst().assertIsDisplayed()
        // No pot anywhere: in this game there is not one.
        compose.onNodeWithText("Pot".uppercase()).assertDoesNotExist()
        compose.onNodeWithText("Apostar").performClick()

        assertEquals(true, staked)
    }

    @Test
    fun `the banker is told to settle from the panel rather than offered a bet`() {
        val state = bankTable().let { it.copy(you = anna) }
        compose.setContent {
            TableScreen(
                state = state, selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {},
                onStand = {}, onSit = {},
            )
        }

        // The card says who holds it; the bar says what holding it means.
        compose.onNodeWithText("Tens la banca").assertIsDisplayed()
        compose.onNodeWithText(
            "Els altres juguen contra tu. Resol cada mà des del panell.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Apostar").assertDoesNotExist()
    }

    /**
     * The four buttons that are the whole of the app's opinion about a hand of
     * blackjack, and the word on the second one, which belongs to the game.
     */
    @Test
    fun `the host settles a hand from the panel, in the game's own words`() {
        var settled: Pair<String, HandOutcome>? = null
        val state = bankTable(GameMode.SEVEN_HALF).copy(you = anna)
        compose.setContent {
            HostPanelScreen(
                state = state, selectedPot = MAIN_POT, pendingSplit = null,
                onAward = { _, _ -> }, onSplit = {}, onNewPot = {}, onGive = {}, onTake = {},
                onBuyIn = {}, onSeats = {}, onBanker = {},
                onSettle = { player, outcome -> settled = player.name to outcome },
                onMode = {}, onNaturalPays = {}, onNewHand = {}, onCloseRound = {},
                onSplitPots = {}, onBlinds = {}, onLog = {}, onClose = {}, onBack = {},
            )
        }

        compose.onNodeWithText("Mans per resoldre".uppercase()).assertIsDisplayed()
        // The word on the button is the game's: set i mig here, blackjack there.
        compose.onAllNodesWithText("Set i mig").onFirst().assertExists()
        compose.onNodeWithText("Guanya").performScrollTo().performClick()

        assertEquals("Bru" to HandOutcome.WIN, settled)
    }

    /** A table mid-hand of poker, with somebody all-in for less than the rest. */
    private fun pokerState(): ClientState {
        val table = Table(
            "ZGWH",
            TableConfig(defaultBuyIn = 1000, mode = GameMode.POKER, smallBlind = 10, bigBlind = 20),
            clock = { 0 },
        )
        table.execute(JoinTable(anna, "Anna"))
        table.execute(JoinTable(bru, "Bru"))
        table.execute(SitDown(anna))
        table.execute(SitDown(bru))
        table.execute(StartHand(anna))
        table.setConnected(anna, true)
        table.setConnected(bru, true)
        return ClientState(
            connection = Connection.ONLINE,
            table = table.snapshot(),
            you = bru,
            undoDepth = table.undoDepth,
        )
    }

    @Test
    fun `at poker the button says what a call costs`() {
        var called = 0L
        var folded = false
        compose.setContent {
            TableScreen(
                state = pokerState(), selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {},
                onStand = {}, onSit = {},
                onCall = { called = it }, onFold = { folded = true },
            )
        }

        // Heads-up: Anna has the button and the small blind, Bru the big one,
        // so Bru is the one facing nothing and Anna owes the difference.
        compose.onNodeWithText("Per igualar".uppercase()).assertDoesNotExist()
        compose.onNodeWithText("Apostar").assertIsDisplayed()
        compose.onNodeWithText("Retirar-se").performClick()
        assertEquals(true, folded)
        assertEquals(0L, called)
    }

    @Test
    fun `the player who owes chips is offered the exact number`() {
        var called = 0L
        val state = pokerState().copy(you = anna)
        compose.setContent {
            TableScreen(
                state = state, selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {},
                onStand = {}, onSit = {}, onCall = { called = it },
            )
        }

        compose.onNodeWithText("Per igualar".uppercase()).assertIsDisplayed()
        compose.onNodeWithText("Igualar 10").performClick()

        assertEquals(10L, called)
    }

    @Test
    fun `the host runs the hand from the panel and can change the game`() {
        var started = false
        var picked: GameMode? = null
        val state = pokerState().copy(you = anna)
        compose.setContent {
            HostPanelScreen(
                state = state, selectedPot = MAIN_POT, pendingSplit = null,
                onAward = { _, _ -> }, onSplit = {}, onNewPot = {}, onGive = {}, onTake = {},
                onBuyIn = {}, onSeats = {}, onBanker = {}, onSettle = { _, _ -> },
                onMode = { picked = it }, onNaturalPays = {}, onNewHand = { started = true },
                onCloseRound = {}, onSplitPots = {}, onBlinds = {}, onLog = {},
                onClose = {}, onBack = {},
            )
        }

        compose.onNodeWithText("Mà nova").performScrollTo().performClick()
        assertEquals(true, started)
        compose.onNodeWithText("Joc".uppercase()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Set i mig").performScrollTo().performClick()
        assertEquals(GameMode.SEVEN_HALF, picked)
    }

    /**
     * The stack drawn as the chips a real deal gives: five of each at 905,
     * every value in its place, and the count written under each so the
     * picture never has to be counted.
     */
    @Test
    fun `a fresh stack is drawn as five chips of each value`() {
        compose.setContent { ChipStacks(905) }

        compose.onAllNodesWithText("\u00D75").assertCountEquals(5)
        listOf("100", "50", "25", "5", "1").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `the chips follow the stack as it moves`() {
        var stack by mutableStateOf(905L)
        compose.setContent { ChipStacks(stack) }

        stack = 800 // a 100 and a 5 pushed forward
        compose.waitForIdle()
        compose.onAllNodesWithText("\u00D74").assertCountEquals(2)
        compose.onAllNodesWithText("\u00D75").assertCountEquals(3)
    }

    @Test
    fun `poker blinds start at amounts the chips can pay`() {
        val config = TableConfig().forMode(GameMode.POKER)

        assertEquals(905L, config.defaultBuyIn)
        assertEquals(10L, config.bigBlind)
        assertEquals(5L, config.smallBlind)
    }

    /**
     * Nobody holds the bank yet, so nothing can be staked: taking it is the
     * one thing to offer, to anybody seated, not only the host.
     */
    @Test
    fun `with a free bank the big button takes it`() {
        val table = Table("HUGH", TableConfig(mode = GameMode.SEVEN_HALF), clock = { 0 })
        table.execute(JoinTable(anna, "Anna"))
        table.execute(JoinTable(bru, "Bru"))
        table.execute(SitDown(anna))
        table.execute(SitDown(bru))
        val state = ClientState(
            connection = Connection.ONLINE,
            table = table.snapshot(),
            you = bru, // not the host
            undoDepth = table.undoDepth,
        )
        var taken = false
        compose.setContent {
            TableScreen(
                state = state, selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {},
                onStand = {}, onSit = {}, onTakeBank = { taken = true },
            )
        }

        compose.onNodeWithText("Apostar").assertDoesNotExist()
        compose.onNodeWithText("Agafar la banca").performClick()
        assertEquals(true, taken)
    }

    private fun betRequest(max: Long?) = AmountRequest(
        title = "Apostar", subtitle = "", confirm = "Apostar", max = max,
        restLabel = "", rest = { it }, onConfirm = {},
    )

    /** Counting a bet chip by chip: the figure is always the total. */
    @Test
    fun `chips mode adds up the chips pushed forward`() {
        var typed by mutableStateOf("")
        compose.setContent {
            AmountScreen(
                request = betRequest(max = 905), typed = typed, untouched = false,
                onType = {}, onSet = { typed = it.toString() }, onBack = {}, byChips = true,
            )
        }

        compose.onNodeWithContentDescription("Una fitxa de 100 més").performClick()
        compose.onNodeWithContentDescription("Una fitxa de 100 més").performClick()
        compose.onNodeWithContentDescription("Una fitxa de 25 més").performClick()
        compose.onNodeWithContentDescription("Una fitxa de 5 més").performClick()

        assertEquals("230", typed)
        compose.onNodeWithTag("chip-count:100").assertTextEquals("2")
        compose.onNodeWithContentDescription("Una fitxa de 100 menys").performClick()
        assertEquals("130", typed)
    }

    @Test
    fun `a chip that would go past the stack cannot be added`() {
        var typed by mutableStateOf("")
        compose.setContent {
            AmountScreen(
                request = betRequest(max = 120), typed = typed, untouched = false,
                onType = {}, onSet = { typed = it.toString() }, onBack = {}, byChips = true,
            )
        }

        compose.onNodeWithContentDescription("Una fitxa de 100 més").performClick()
        // 100 in, 20 left: another 100 or a 50 would bet chips that are not there.
        compose.onNodeWithContentDescription("Una fitxa de 100 més").performClick()
        compose.onNodeWithContentDescription("Una fitxa de 50 més").performClick()
        assertEquals("100", typed)
        compose.onNodeWithContentDescription("Una fitxa de 5 més").performClick()
        assertEquals("105", typed)
    }

    @Test
    fun `switching to chips starts from the amount, in the fewest chips`() {
        compose.setContent {
            AmountScreen(
                request = betRequest(max = null), typed = "150", untouched = true,
                onType = {}, onSet = {}, onBack = {}, byChips = true,
            )
        }

        compose.onNodeWithTag("chip-count:100").assertTextEquals("1")
        compose.onNodeWithTag("chip-count:50").assertTextEquals("1")
        compose.onNodeWithTag("chip-count:5").assertTextEquals("0")
    }

    /**
     * At a poker table the row shows what each player has pushed forward this
     * round, not what they have behind.
     */
    @Test
    fun `at poker the rows show each player's bet, not their stack`() {
        compose.setContent {
            TableScreen(
                state = pokerState(), selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {},
                onStand = {}, onSit = {},
            )
        }

        // Heads-up blinds 10/20: Anna posted 10, Bru 20.
        compose.onNodeWithText("Aposta".uppercase()).assertIsDisplayed()
        compose.onNodeWithText("10").assertExists()
        compose.onNodeWithText("20").assertExists()
        // Anna's 990 behind is hers to know, not the table's.
        compose.onNodeWithText("990").assertDoesNotExist()
    }

    /** The host's name is asked with the rest of the setup, and a table needs one. */
    @Test
    fun `the setup asks for the host's name and will not open without one`() {
        var name by mutableStateOf("")
        var opened = false
        compose.setContent {
            SetupScreen(
                config = TableConfig(), starting = false, name = name, onName = { name = it },
                onConfig = {}, onBuyIn = {}, onBlinds = {}, onRules = {},
                onOpen = { opened = true }, onBack = {},
            )
        }

        compose.onNodeWithText("Obrir la taula").performClick()
        assertEquals(false, opened)

        name = "Víctor"
        compose.waitForIdle()
        compose.onNodeWithText("Obrir la taula").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `raising starts from what is owed`() {
        var raised = 0L
        val state = pokerState().copy(you = anna) // owes 10
        compose.setContent {
            TableScreen(
                state = state, selectedPot = MAIN_POT, onSelectPot = {},
                undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {},
                onStand = {}, onSit = {}, onRaise = { raised = it },
            )
        }

        compose.onNodeWithText("Pujar").performClick()
        assertEquals(10L, raised)
    }

    /**
     * A raise is only a raise above the call: at the call itself the button
     * stays off, and the line under the figure says what the least raise is.
     */
    @Test
    fun `a raise cannot be confirmed at or under the call`() {
        var typed by mutableStateOf("50")
        var confirmed = 0L
        compose.setContent {
            AmountScreen(
                request = AmountRequest(
                    title = "Pujar", subtitle = "", confirm = "Pujar",
                    initial = 50, min = 51, minLabel = "Mínim per pujar", max = 905,
                    restLabel = "Et quedarien", rest = { 905 - it }, onConfirm = { confirmed = it },
                ),
                typed = typed, untouched = false, onType = {}, onSet = { typed = it.toString() },
                onBack = {}, byChips = true,
            )
        }

        compose.onNodeWithText("Mínim per pujar").assertIsDisplayed()
        compose.onNodeWithText("Pujar  50").assertDoesNotExist()
        compose.onAllNodesWithText("Pujar").onLast().performClick()
        assertEquals(0L, confirmed)

        // A 5 on top of the call makes it a raise.
        compose.onNodeWithContentDescription("Una fitxa de 5 més").performClick()
        compose.onNodeWithText("Et quedarien").assertIsDisplayed()
        compose.onAllNodesWithText("Pujar").onLast().performClick()
        assertEquals(55L, confirmed)
    }

    /** Which hand beats which, where it gets looked up: the poker rules. */
    @Test
    fun `the poker rules rank the hands, highest first`() {
        compose.setContent {
            RulesScreen(config = TableConfig(mode = GameMode.POKER, smallBlind = 5, bigBlind = 10), onBack = {})
        }

        compose.onNodeWithText("Ordre de les jugades".uppercase()).performScrollTo().assertIsDisplayed()
        val royal = compose.onNodeWithText("Escala reial").fetchSemanticsNode().positionInRoot.y
        val twoPair = compose.onNodeWithText("Doble parella").fetchSemanticsNode().positionInRoot.y
        val pair = compose.onNodeWithText("Parella").fetchSemanticsNode().positionInRoot.y
        assertEquals(true, royal < twoPair && twoPair < pair)
        compose.onNodeWithText("Al full mana el trio: 8-8-8-K-K guanya 7-7-7-A-A.").assertExists()
    }

    @Test
    fun `other games have no hand rankings`() {
        compose.setContent { RulesScreen(config = TableConfig(mode = GameMode.BLACKJACK), onBack = {}) }

        compose.onNodeWithText("Ordre de les jugades".uppercase()).assertDoesNotExist()
    }
}
