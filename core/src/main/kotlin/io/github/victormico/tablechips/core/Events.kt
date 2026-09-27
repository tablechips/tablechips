package io.github.victormico.tablechips.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An entry of the chip ledger. Events are the only thing that ever changes the
 * accounting state; the state itself is the fold of every event applied so far,
 * which is what makes undo of any depth a truncation instead of a special case.
 *
 * Presence (who is currently connected) is deliberately not an event.
 */
@Serializable
sealed interface TableEvent {
    /** Host clock, epoch millis. */
    val at: Long

    /** Key used to render this entry in the activity log, in the reader's language. */
    val logKey: String

    /** Player the log line is about, if any. */
    val logActor: PlayerId? get() = null

    /** Arguments for the log line. Numbers as plain strings; the client formats them. */
    fun logArgs(): Map<String, String> = emptyMap()
}

@Serializable
@SerialName("table_opened")
data class TableOpened(
    val roomCode: String,
    val config: TableConfig,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.table_opened"
    override fun logArgs(): Map<String, String> = mapOf("room" to roomCode)
}

@Serializable
@SerialName("config_changed")
data class ConfigChanged(
    val config: TableConfig,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.config_changed"
    override fun logArgs(): Map<String, String> = mapOf("buyIn" to config.defaultBuyIn.toString())
}

@Serializable
@SerialName("player_joined")
data class PlayerJoined(
    val player: PlayerId,
    val name: String,
    val isHost: Boolean,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.player_joined"
    override val logActor: PlayerId get() = player
    override fun logArgs(): Map<String, String> = mapOf("name" to name)
}

@Serializable
@SerialName("player_renamed")
data class PlayerRenamed(
    val player: PlayerId,
    val name: String,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.player_renamed"
    override val logActor: PlayerId get() = player
    override fun logArgs(): Map<String, String> = mapOf("name" to name)
}

@Serializable
@SerialName("player_sat")
data class PlayerSat(
    val player: PlayerId,
    val seat: Int,
    val buyIn: Long,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.player_sat"
    override val logActor: PlayerId get() = player
    override fun logArgs(): Map<String, String> =
        mapOf("seat" to (seat + 1).toString(), "amount" to buyIn.toString())
}

@Serializable
@SerialName("player_stood_up")
data class PlayerStoodUp(
    val player: PlayerId,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.player_stood_up"
    override val logActor: PlayerId get() = player
}

@Serializable
@SerialName("player_left")
data class PlayerLeft(
    val player: PlayerId,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.player_left"
    override val logActor: PlayerId get() = player
}

/**
 * The host closing somebody's seat. Same accounting as leaving — the chips go
 * with them — but it is a different thing to read in the log, and it is
 * undoable like everything else.
 */
@Serializable
@SerialName("player_kicked")
data class PlayerKicked(
    val player: PlayerId,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.player_kicked"
    override val logActor: PlayerId get() = player
}

/**
 * A seat handed from one identity to another: the way back for somebody whose
 * phone forgot who it was. The chips do not move, the name over them does.
 */
@Serializable
@SerialName("seat_transferred")
data class SeatTransferred(
    val from: PlayerId,
    val to: PlayerId,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.seat_transferred"
    override val logActor: PlayerId get() = to
}

@Serializable
@SerialName("rebuy")
data class Rebought(
    val player: PlayerId,
    val amount: Long,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.rebuy"
    override val logActor: PlayerId get() = player
    override fun logArgs(): Map<String, String> = mapOf("amount" to amount.toString())
}

@Serializable
@SerialName("bet")
data class BetPlaced(
    val player: PlayerId,
    val amount: Long,
    val pot: PotId,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.bet"
    override val logActor: PlayerId get() = player
    override fun logArgs(): Map<String, String> =
        mapOf("amount" to amount.toString(), "pot" to pot.value)
}

@Serializable
@SerialName("pot_awarded")
data class PotAwarded(
    val pot: PotId,
    val player: PlayerId,
    val amount: Long,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.pot_awarded"
    override val logActor: PlayerId get() = player
    override fun logArgs(): Map<String, String> =
        mapOf("amount" to amount.toString(), "pot" to pot.value)
}

@Serializable
@SerialName("pot_created")
data class PotCreated(
    val pot: PotId,
    val name: String?,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.pot_created"
    override fun logArgs(): Map<String, String> = mapOf("pot" to (name ?: pot.value))
}

@Serializable
@SerialName("transfer")
data class ChipsTransferred(
    val from: PlayerId,
    val to: PlayerId,
    val amount: Long,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = "log.transfer"
    override val logActor: PlayerId get() = from
    override fun logArgs(): Map<String, String> = mapOf("amount" to amount.toString())
}

/** Host correction. The only way chips appear or vanish without a buy-in. */
@Serializable
@SerialName("stack_adjusted")
data class StackAdjusted(
    val player: PlayerId,
    val delta: Long,
    override val at: Long,
) : TableEvent {
    override val logKey: String get() = if (delta >= 0) "log.stack_added" else "log.stack_removed"
    override val logActor: PlayerId get() = player
    override fun logArgs(): Map<String, String> =
        mapOf("amount" to (if (delta < 0) -delta else delta).toString())
}
