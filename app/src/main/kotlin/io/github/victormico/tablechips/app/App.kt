package io.github.victormico.tablechips.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.victormico.tablechips.app.ui.Card
import io.github.victormico.tablechips.app.ui.ConfirmDialog
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips
import io.github.victormico.tablechips.core.Player
import io.github.victormico.tablechips.core.PotId
import io.github.victormico.tablechips.core.TableConfig
import io.github.victormico.tablechips.core.MAIN_POT
import io.github.victormico.tablechips.protocol.Action
import io.github.victormico.tablechips.protocol.AdjustStackCommand
import io.github.victormico.tablechips.protocol.AwardPotCommand
import io.github.victormico.tablechips.protocol.BetAction
import io.github.victormico.tablechips.protocol.CancelStakeAction
import io.github.victormico.tablechips.protocol.CloseRoundCommand
import io.github.victormico.tablechips.protocol.FoldAction
import io.github.victormico.tablechips.protocol.CreatePotCommand
import io.github.victormico.tablechips.protocol.HostCommandMessage
import io.github.victormico.tablechips.protocol.KickCommand
import io.github.victormico.tablechips.protocol.RebuyAction
import io.github.victormico.tablechips.protocol.SetBankerCommand
import io.github.victormico.tablechips.protocol.SetConfigCommand
import io.github.victormico.tablechips.protocol.SettleCommand
import io.github.victormico.tablechips.protocol.SplitPotsCommand
import io.github.victormico.tablechips.protocol.StakeAction
import io.github.victormico.tablechips.protocol.StartHandCommand
import io.github.victormico.tablechips.protocol.TakeBankAction
import io.github.victormico.tablechips.protocol.Sit
import io.github.victormico.tablechips.protocol.StandUpAction
import io.github.victormico.tablechips.protocol.TableConnection
import io.github.victormico.tablechips.protocol.TransferSeatCommand
import io.github.victormico.tablechips.protocol.parseTableLink
import io.github.victormico.tablechips.protocol.UndoCommand
import kotlinx.coroutines.delay


/**
 * The strings the handlers need, read during composition rather than from the
 * context when a button is pressed: that is what keeps them right if the phone
 * changes language while the app is open. The ones with a placeholder are kept
 * as templates and filled in at the moment of use.
 */
private class AppTexts(
    val actionBet: String,
    val amountRemaining: String,
    val amountResulting: String,
    val betSub: String,
    val betTitle: String,
    val bankStake: String,
    val bankStakeTitle: String,
    val pokerBlinds: String,
    val pokerRaise: String,
    val pokerRaiseSub: String,
    val pokerRaiseMin: String,
    val pokerBlindsSub: String,
    val buyInConfirm: String,
    val buyInSub: String,
    val giveTitle: String,
    val hostPanelBuyIn: String,
    val hostPanelGive: String,
    val hostPanelNewPotName: String,
    val hostPanelSplit: String,
    val hostPanelTake: String,
    val rebuyConfirm: String,
    val rebuyTitle: String,
    val sitConfirm: String,
    val sitSub: String,
    val sitTitle: String,
    val splitSub: String,
    val splitTitle: String,
    val takeTitle: String,
)

@Composable
private fun appTexts(): AppTexts = AppTexts(
    actionBet = stringResource(R.string.action_bet),
    amountRemaining = stringResource(R.string.amount_remaining),
    amountResulting = stringResource(R.string.amount_resulting),
    betSub = stringResource(R.string.bet_sub),
    betTitle = stringResource(R.string.bet_title),
    bankStake = stringResource(R.string.bank_stake),
    bankStakeTitle = stringResource(R.string.bank_stake_title),
    pokerBlinds = stringResource(R.string.poker_blinds),
    pokerRaise = stringResource(R.string.poker_raise),
    pokerRaiseSub = stringResource(R.string.poker_raise_sub),
    pokerRaiseMin = stringResource(R.string.poker_raise_min),
    pokerBlindsSub = stringResource(R.string.poker_blinds_sub),
    buyInConfirm = stringResource(R.string.buy_in_confirm),
    buyInSub = stringResource(R.string.buy_in_sub),
    giveTitle = stringResource(R.string.give_title),
    hostPanelBuyIn = stringResource(R.string.host_panel_buy_in),
    hostPanelGive = stringResource(R.string.host_panel_give),
    hostPanelNewPotName = stringResource(R.string.host_panel_new_pot_name),
    hostPanelSplit = stringResource(R.string.host_panel_split),
    hostPanelTake = stringResource(R.string.host_panel_take),
    rebuyConfirm = stringResource(R.string.rebuy_confirm),
    rebuyTitle = stringResource(R.string.rebuy_title),
    sitConfirm = stringResource(R.string.sit_confirm),
    sitSub = stringResource(R.string.sit_sub),
    sitTitle = stringResource(R.string.sit_title),
    splitSub = stringResource(R.string.split_sub),
    splitTitle = stringResource(R.string.split_title),
    takeTitle = stringResource(R.string.take_title),
)

