package io.github.victormico.tablechips.core

/** How many log lines travel with the state. The full history stays in the ledger. */
const val LOG_WINDOW: Int = 100

/**
 * The rules engine: pure functions over state. No clock, no randomness, no I/O,
 * so every rule is testable with `./gradlew :core:test`.
 */
object Rules {

    /** Applies one ledger entry. Total: an event that reached the ledger is always valid. */
    fun apply(state: TableState, event: TableEvent): TableState {
        val next = reduce(state, event)
        val entry = LogEntry(
            seq = state.rev + 1,
            at = event.at,
            key = event.logKey,
            actor = event.logActor,
            // Before the event, so somebody thrown out still has a name here,
            // and a rename reads as the old name becoming the new one.
            actorName = event.logActor?.let { id ->
                (state.player(id) ?: next.player(id))?.name
            },
            args = event.logArgs(),
        )
        return next.copy(
            rev = state.rev + 1,
            log = (next.log + entry).takeLast(LOG_WINDOW),
        )
    }

    /** Folds a whole ledger. This is what undo uses, and what a restart uses. */
    fun fold(events: List<TableEvent>, roomCode: String = ""): TableState =
        events.fold(TableState(roomCode = roomCode), ::apply)

    /**
     * Validates a command against the state and turns it into ledger entries.
     * An accepted command may produce no events at all (a rejoin that changes
     * nothing), which must not move the revision.
     */
    fun plan(state: TableState, command: TableCommand, at: Long): CommandResult {
        val events = when (command) {
            is JoinTable -> planJoin(state, command, at)
            is Rename -> planRename(state, command, at)
            is SitDown -> planSit(state, command, at)
            is StandUp -> planStandUp(state, command, at)
            is LeaveTable -> planLeave(state, command, at)
            is PlaceBet -> planBet(state, command, at)
            is Rebuy -> planRebuy(state, command, at)
            is TransferChips -> planTransfer(state, command, at)
            is AwardPot -> planAward(state, command, at)
            is CreatePot -> planCreatePot(state, command, at)
            is AdjustStack -> planAdjust(state, command, at)
            is SetConfig -> planSetConfig(state, command, at)
            is KickPlayer -> planKick(state, command, at)
            is TransferSeat -> planTransferSeat(state, command, at)
            is SetBanker -> planSetBanker(state, command, at)
            is PlaceStake -> planStake(state, command, at)
            is CancelStake -> planCancelStake(state, command, at)
            is SettleHand -> planSettle(state, command, at)
            is StartHand -> planStartHand(state, command, at)
            is Fold -> planFold(state, command, at)
            is CloseRound -> planCloseRound(state, command, at)
            is SplitPots -> planSplitPots(state, command, at)
            // Undo is not an event: it removes them. See Table.undo.
            is UndoLast -> return if (!isHost(state, command.actor)) {
                CommandResult.Rejected(RuleError.NOT_HOST)
            } else {
                CommandResult.Rejected(RuleError.NOTHING_TO_UNDO)
            }
        }
        return when (events) {
            is Plan.Fail -> CommandResult.Rejected(events.error)
            is Plan.Ok -> CommandResult.Accepted(events.events, events.events.fold(state, ::apply))
        }
    }

    private sealed interface Plan {
        data class Ok(val events: List<TableEvent>) : Plan

        data class Fail(val error: RuleError) : Plan
    }

    private fun ok(vararg events: TableEvent): Plan = Plan.Ok(events.toList())

    private fun fail(error: RuleError): Plan = Plan.Fail(error)

    fun isHost(state: TableState, actor: PlayerId): Boolean =
        state.player(actor)?.isHost == true

    private fun checkName(name: String): RuleError? = when {
        name.isBlank() -> RuleError.NAME_REQUIRED
        name.trim().length > MAX_NAME_LENGTH -> RuleError.NAME_TOO_LONG
        else -> null
    }

