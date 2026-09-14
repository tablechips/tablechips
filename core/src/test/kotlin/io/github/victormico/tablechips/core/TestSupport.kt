package io.github.victormico.tablechips.core

import kotlin.test.assertIs

/** A clock that never surprises a test. */
class FakeClock(private var now: Long = 1_000L) {
    fun tick(step: Long = 1_000L): Long {
        now += step
        return now
    }

    operator fun invoke(): Long = now
}

val ANNA = PlayerId("anna")
val BRU = PlayerId("bru")
val CARME = PlayerId("carme")

fun newTable(defaultBuyIn: Long = 100, seatCount: Int = MAX_SEATS): Table {
    val clock = FakeClock()
    return Table(
        roomCode = "TEST",
        config = TableConfig(defaultBuyIn = defaultBuyIn, seatCount = seatCount),
        clock = { clock.tick() },
    )
}

/** Runs a command that the test expects to be accepted, and returns the new state. */
fun Table.accept(command: TableCommand): TableState {
    val result = execute(command)
    assertIs<CommandResult.Accepted>(result, "expected $command to be accepted, got $result")
    return snapshot()
}

/** Runs a command that the test expects to be refused, and returns the reason. */
fun Table.reject(command: TableCommand): RuleError {
    val result = execute(command)
    assertIs<CommandResult.Rejected>(result, "expected $command to be refused, got $result")
    return result.error
}

/** A table with three players seated, each with the default buy-in. */
fun seatedTable(defaultBuyIn: Long = 100): Table = newTable(defaultBuyIn).apply {
    accept(JoinTable(ANNA, "Anna"))
    accept(JoinTable(BRU, "Bru"))
    accept(JoinTable(CARME, "Carme"))
    accept(SitDown(ANNA))
    accept(SitDown(BRU))
    accept(SitDown(CARME))
}