/** A question that has to be answered before something irreversible happens. */
private sealed interface Ask {
    data object CloseTable : Ask
    data object Leave : Ask
    data object DiscardSaved : Ask
    data class Kick(val player: Player) : Ask
    data class HandSeat(val from: Player, val to: Player) : Ask
}

private sealed interface Screen {
    data object Home : Screen
    data object Name : Screen
    data object Join : Screen
    data object Scan : Screen
    data object Table : Screen
    data object Amount : Screen
    data object Setup : Screen
    data object Rename : Screen
    data object Rules : Screen
    data object HostPanel : Screen
    data object Seats : Screen
    data object Log : Screen
    data object Connection : Screen
}

/** How long the table screen keeps offering the last move back. */
private const val UNDO_WINDOW_MILLIS = 30_000L

@Composable
fun App(
    prefs: Prefs,
    /**
     * Opens the table: a saved game picked back up, or a fresh one with the
     * config from the setup screen.
     */
    onStartHost: (resume: Boolean, config: TableConfig?) -> Unit,
    onStopHost: () -> Unit,
    onDiscardSaved: () -> Unit,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    val texts = appTexts()
    val hostStatus by HostController.status.collectAsStateWithLifecycle()
    val abandoned by HostController.abandoned.collectAsStateWithLifecycle()
    val incoming by IncomingLinks.link.collectAsStateWithLifecycle()
    val state by Session.state.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var menuOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(prefs.name.orEmpty()) }
    var address by remember { mutableStateOf(prefs.lastAddress.orEmpty()) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var selectedPot by remember { mutableStateOf(MAIN_POT) }
    var pendingSplit by remember { mutableStateOf<Long?>(null) }
    var request by remember { mutableStateOf<AmountRequest?>(null) }
    var typed by remember { mutableStateOf("") }
    var untouched by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf<Ask?>(null) }
    // The table being set up, starting from how the last one was.
    var setup by remember { mutableStateOf(prefs.lastConfig ?: TableConfig()) }
    var rulesFrom by remember { mutableStateOf<Screen>(Screen.Table) }
    // Asking for a name comes before both creating and recovering a table.
    var recovering by remember { mutableStateOf(false) }
    // Whoever counts in chips keeps counting in chips: it is a way of playing.
    var byChips by remember { mutableStateOf(prefs.amountByChips) }

    // The host is a player at its own table, over localhost, exactly like a
    // guest over the hotspot. One code path, no special case.
    LaunchedEffect(hostStatus.running, hostStatus.port, name) {
        if (hostStatus.running && name.isNotBlank()) {
            Session.connect(TableConnection.webSocketUrl("127.0.0.1", hostStatus.port), name)
        }
    }
    LaunchedEffect(state.table != null) {
        if (state.table != null && screen in listOf(Screen.Home, Screen.Name, Screen.Join, Screen.Setup)) {
            screen = Screen.Table
        }
    }
    // A link from outside only fills the join screen in. What it carries was
    // chosen by whoever made the code, so a person looks at it first.
    LaunchedEffect(incoming) {
        incoming?.let { link ->
            address = link.address
            addressError = null
            screen = Screen.Join
            IncomingLinks.consume()
        }
    }
    // One timer per move, so the undo offer expires without anything ticking.
    LaunchedEffect(state.table?.rev) {
        delay(UNDO_WINDOW_MILLIS)
        tick++
    }

    val table = state.table
    val lastEntry = table?.log?.lastOrNull()
    val undoable = remember(state.table?.rev, tick) {
        lastEntry?.takeIf { System.currentTimeMillis() - it.at < UNDO_WINDOW_MILLIS }
    }

    fun openAmount(next: AmountRequest) {
        request = next
        typed = next.initial?.toString().orEmpty()
        untouched = typed.isNotEmpty()
        screen = Screen.Amount
    }

    fun back() {
        screen = if (state.table != null) Screen.Table else Screen.Home
    }

    fun openTable() {
        name = name.trim()
        prefs.name = name
        prefs.lastConfig = setup
        onStartHost(false, setup)
    }

    fun closeTable() {
        onStopHost()
        Session.disconnect()
        screen = Screen.Home
    }

    Box(
        Modifier.fillMaxSize().background(Refugi.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (screen) {
            Screen.Home -> HomeScreen(
                starting = hostStatus.starting,
                failed = hostStatus.failure != null,
                canResume = prefs.lastAddress != null,
                tableOpen = hostStatus.running,
                abandoned = abandoned,
                // The name is asked on the setup screen itself, with the rest.
                onCreate = {
                    recovering = false
                    screen = Screen.Setup
                },
                onRecover = {
                    recovering = true
                    if (name.isBlank()) screen = Screen.Name else onStartHost(true, null)
                },
                onReturn = {
                    // The table is up; this only takes a seat at it again.
                    Session.connect(
                        TableConnection.webSocketUrl("127.0.0.1", hostStatus.port),
                        name.ifBlank { prefs.name.orEmpty() },
                    )
                },
                onClose = { asking = Ask.CloseTable },
                onJoin = { screen = Screen.Join },
                onResume = {
                    prefs.lastAddress?.let { last ->
                        address = last
                        screen = Screen.Join
                    }
                },
            )

            Screen.Name -> NameScreen(
                name = name,
                onName = { name = it },
                onDone = {
                    prefs.name = name.trim()
                    if (recovering) onStartHost(true, null) else screen = Screen.Setup
                },
                onBack = { screen = Screen.Home },
                confirm = stringResource(if (recovering) R.string.home_recover else R.string.home_create),
            )

            Screen.Rename -> NameScreen(
                name = name,
                onName = { name = it },
                onDone = {
                    name = name.trim()
                    Session.rename(name)
                    screen = Screen.Table
                },
                onBack = {
                    name = prefs.name.orEmpty()
                    screen = Screen.Table
                },
                confirm = stringResource(R.string.common_save),
            )

            Screen.Join -> JoinScreen(
                name = name,
                address = address,
                error = addressError,
                onName = { name = it },
                onAddress = { address = it; addressError = null },
                onJoin = {
                    val url = TableConnection.parse(address, io.github.victormico.tablechips.server.DEFAULT_PORT)
                    when {
                        name.isBlank() -> addressError = "name_required"
                        url == null -> addressError = "bad_address"
                        else -> {
                            prefs.name = name.trim()
                            prefs.lastAddress = address.trim()
                            Session.connect(url, name.trim())
                        }
                    }
                },
                onScan = { screen = Screen.Scan },
                onBack = { screen = Screen.Home },
            )

            Screen.Scan -> ScannerScreen(
                onCode = { text ->
                    val link = parseTableLink(text, io.github.victormico.tablechips.server.DEFAULT_PORT)
                    if (link == null) {
                        addressError = "unreadable"
                    } else {
                        address = link.address
                        addressError = null
                    }
                    screen = Screen.Join
                },
                onType = { screen = Screen.Join },
                onBack = { screen = Screen.Join },
            )

            Screen.Setup -> SetupScreen(
                config = setup,
                starting = hostStatus.starting,
                failed = hostStatus.failure != null,
                name = name,
                onName = { name = it },
                onConfig = { setup = it },
                onBuyIn = {
                    openAmount(
                        AmountRequest(
                            title = texts.hostPanelBuyIn,
                            subtitle = texts.buyInSub,
                            confirm = texts.buyInConfirm,
                            initial = setup.defaultBuyIn,
                            dealt = true,
                            allowZero = true,
                            restLabel = texts.hostPanelBuyIn,
                            rest = { it },
                            onConfirm = { amount ->
                                setup = setup.copy(defaultBuyIn = amount)
                                screen = Screen.Setup
                            },
                            onCancel = { screen = Screen.Setup },
                        ),
                    )
                },
                onBlinds = {
                    openAmount(
                        AmountRequest(
                            title = texts.pokerBlinds,
                            subtitle = texts.pokerBlindsSub,
                            confirm = texts.buyInConfirm,
                            initial = setup.bigBlind.takeIf { it > 0 },
                            allowZero = true,
                            restLabel = texts.pokerBlinds,
                            rest = { it / 2 },
                            onConfirm = { amount ->
                                setup = setup.copy(bigBlind = amount, smallBlind = amount / 2)
                                screen = Screen.Setup
                            },
                            onCancel = { screen = Screen.Setup },
                        ),
                    )
                },
                onRules = { rulesFrom = Screen.Setup; screen = Screen.Rules },
                // Opening a new table over an interrupted one throws that one
                // away, so it is asked about here, at the last moment, and not
                // before the setup: backing out must not have cost anything.
                onOpen = { if (abandoned != null) asking = Ask.DiscardSaved else openTable() },
                onBack = { screen = Screen.Home },
            )

            Screen.Rules -> RulesScreen(
                config = if (rulesFrom == Screen.Setup) setup else table?.config ?: setup,
                onBack = { screen = rulesFrom },
            )

            Screen.Table -> TableScreen(
                state = state,
                selectedPot = selectedPot,
                onSelectPot = { selectedPot = it },
                undoable = undoable?.let { entry -> table?.let { logLine(entry, it) } },
                onUndo = { Session.act(HostCommandMessage(UndoCommand)) },
                onMenu = { menuOpen = true },
                onRules = { rulesFrom = Screen.Table; screen = Screen.Rules },
                onBet = {
                    val me = state.me ?: return@TableScreen
                    val pot = table?.pots?.firstOrNull { it.id == selectedPot }?.amount ?: 0
                    openAmount(
                        AmountRequest(
                            title = texts.betTitle,
                            subtitle = texts.betSub.format(chips(me.stack)),
                            confirm = texts.actionBet,
                            max = me.stack,
                            pot = pot,
                            restLabel = texts.amountRemaining,
                            rest = { me.stack - it },
                            onConfirm = { amount ->
                                Session.act(Action(BetAction(amount, selectedPot)))
                                back()
                            },
                        ),
                    )
                },
                onRebuy = {
                    val me = state.me ?: return@TableScreen
                    openAmount(
                        AmountRequest(
                            title = texts.rebuyTitle,
                            subtitle = texts.betSub.format(chips(me.stack)),
                            confirm = texts.rebuyConfirm,
                            initial = table?.config?.defaultBuyIn,
                            dealt = true,
                            restLabel = texts.amountResulting,
                            rest = { me.stack + it },
                            onConfirm = { amount ->
                                Session.act(Action(RebuyAction(amount)))
                                back()
                            },
                        ),
                    )
                },
                onStand = { Session.act(Action(StandUpAction)) },
                onStake = {
                    val me = state.me ?: return@TableScreen
                    openAmount(
                        AmountRequest(
                            title = texts.bankStakeTitle,
                            subtitle = texts.betSub.format(chips(me.stack)),
                            confirm = texts.bankStake,
                            max = me.stack,
                            restLabel = texts.amountRemaining,
                            rest = { me.stack - it },
                            onConfirm = { amount ->
                                Session.act(Action(StakeAction(amount)))
                                back()
                            },
                        ),
                    )
                },
                onCancelStake = { Session.act(Action(CancelStakeAction)) },
                onTakeBank = { Session.act(Action(TakeBankAction)) },
                // A call goes into the pot being played for, not into whichever
                // side pot the screen happens to be showing.
                onCall = { amount -> Session.act(Action(BetAction(amount, MAIN_POT))) },
                onFold = { Session.act(Action(FoldAction)) },
                // A raise starts from the call — those chips go in either way —
                // and is only a raise once it goes over it.
                onRaise = { owed ->
                    val me = state.me ?: return@TableScreen
                    openAmount(
                        AmountRequest(
                            title = texts.pokerRaise,
                            subtitle = texts.pokerRaiseSub.format(chips(owed)),
                            confirm = texts.pokerRaise,
                            initial = owed,
                            min = owed + 1,
                            minLabel = texts.pokerRaiseMin,
                            max = me.stack,
                            restLabel = texts.amountRemaining,
                            rest = { me.stack - it },
                            onConfirm = { amount ->
                                Session.act(Action(BetAction(amount, MAIN_POT)))
                                back()
                            },
                        ),
                    )
                },
                onSit = {
                    // Back from the bar with chips: sit straight down with them.
                    // The buy-in screen is for somebody bringing chips in.
                    if ((state.me?.stack ?: 0) > 0) {
                        Session.act(Sit(seat = null, buyIn = 0))
                        return@TableScreen
                    }
                    openAmount(
                        AmountRequest(
                            title = texts.sitTitle,
                            subtitle = texts.sitSub.format(chips(table?.config?.defaultBuyIn ?: 0)),
                            confirm = texts.sitConfirm,
                            initial = table?.config?.defaultBuyIn,
                            dealt = true,
                            allowZero = true,
                            restLabel = texts.amountResulting,
                            rest = { it },
                            onConfirm = { amount ->
                                Session.act(Sit(seat = null, buyIn = amount))
                                back()
                            },
                        ),
                    )
                },
            )

            Screen.Amount -> if (request == null) back() else request?.let { current ->
                AmountScreen(
                    request = current,
                    typed = typed,
                    untouched = untouched,
                    onType = { key ->
                        if (key == "<") {
                            untouched = false
                            typed = typed.dropLast(1)
                        } else {
                            if (untouched) { typed = ""; untouched = false }
                            if (typed.length + key.length <= 9) {
                                typed = (if (typed == "0") "" else typed) + key
                            }
                        }
                    },
                    onSet = { value -> typed = value.coerceAtLeast(0).toString(); untouched = false },
                    onBack = { current.onCancel?.invoke() ?: back() },
                    byChips = byChips,
                    onByChips = { byChips = it; prefs.amountByChips = it },
                )
            }

            Screen.HostPanel -> HostPanelScreen(
                state = state,
                selectedPot = selectedPot,
                pendingSplit = pendingSplit,
                onAward = { player, part ->
                    Session.act(
                        HostCommandMessage(AwardPotCommand(player.id, selectedPot, part)),
                    )
                    pendingSplit = null
                },
                onSplit = {
                    val pot = table?.pots?.firstOrNull { it.id == selectedPot }?.amount ?: 0
                    openAmount(
                        AmountRequest(
                            title = texts.splitTitle,
                            subtitle = texts.splitSub.format(chips(pot)),
                            confirm = texts.hostPanelSplit,
                            max = pot,
                            restLabel = texts.amountRemaining,
                            rest = { pot - it },
                            onConfirm = { amount ->
                                pendingSplit = amount
                                screen = Screen.HostPanel
                            },
                        ),
                    )
                },
                onNewPot = {
                    Session.act(
                        HostCommandMessage(CreatePotCommand(texts.hostPanelNewPotName)),
                    )
                },
                onGive = { player ->
                    openAmount(
                        adjustRequest(
                            player = player,
                            give = true,
                            title = texts.giveTitle.format(player.name),
                            subtitle = texts.betSub.format(chips(player.stack)),
                            confirm = texts.hostPanelGive,
                            restLabel = texts.amountResulting,
                            initial = table?.config?.defaultBuyIn,
                        ) { screen = Screen.HostPanel },
                    )
                },
                onTake = { player ->
                    openAmount(
                        adjustRequest(
                            player = player,
                            give = false,
                            title = texts.takeTitle.format(player.name),
                            subtitle = texts.betSub.format(chips(player.stack)),
                            confirm = texts.hostPanelTake,
                            restLabel = texts.amountResulting,
                            initial = null,
                        ) { screen = Screen.HostPanel },
                    )
                },
                onBuyIn = {
                    val config = table?.config ?: return@HostPanelScreen
                    openAmount(
                        AmountRequest(
                            title = texts.hostPanelBuyIn,
                            subtitle = texts.buyInSub,
                            confirm = texts.buyInConfirm,
                            initial = config.defaultBuyIn,
                            dealt = true,
                            allowZero = true,
                            restLabel = texts.hostPanelBuyIn,
                            rest = { it },
                            onConfirm = { amount ->
                                Session.act(
                                    HostCommandMessage(SetConfigCommand(config.copy(defaultBuyIn = amount))),
                                )
                                screen = Screen.HostPanel
                            },
                        ),
                    )
                },
                onSeats = { screen = Screen.Seats },
                onBanker = { player -> Session.act(HostCommandMessage(SetBankerCommand(player?.id))) },
                onSettle = { player, outcome ->
                    Session.act(HostCommandMessage(SettleCommand(player.id, outcome)))
                },
                onMode = { mode ->
                    val config = table?.config ?: return@HostPanelScreen
                    Session.act(HostCommandMessage(SetConfigCommand(config.copy(mode = mode))))
                },
                onNaturalPays = { pays ->
                    val config = table?.config ?: return@HostPanelScreen
                    Session.act(HostCommandMessage(SetConfigCommand(config.copy(naturalPays = pays))))
                },
                onNewHand = { Session.act(HostCommandMessage(StartHandCommand)) },
                onCloseRound = { Session.act(HostCommandMessage(CloseRoundCommand)) },
                onSplitPots = { Session.act(HostCommandMessage(SplitPotsCommand)) },
                onBlinds = {
                    val config = table?.config ?: return@HostPanelScreen
                    openAmount(
                        AmountRequest(
                            title = texts.pokerBlinds,
                            subtitle = texts.pokerBlindsSub,
                            confirm = texts.buyInConfirm,
                            initial = config.bigBlind.takeIf { it > 0 },
                            allowZero = true,
                            restLabel = texts.pokerBlinds,
                            rest = { it / 2 },
                            onConfirm = { amount ->
                                // One number to type: the big blind. The small
                                // one is half of it, as at any table that does
                                // not say otherwise.
                                Session.act(
                                    HostCommandMessage(
                                        SetConfigCommand(
                                            config.copy(bigBlind = amount, smallBlind = amount / 2),
                                        ),
                                    ),
                                )
                                screen = Screen.HostPanel
                            },
                        ),
                    )
                },
                onLog = { screen = Screen.Log },
                onClose = if (hostStatus.running) ({ asking = Ask.CloseTable }) else null,
                onBack = { screen = Screen.Table },
            )

            Screen.Seats -> SeatsScreen(
                state = state,
                onKick = { player -> asking = Ask.Kick(player) },
                onTransfer = { from, to -> asking = Ask.HandSeat(from, to) },
                onBack = { screen = Screen.HostPanel },
            )

            Screen.Log -> LogScreen(
                state = state,
                onUndo = { Session.act(HostCommandMessage(UndoCommand)) },
                onBack = { screen = Screen.Table },
            )

            Screen.Connection -> ConnectionScreen(
                status = hostStatus,
                table = table,
                onCopy = { },
                onShare = onShare,
                onOpenInBrowser = onOpenInBrowser,
                onStop = { asking = Ask.CloseTable },
                onBack = { screen = Screen.Table },
            )
        }

        // Leaving your seat and closing the table are different things, and the
        // difference matters most to the host: the table outlives the seat.
        when (asking) {
            Ask.CloseTable -> ConfirmDialog(
                title = stringResource(R.string.host_close_title),
                body = stringResource(R.string.host_close_body),
                confirm = stringResource(R.string.host_stop),
                cancel = stringResource(R.string.common_cancel),
                onConfirm = { asking = null; closeTable() },
                onCancel = { asking = null },
            )

            Ask.DiscardSaved -> ConfirmDialog(
                title = stringResource(R.string.discard_title),
                body = stringResource(R.string.discard_body),
                confirm = stringResource(R.string.discard_confirm),
                cancel = stringResource(R.string.common_cancel),
                onConfirm = { asking = null; onDiscardSaved(); openTable() },
                onCancel = { asking = null },
            )

            is Ask.Kick -> (asking as Ask.Kick).let { ask ->
                ConfirmDialog(
                    title = stringResource(R.string.kick_title, ask.player.name),
                    body = stringResource(R.string.kick_body, chips(ask.player.stack)),
                    confirm = stringResource(R.string.kick_confirm),
                    cancel = stringResource(R.string.common_cancel),
                    onConfirm = {
                        asking = null
                        Session.act(HostCommandMessage(KickCommand(ask.player.id)))
                    },
                    onCancel = { asking = null },
                )
            }

            is Ask.HandSeat -> (asking as Ask.HandSeat).let { ask ->
                ConfirmDialog(
                    title = stringResource(R.string.seat_confirm_title),
                    body = stringResource(
                        R.string.seat_confirm_body,
                        ask.to.name,
                        chips((ask.from.seat ?: 0).toLong()),
                        chips(ask.from.stack),
                    ),
                    confirm = stringResource(R.string.seat_confirm),
                    cancel = stringResource(R.string.common_cancel),
                    onConfirm = {
                        asking = null
                        Session.act(HostCommandMessage(TransferSeatCommand(ask.from.id, ask.to.id)))
                        screen = Screen.HostPanel
                    },
                    onCancel = { asking = null },
                )
            }

            Ask.Leave -> ConfirmDialog(
                title = stringResource(R.string.leave_title),
                body = stringResource(R.string.leave_body),
                confirm = stringResource(R.string.action_leave),
                cancel = stringResource(R.string.common_cancel),
                onConfirm = {
                    asking = null
                    Session.leave()
                    screen = Screen.Home
                },
                onCancel = { asking = null },
            )

            null -> Unit
        }

        if (menuOpen) {
            TableMenu(
                hosting = hostStatus.running,
                isHost = state.isHost,
                onConnection = { menuOpen = false; screen = Screen.Connection },
                onHostPanel = { menuOpen = false; screen = Screen.HostPanel },
                onLog = { menuOpen = false; screen = Screen.Log },
                onRename = { menuOpen = false; screen = Screen.Rename },
                onLeave = {
                    menuOpen = false
                    asking = Ask.Leave
                },
                onClose = { menuOpen = false },
            )
        }
    }
}