    private fun planJoin(state: TableState, command: JoinTable, at: Long): Plan {
        checkName(command.name)?.let { return fail(it) }
        val name = command.name.trim()
        val existing = state.player(command.actor)
        return when {
            existing == null -> ok(
                PlayerJoined(
                    player = command.actor,
                    name = name,
                    // The first player through the door runs the table.
                    isHost = state.players.none { it.isHost },
                    at = at,
                ),
            )
            existing.name != name -> ok(PlayerRenamed(command.actor, name, at))
            // Reconnecting with the same name changes nothing in the ledger.
            else -> Plan.Ok(emptyList())
        }
    }

    private fun planRename(state: TableState, command: Rename, at: Long): Plan {
        checkName(command.name)?.let { return fail(it) }
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        val name = command.name.trim()
        return if (player.name == name) Plan.Ok(emptyList()) else ok(PlayerRenamed(player.id, name, at))
    }

    private fun planSit(state: TableState, command: SitDown, at: Long): Plan {
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        if (player.seated) return fail(RuleError.ALREADY_SEATED)
        // Somebody coming back with chips sits down with them. Buying in again
        // is what "buy more" is for, and it is never a side effect of sitting.
        val buyIn = command.buyIn ?: if (player.stack > 0) 0 else state.config.defaultBuyIn
        if (buyIn < 0) return fail(RuleError.INVALID_AMOUNT)
        val seat = when (val requested = command.seat) {
            null -> player.lastSeat?.takeIf { it in state.freeSeats }
                ?: state.freeSeats.firstOrNull()
                ?: return fail(RuleError.TABLE_FULL)
            else -> {
                if (requested !in 0 until state.config.seatCount) return fail(RuleError.SEAT_OUT_OF_RANGE)
                if (state.playerAtSeat(requested) != null) return fail(RuleError.SEAT_TAKEN)
                requested
            }
        }
        return ok(PlayerSat(player.id, seat, buyIn, at))
    }

    private fun planStandUp(state: TableState, command: StandUp, at: Long): Plan {
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        if (!player.seated) return fail(RuleError.NOT_SEATED)
        return Plan.Ok(
            buildList {
                // Nobody walks away from the table holding chips that are still
                // up for a hand: they go back to the stack on the way out.
                if (player.stake > 0) add(StakeReturned(player.id, player.stake, at))
                // In poker what is already in the pot stays there, which is
                // what folding means; leaving the seat is not a free take-back.
                if (state.config.mode == GameMode.POKER && !player.folded && player.committed > 0) {
                    add(PlayerFolded(player.id, at))
                }
                add(PlayerStoodUp(player.id, at))
            },
        )
    }

    private fun planLeave(state: TableState, command: LeaveTable, at: Long): Plan {
        state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        return ok(PlayerLeft(command.actor, at))
    }

    private fun planBet(state: TableState, command: PlaceBet, at: Long): Plan {
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        if (!player.seated) return fail(RuleError.NOT_SEATED)
        if (command.amount <= 0) return fail(RuleError.INVALID_AMOUNT)
        if (command.amount > player.stack) return fail(RuleError.INSUFFICIENT_CHIPS)
        state.pot(command.pot) ?: return fail(RuleError.UNKNOWN_POT)
        // At poker a bet at least matches what is owed — calling it or raising
        // over it — unless it is everything the player has: an all-in for
        // less is the one short bet the game allows.
        if (state.config.mode == GameMode.POKER) {
            val owed = state.toCall(player.id)
            if (command.amount < owed && command.amount < player.stack) return fail(RuleError.BELOW_CALL)
        }
        return ok(BetPlaced(player.id, command.amount, command.pot, at))
    }

    private fun planRebuy(state: TableState, command: Rebuy, at: Long): Plan {
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        if (!player.seated) return fail(RuleError.NOT_SEATED)
        if (command.amount <= 0) return fail(RuleError.INVALID_AMOUNT)
        return ok(Rebought(player.id, command.amount, at))
    }

