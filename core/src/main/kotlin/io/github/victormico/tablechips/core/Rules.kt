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
        val buyIn = command.buyIn ?: state.config.defaultBuyIn
        if (buyIn < 0) return fail(RuleError.INVALID_AMOUNT)
        val seat = when (val requested = command.seat) {
            null -> state.freeSeats.firstOrNull() ?: return fail(RuleError.TABLE_FULL)
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
        return ok(PlayerStoodUp(player.id, at))
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
        val amount = command.amount ?: pot.amount
        if (amount <= 0) return fail(RuleError.INVALID_AMOUNT)
        if (amount > pot.amount) return fail(RuleError.POT_TOO_SMALL)
        return ok(PotAwarded(pot.id, winner.id, amount, at))
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

    private fun planSetConfig(state: TableState, command: SetConfig, at: Long): Plan {
        if (!isHost(state, command.actor)) return fail(RuleError.NOT_HOST)
        val config = command.config
        if (config.seatCount !in 1..MAX_SEATS) return fail(RuleError.INVALID_CONFIG)
        if (config.defaultBuyIn < 0) return fail(RuleError.INVALID_CONFIG)
        return ok(ConfigChanged(config, at))
    }

    private fun reduce(state: TableState, event: TableEvent): TableState = when (event) {
        is TableOpened -> state.copy(roomCode = event.roomCode, config = event.config)

        is ConfigChanged -> state.copy(
            config = event.config,
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

        is PlayerStoodUp -> state.mapPlayer(event.player) { it.copy(seat = null) }

        is PlayerLeft -> {
            // Whatever the player still had is cashed out, so the books close.
            val leaving = state.player(event.player)
            state.copy(
                players = state.players.filterNot { it.id == event.player },
                bank = state.bank.copy(cashedOut = state.bank.cashedOut + (leaving?.stack ?: 0)),
            )
        }

        is PlayerKicked -> {
            val kicked = state.player(event.player)
            state.copy(
                players = state.players.filterNot { it.id == event.player },
                bank = state.bank.copy(cashedOut = state.bank.cashedOut + (kicked?.stack ?: 0)),
            )
        }

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
                                    isHost = player.isHost || from.isHost,
                                )
                            }
                        },
                )
            }
        }

        is Rebought -> state.mapPlayer(event.player) {
            it.copy(stack = it.stack + event.amount, boughtIn = it.boughtIn + event.amount)
        }.let { it.copy(bank = it.bank.copy(boughtIn = it.bank.boughtIn + event.amount)) }

        is BetPlaced -> state
            .mapPlayer(event.player) { it.copy(stack = it.stack - event.amount) }
            .mapPot(event.pot) { it.copy(amount = it.amount + event.amount) }

        is PotAwarded -> state
            .mapPot(event.pot) { it.copy(amount = it.amount - event.amount) }
            .mapPlayer(event.player) { it.copy(stack = it.stack + event.amount) }

        is PotCreated -> state.copy(pots = state.pots + Pot(event.pot, 0, event.name))

        is ChipsTransferred -> state
            .mapPlayer(event.from) { it.copy(stack = it.stack - event.amount) }
            .mapPlayer(event.to) { it.copy(stack = it.stack + event.amount) }

        is StackAdjusted -> state
            .mapPlayer(event.player) { it.copy(stack = it.stack + event.delta) }
            .let { it.copy(bank = it.bank.copy(adjusted = it.bank.adjusted + event.delta)) }
    }

    private fun TableState.mapPlayer(id: PlayerId, block: (Player) -> Player): TableState =
        copy(players = players.map { if (it.id == id) block(it) else it })

    private fun TableState.mapPot(id: PotId, block: (Pot) -> Pot): TableState =
        copy(pots = pots.map { if (it.id == id) block(it) else it })
}