/** Host correction, in either direction, through the same amount screen. */
private fun adjustRequest(
    player: Player,
    give: Boolean,
    title: String,
    subtitle: String,
    confirm: String,
    restLabel: String,
    initial: Long?,
    done: () -> Unit,
): AmountRequest = AmountRequest(
    title = title,
    subtitle = subtitle,
    confirm = confirm,
    initial = initial,
    max = if (give) null else player.stack,
    restLabel = restLabel,
    rest = { if (give) player.stack + it else player.stack - it },
    onConfirm = { amount ->
        Session.act(HostCommandMessage(AdjustStackCommand(player.id, if (give) amount else -amount)))
        done()
    },
)

@Composable
private fun TableMenu(
    hosting: Boolean,
    isHost: Boolean,
    onConnection: () -> Unit,
    onHostPanel: () -> Unit,
    onLog: () -> Unit,
    onRename: () -> Unit,
    onLeave: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Refugi.bg.copy(alpha = .72f))
            .padding(Refugi.side, 64.dp, Refugi.side, 0.dp),
    ) {
        Card(Modifier.fillMaxWidth(), padding = 8.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hosting) {
                    MenuItem(stringResource(R.string.menu_connection), onConnection)
                }
                if (isHost) MenuItem(stringResource(R.string.host_panel_title), onHostPanel)
                MenuItem(stringResource(R.string.log_title), onLog)
                MenuItem(stringResource(R.string.menu_rename), onRename)
                MenuItem(stringResource(R.string.action_leave), onLeave, Refugi.loss)
                MenuItem(stringResource(R.string.common_cancel), onClose, Refugi.text2)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit, colour: androidx.compose.ui.graphics.Color = Refugi.text) {
    io.github.victormico.tablechips.app.ui.ClickableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        fill = Refugi.surface,
        pressedFill = Refugi.surfaceHigh,
        border = null,
        padding = 12.dp,
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) {
        TcText(label, Type.secondary, color = colour)
    }
}