    private fun planTransfer(state: TableState, command: TransferChips, at: Long): Plan {
        val from = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        val to = state.player(command.to) ?: return fail(RuleError.INVALID_TARGET)
        if (from.id == to.id) return fail(RuleError.INVALID_TARGET)
        if (command.amount <= 0) return fail(RuleError.INVALID_AMOUNT)
        if (command.amount > from.stack) return fail(RuleError.INSUFFICIENT_CHIPS)
        return ok(ChipsTransferred(from.id, to.id, command.amount, at))
    }

    private fun planAward(state: TableState, command: AwardPot, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        val pot = state.pot(command.pot) ?: return fail(RuleError.UNKNOWN_POT)
        val winner = state.player(command.to) ?: return fail(RuleError.INVALID_TARGET)
        // A side pot is only winnable by those who paid into it. The clients
        // hide the others, but the rule lives here: the whole point of the
        // split is that an all-in for less cannot be handed more than its share.
        // A host who disagrees with the split undoes it and awards by hand.
        pot.eligible?.let { if (winner.id !in it) return fail(RuleError.INVALID_TARGET) }
        val amount = command.amount ?: pot.amount
        if (amount <= 0) return fail(RuleError.INVALID_AMOUNT)
        if (amount > pot.amount) return fail(RuleError.POT_TOO_SMALL)
        val award = PotAwarded(pot.id, winner.id, amount, at)
        // At poker, the last chips leaving the pot is the end of the hand, and a
        // real table deals the next one: the button moves and the blinds go in,
        // in the same move. A pot split between winners, or a side pot still to
        // give, keeps the hand open until the last of it has gone.
        if (state.config.mode != GameMode.POKER) return ok(award)
        val after = apply(state, award)
        if (after.pots.any { it.amount > 0 }) return ok(award)
        val next = nextHand(after, at) ?: return ok(award)
        return ok(award, next)
    }

    private fun planCreatePot(state: TableState, command: CreatePot, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        command.name?.let { if (it.trim().length > MAX_NAME_LENGTH) return fail(RuleError.NAME_TOO_LONG) }
        val id = PotId("pot-" + (state.pots.size + 1))
        return ok(PotCreated(id, command.name?.trim()?.ifBlank { null }, at))
    }

    private fun planAdjust(state: TableState, command: AdjustStack, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        val player = state.player(command.player) ?: return fail(RuleError.INVALID_TARGET)
        if (command.delta == 0L) return fail(RuleError.INVALID_AMOUNT)
        if (player.stack + command.delta < 0) return fail(RuleError.INSUFFICIENT_CHIPS)
        return ok(StackAdjusted(player.id, command.delta, at))
    }

    private fun planKick(state: TableState, command: KickPlayer, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        state.player(command.player) ?: return fail(RuleError.INVALID_TARGET)
        // A host who throws themselves out leaves the table with no host at all.
        if (command.player == command.actor) return fail(RuleError.NOT_YOURSELF)
        return ok(PlayerKicked(command.player, at))
    }

    private fun planTransferSeat(state: TableState, command: TransferSeat, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        val from = state.player(command.from) ?: return fail(RuleError.INVALID_TARGET)
        val to = state.player(command.to) ?: return fail(RuleError.INVALID_TARGET)
        if (from.id == to.id) return fail(RuleError.NOT_YOURSELF)
        if (!from.seated) return fail(RuleError.NOT_SEATED)
        // The one taking it over must have nothing of their own to lose.
        if (to.seated) return fail(RuleError.SEAT_NOT_FREE)
        return ok(SeatTransferred(from.id, to.id, at))
    }

    // ----------------------------------------------------------- bank games

