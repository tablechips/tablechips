package io.github.tablechips.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.tablechips.app.ui.chips
import io.github.tablechips.core.GameMode
import io.github.tablechips.core.JoinTable
import io.github.tablechips.core.MAIN_POT
import io.github.tablechips.core.PlaceBet
import io.github.tablechips.core.PlaceStake
import io.github.tablechips.core.PlayerId
import io.github.tablechips.core.SetBanker
import io.github.tablechips.core.SitDown
import io.github.tablechips.core.Table
import io.github.tablechips.core.TableConfig
import io.github.tablechips.protocol.ClientState
import io.github.tablechips.protocol.Connection
import io.github.tablechips.server.HostAddress
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The store screenshots, drawn by the real screens on the JVM, in the three
 * languages. Not a test of anything: it only runs when asked to, and writes
 * straight into the fastlane folder F-Droid reads.
 *
 *     TABLECHIPS_SCREENSHOTS=fastlane/metadata/android ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots*'
 *
 * The directory is relative to the repository root.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StoreScreenshots(private val locale: String, private val folder: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf(
            arrayOf("ca", "ca"),
            arrayOf("es", "es-ES"),
            arrayOf("en", "en-US"),
        )
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val target: File? = System.getenv("TABLECHIPS_SCREENSHOTS")?.let { dir ->
        // Gradle runs the tests from the module's folder; the path is the repository's.
        File(File(System.getProperty("user.dir")).parentFile, dir)
    }

    @Before
    fun setUp() {
        assumeTrue("set TABLECHIPS_SCREENSHOTS to draw the store screenshots", target != null)
        // A phone held upright, at twice the density: 786 × 1702 pixels.
        RuntimeEnvironment.setQualifiers("$locale-w393dp-h851dp-xhdpi")
    }

    private val marta = PlayerId("marta")
    private val pau = PlayerId("pau")
    private val carme = PlayerId("carme")
    private val joan = PlayerId("joan")

    /** Four at the table, a hand under way: what most of a night looks like. */
    private fun midHand(): ClientState {
        val table = Table("ZGWH", TableConfig(), clock = { 0 })
        listOf(marta to "Marta", pau to "Pau", carme to "Carme", joan to "Joan").forEach { (id, name) ->
            table.execute(JoinTable(id, name))
            table.execute(SitDown(id))
            table.setConnected(id, true)
        }
        table.execute(PlaceBet(marta, 40))
        table.execute(PlaceBet(pau, 40))
        table.execute(PlaceBet(carme, 80))
        return ClientState(Connection.ONLINE, table.snapshot(), you = marta, undoDepth = table.undoDepth)
    }

    /** Set i mig, Marta holding the bank, the others staked against her. */
    private fun bankHand(): ClientState {
        val table = Table("ZGWH", TableConfig(mode = GameMode.SEVEN_HALF), clock = { 0 })
        listOf(marta to "Marta", pau to "Pau", carme to "Carme", joan to "Joan").forEach { (id, name) ->
            table.execute(JoinTable(id, name))
            table.execute(SitDown(id))
            table.setConnected(id, true)
        }
        table.execute(SetBanker(marta, marta))
        table.execute(PlaceStake(pau, 25))
        table.execute(PlaceStake(carme, 50))
        table.execute(PlaceStake(joan, 10))
        return ClientState(Connection.ONLINE, table.snapshot(), you = marta, undoDepth = table.undoDepth)
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        // The test activity has a light theme; the app's window is Refugi's bg.
        compose.runOnUiThread {
            compose.activity.window.setBackgroundDrawable(ColorDrawable(0xFF14110E.toInt()))
        }
        compose.setContent(content)
        compose.waitForIdle()
        val dir = File(target, "$folder/images/phoneScreenshots").apply { mkdirs() }
        // captureToImage() waits for a redraw Robolectric never schedules: draw
        // the window's root view onto a bitmap instead, through the same Skia.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun table() = shoot("1_table") {
        TableScreen(
            state = midHand(), selectedPot = MAIN_POT, onSelectPot = {},
            undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {}, onSit = {},
        )
    }

    @Test
    fun join() = shoot("2_join") {
        val state = midHand()
        ConnectionScreen(
            status = HostStatus(
                running = true,
                roomCode = "ZGWH",
                addresses = listOf(HostAddress("wlan0", "192.168.43.1")),
            ),
            table = state.table,
            onCopy = {}, onShare = {}, onOpenInBrowser = {}, onStop = {}, onBack = {},
        )
    }

    @Test
    fun amount() = shoot("3_amount") {
        val stack = midHand().me!!.stack
        AmountScreen(
            request = AmountRequest(
                title = stringResource(R.string.bet_title),
                subtitle = stringResource(R.string.bet_sub).format(chips(stack)),
                confirm = stringResource(R.string.action_bet),
                max = stack,
                pot = 160,
                restLabel = stringResource(R.string.amount_remaining),
                rest = { stack - it },
                onConfirm = {},
            ),
            typed = "120", untouched = false, onType = {}, onSet = {}, onBack = {},
        )
    }

    @Test
    fun bank() = shoot("4_bank") {
        TableScreen(
            state = bankHand(), selectedPot = MAIN_POT, onSelectPot = {},
            undoable = null, onUndo = {}, onMenu = {}, onBet = {}, onRebuy = {}, onSit = {},
            onSettle = { _, _, _ -> },
        )
    }

    @Test
    fun host() = shoot("5_host") {
        HostPanelScreen(
            state = midHand(), selectedPot = MAIN_POT, pendingSplit = null,
            onAward = { _, _ -> }, onSplit = {}, onNewPot = {}, onGive = {}, onTake = {}, onBuyIn = {},
            onSeats = {}, onBanker = {}, onMode = {},
            onNaturalPays = {}, onNewHand = {}, onCloseRound = {}, onSplitPots = {},
            onBlinds = {}, onLog = {}, onClose = {}, onBack = {},
        )
    }

    @Test
    fun home() = shoot("6_home") {
        HomeScreen(
            starting = false, failed = false, canResume = false, tableOpen = false, abandoned = null,
            onCreate = {}, onJoin = {}, onResume = {}, onReturn = {}, onRecover = {}, onClose = {},
        )
    }
}