    private fun planSetBanker(state: TableState, command: SetBanker, at: Long): Plan {
        if (!state.config.mode.isBankGame) return fail(RuleError.WRONG_MODE)
        // Anybody seated may take a bank nobody holds: at a real table that is
        // just saying "I'll bank". Taking it from somebody, or handing it to
        // somebody else, is the host's call.
        val claimingFreeBank = command.player == command.actor && state.banker == null
        if (!claimingFreeBank && !isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        val player = command.player
        if (player == null) return ok(BankerChanged(null, at))
        val taking = state.player(player) ?: return fail(RuleError.INVALID_TARGET)
        if (!taking.seated) return fail(RuleError.NOT_SEATED)
        if (state.banker == taking.id) return Plan.Ok(emptyList())
        // The bank plays against the others, so it cannot be one of them.
        return if (taking.stake > 0) {
            Plan.Ok(listOf(StakeReturned(taking.id, taking.stake, at), BankerChanged(taking.id, at)))
        } else {
            ok(BankerChanged(taking.id, at))
        }
    }

    private fun planStake(state: TableState, command: PlaceStake, at: Long): Plan {
        if (!state.config.mode.isBankGame) return fail(RuleError.WRONG_MODE)
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        if (!player.seated) return fail(RuleError.NOT_SEATED)
        val banker = state.bankerPlayer ?: return fail(RuleError.NO_BANKER)
        if (banker.id == player.id) return fail(RuleError.BANKER_CANNOT_BET)
        if (command.amount <= 0) return fail(RuleError.INVALID_AMOUNT)
        if (command.amount > player.stack) return fail(RuleError.INSUFFICIENT_CHIPS)
        return ok(StakePlaced(player.id, command.amount, at))
    }

    private fun planCancelStake(state: TableState, command: CancelStake, at: Long): Plan {
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        if (player.stake <= 0) return fail(RuleError.NO_STAKE)
        return ok(StakeReturned(player.id, player.stake, at))
    }

    private fun planSettle(state: TableState, command: SettleHand, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        if (!state.config.mode.isBankGame) return fail(RuleError.WRONG_MODE)
        val banker = state.bankerPlayer ?: return fail(RuleError.NO_BANKER)
        val player = state.player(command.player) ?: return fail(RuleError.INVALID_TARGET)
        if (player.id == banker.id) return fail(RuleError.BANKER_CANNOT_BET)
        if (player.stake <= 0) return fail(RuleError.NO_STAKE)
        val stake = command.amount ?: player.stake
        if (stake <= 0) return fail(RuleError.INVALID_AMOUNT)
        if (stake > player.stake) return fail(RuleError.INSUFFICIENT_CHIPS)
        val delta = when (command.outcome) {
            HandOutcome.LOSE -> -stake
            HandOutcome.PUSH -> 0
            HandOutcome.WIN -> stake
            HandOutcome.NATURAL -> state.config.naturalPays.of(stake)
        }
        // A bank that cannot cover what it owes is not a bank: buy in first.
        if (delta > banker.stack) return fail(RuleError.INSUFFICIENT_CHIPS)
        return ok(HandSettled(player.id, banker.id, command.outcome, stake, delta, at))
    }

    // ---------------------------------------------------------------- poker

    private fun planStartHand(state: TableState, command: StartHand, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        if (state.config.mode != GameMode.POKER) return fail(RuleError.WRONG_MODE)
        val order = seatOrder(state)
        if (order.size < 2) return fail(RuleError.NOT_ENOUGH_PLAYERS)
        val button = nextSeat(order, state.button)
        return ok(HandStarted(button, blindsFor(state, order, button), at))
    }

    /**
     * The hand that follows an awarded pot, or null when there is nobody to
     * play it: with fewer than two players holding chips the game is over, or
     * waiting for somebody to buy back in, and dealing would only post blinds
     * into an empty hand.
     */
    private fun nextHand(state: TableState, at: Long): TableEvent? {
        val withChips = state.players.count { it.seated && it.stack > 0 }
        if (withChips < 2) return null
        val order = seatOrder(state)
        val button = nextSeat(order, state.button)
        return HandStarted(button, blindsFor(state, order, button), at)
    }

    /** Seats with somebody in them, in table order. */
    private fun seatOrder(state: TableState): List<Int> =
        state.players.mapNotNull { it.seat }.sorted()

    private fun nextSeat(order: List<Int>, after: Int?): Int =
        order.firstOrNull { after == null || it > after } ?: order.first()

    /**
     * Small blind to the left of the button, big blind after it — except
     * heads-up, where the button posts the small blind, as at any real table.
     */
    private fun blindsFor(state: TableState, order: List<Int>, button: Int): List<Blind> {
        val small = state.config.smallBlind
        val big = state.config.bigBlind
        if (small <= 0 && big <= 0) return emptyList()
        val smallSeat = if (order.size == 2) button else nextSeat(order, button)
        val bigSeat = nextSeat(order, smallSeat)
        return listOfNotNull(
            blind(state, smallSeat, small),
            blind(state, bigSeat, big),
        )
    }

    /** A blind is capped by the stack behind it: that is what being all-in is. */
    private fun blind(state: TableState, seat: Int, amount: Long): Blind? {
        if (amount <= 0) return null
        val player = state.playerAtSeat(seat) ?: return null
        val posted = minOf(amount, player.stack)
        return if (posted <= 0) null else Blind(player.id, posted)
    }

    private fun planFold(state: TableState, command: Fold, at: Long): Plan {
        if (state.config.mode != GameMode.POKER) return fail(RuleError.WRONG_MODE)
        val player = state.player(command.actor) ?: return fail(RuleError.UNKNOWN_PLAYER)
        if (!player.seated) return fail(RuleError.NOT_SEATED)
        if (player.folded) return Plan.Ok(emptyList())
        return ok(PlayerFolded(player.id, at))
    }

    private fun planCloseRound(state: TableState, command: CloseRound, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        if (state.config.mode != GameMode.POKER) return fail(RuleError.WRONG_MODE)
        if (state.currentBet == 0L && state.players.none { it.roundBet > 0 }) {
            return Plan.Ok(emptyList())
        }
        return ok(RoundClosed(at))
    }

    private fun planSplitPots(state: TableState, command: SplitPots, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        if (state.config.mode != GameMode.POKER) return fail(RuleError.WRONG_MODE)
        val pots = sidePots(state) ?: return fail(RuleError.NOTHING_TO_SPLIT)
        return ok(PotsSplit(pots, at))
    }

    /**
     * The pot cut into the pots that can actually be won.
     *
     * Every player matched what they could; a player who is all-in for less can
     * only win the layer they paid into. Chips nobody could call go back to the
     * one who put them in, as a pot only they may be given.
     *
     * Null when there is nothing to cut: no all-in, or a pot that no longer
     * matches what was put into it because the host has been moving chips by
     * hand. Splitting then would invent chips, so the host keeps doing it by
     * hand.
     */
    fun sidePots(state: TableState): List<Pot>? {
        val committed = state.players.filter { it.committed > 0 }
        if (committed.isEmpty()) return null
        val total = committed.sumOf { it.committed }
        val single = state.pots.singleOrNull() ?: return null
        if (single.amount != total) return null
        val live = committed.filter { !it.folded }
        if (live.isEmpty()) return null
        val levels = live.map { it.committed }.distinct().sorted()
        val top = levels.last()
        if (levels.size < 2 && committed.none { it.committed > top }) return null

        val pots = mutableListOf<Pot>()
        var floor = 0L
        levels.forEach { level ->
            val slice = committed.sumOf {
                minOf(it.committed, level) - minOf(it.committed, floor)
            }
            if (slice > 0) {
                pots += Pot(
                    id = if (pots.isEmpty()) MAIN_POT else PotId("side-" + pots.size),
                    amount = slice,
                    eligible = live.filter { it.committed >= level }.map { it.id },
                )
            }
            floor = level
        }
        // Whatever was bet above what anybody could call is not in play: it goes
        // back to whoever put it there, as a pot only they can be given.
        committed.filter { it.committed > top }.forEach { player ->
            pots += Pot(
                id = PotId("side-" + pots.size),
                amount = player.committed - top,
                eligible = listOf(player.id),
            )
        }
        return pots.takeIf { it.size > 1 }
    }

    private fun planSetConfig(state: TableState, command: SetConfig, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        val config = command.config
        if (config.seatCount !in 1..MAX_SEATS) return fail(RuleError.INVALID_CONFIG)
        if (config.defaultBuyIn < 0) return fail(RuleError.INVALID_CONFIG)
        if (!config.naturalPays.valid) return fail(RuleError.INVALID_CONFIG)
        if (config.smallBlind < 0 || config.bigBlind < 0) return fail(RuleError.INVALID_CONFIG)
        if (config.smallBlind > config.bigBlind) return fail(RuleError.INVALID_CONFIG)
        // Changing the game under chips that are up for a hand would strand
        // them: settle what is on the table first.
        if (config.mode != state.config.mode && state.players.any { it.stake > 0 }) {
            return fail(RuleError.INVALID_CONFIG)
        }
        return ok(ConfigChanged(config, at))
    }

    private fun reduce(state: TableState, event: TableEvent): TableState = when (event) {
        is TableOpened -> state.copy(roomCode = event.roomCode, config = event.config)

        is ConfigChanged -> state.copy(
            config = event.config,
            // A game with no bank has no banker.
            banker = state.banker.takeIf { event.config.mode.isBankGame },
            // Shrinking the table empties the seats that no longer exist.
            players = state.players.map { player ->
                if (player.seat != null && player.seat >= event.config.seatCount) {
                    player.copy(seat = null)
                } else {
                    player
                }
            },
        )

        is PlayerJoined -> state.copy(
            players = state.players + Player(
                id = event.player,
                name = event.name,
                isHost = event.isHost,
            ),
        )

        is PlayerRenamed -> state.mapPlayer(event.player) { it.copy(name = event.name) }

        is PlayerSat -> state.mapPlayer(event.player) {
            it.copy(
                seat = event.seat,
                stack = it.stack + event.buyIn,
                boughtIn = it.boughtIn + event.buyIn,
            )
        }.let { it.copy(bank = it.bank.copy(boughtIn = it.bank.boughtIn + event.buyIn)) }

        is PlayerStoodUp -> state
            .mapPlayer(event.player) { it.copy(seat = null, lastSeat = it.seat) }
            // The bank belongs to a seat at the table, not to a spectator.
            .copy(banker = state.banker.takeIf { it != event.player })

        // Whatever the player still had — stack and anything still up for a
        // hand — is cashed out, so the books close. What they had already put
        // in the pot stays in the pot: that is not theirs any more.
        is PlayerLeft -> state.removePlayer(event.player)

        is PlayerKicked -> state.removePlayer(event.player)

        is SeatTransferred -> {
            val from = state.player(event.from)
            if (from == null) {
                state
            } else {
                state.copy(
                    players = state.players
                        .filterNot { it.id == event.from }
                        .map { player ->
                            if (player.id != event.to) {
                                player
                            } else {
                                // Everything about the seat moves across: the
                                // chips, what was bought in for them, and the
                                // place at the table. Only the name changes.
                                player.copy(
                                    seat = from.seat,
                                    stack = from.stack,
                                    boughtIn = from.boughtIn,
                                    stake = from.stake,
                                    committed = from.committed,
                                    roundBet = from.roundBet,
                                    folded = from.folded,
                                    isHost = player.isHost || from.isHost,
                                )
                            }
                        },
                    banker = if (state.banker == event.from) event.to else state.banker,
                )
            }
        }

        is Rebought -> state.mapPlayer(event.player) {
            it.copy(stack = it.stack + event.amount, boughtIn = it.boughtIn + event.amount)
        }.let { it.copy(bank = it.bank.copy(boughtIn = it.bank.boughtIn + event.amount)) }

        is BetPlaced -> state
            .mapPlayer(event.player) { it.copy(stack = it.stack - event.amount) }
            .mapPot(event.pot) { it.copy(amount = it.amount + event.amount) }
            .let { next ->
                if (next.config.mode != GameMode.POKER) return@let next
                // In poker a bet is also a contribution to this hand, which is
                // what the side pots and the next call are computed from.
                val contributed = next.mapPlayer(event.player) {
                    it.copy(committed = it.committed + event.amount, roundBet = it.roundBet + event.amount)
                }
                contributed.copy(
                    currentBet = maxOf(
                        contributed.currentBet,
                        contributed.player(event.player)?.roundBet ?: 0,
                    ),
                )
            }

        is PotAwarded -> state
            .mapPot(event.pot) { it.copy(amount = it.amount - event.amount) }
            .mapPlayer(event.player) { it.copy(stack = it.stack + event.amount) }

        is PotCreated -> state.copy(pots = state.pots + Pot(event.pot, 0, event.name))

        is ChipsTransferred -> state
            .mapPlayer(event.from) { it.copy(stack = it.stack - event.amount) }
            .mapPlayer(event.to) { it.copy(stack = it.stack + event.amount) }

        is BankerChanged -> state.copy(banker = event.player)

        is StakePlaced -> state.mapPlayer(event.player) {
            it.copy(stack = it.stack - event.amount, stake = it.stake + event.amount)
        }

        is StakeReturned -> state.mapPlayer(event.player) {
            it.copy(stack = it.stack + event.amount, stake = it.stake - event.amount)
        }

        // The stake leaves the pile it was in, the player gets back whatever the
        // outcome says, and the difference is the bank's — in either direction.
        is HandSettled -> state
            .mapPlayer(event.player) {
                it.copy(
                    stake = it.stake - event.stake,
                    stack = it.stack + event.stake + event.delta,
                )
            }
            .mapPlayer(event.banker) { it.copy(stack = it.stack - event.delta) }

        is HandStarted -> {
            val cleared = state.copy(
                button = event.button,
                currentBet = 0,
                // Anything the last hand left lying in a pot rolls into this
                // one, exactly as it would on a table: chips are never dropped.
                pots = listOf(Pot(MAIN_POT, state.pots.sumOf { it.amount })),
                players = state.players.map {
                    it.copy(committed = 0, roundBet = 0, folded = false)
                },
            )
            event.blinds.fold(cleared) { state, blind ->
                state
                    .mapPlayer(blind.player) {
                        it.copy(
                            stack = it.stack - blind.amount,
                            committed = blind.amount,
                            roundBet = blind.amount,
                        )
                    }
                    .mapPot(MAIN_POT) { it.copy(amount = it.amount + blind.amount) }
                    .let { it.copy(currentBet = maxOf(it.currentBet, blind.amount)) }
            }
        }

        is PlayerFolded -> state.mapPlayer(event.player) { it.copy(folded = true) }

        is RoundClosed -> state.copy(
            currentBet = 0,
            players = state.players.map { it.copy(roundBet = 0) },
        )

        is PotsSplit -> state.copy(pots = event.pots)

        is StackAdjusted -> state
            .mapPlayer(event.player) { it.copy(stack = it.stack + event.delta) }
            .let { it.copy(bank = it.bank.copy(adjusted = it.bank.adjusted + event.delta)) }
    }

    /** Takes somebody off the table and cashes out everything that was theirs. */
    private fun TableState.removePlayer(id: PlayerId): TableState {
        val leaving = player(id)
        return copy(
            players = players.filterNot { it.id == id },
            banker = banker.takeIf { it != id },
            bank = bank.copy(cashedOut = bank.cashedOut + (leaving?.stack ?: 0) + (leaving?.stake ?: 0)),
        )
    }

    private fun TableState.mapPlayer(id: PlayerId, block: (Player) -> Player): TableState =
        copy(players = players.map { if (it.id == id) block(it) else it })

    private fun TableState.mapPot(id: PotId, block: (Pot) -> Pot): TableState =
        copy(pots = pots.map { if (it.id == id) block(it) else it })
}
