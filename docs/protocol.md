# Protocol

JSON over one WebSocket at `/ws`. Every frame carries `v`, the protocol version,
and a `type` discriminator. The definitions live in `server/…/protocol/` and are
the single source of truth; this file is the summary a client author needs.

Current version: **1**.

## Shape of the conversation

1. The client opens the socket and sends `join` as its first frame.
2. The server answers with `state` — the whole table, every time.
3. The client sends `sit`, `action` or `hostCommand`; the server answers every
   accepted command with a new `state` **to every connected client**, and every
   refused one with an `error` **to the sender only**.

There are no deltas. With ten players a full state is a few kB, and sending it
whole removes an entire category of synchronization bugs.

The server validates everything against the authoritative state. The client
computes nothing that matters.

## Client to server

| type | fields | meaning |
|---|---|---|
| `join` | `name`, `playerId?`, `room?` | enter the table; the id is how a client gets its seat back after a reconnection |
| `sit` | `seat?`, `buyIn?` | take a seat (first free one, table default buy-in) |
| `leave` | — | leave the table and cash out |
| `action` | `action` | something a player does for themselves |
| `hostCommand` | `command` | something only the host may do |

`action` is one of `bet` (`amount`, `pot`), `rebuy` (`amount`), `transfer`
(`to`, `amount`), `rename` (`name`), `stand_up`, `stake` (`amount`, bank games),
`cancel_stake` (bank games), `take_bank` (bank games, only while nobody holds
it), `double` and `split` (`hand`, blackjack), `fold` (poker).

`sit` with no `seat` returns a player to the seat they last stood up from while
it is free, and with no `buyIn` a player who still has chips sits down with
them: coming back from the bar is not a buy-in.

`hostCommand` is one of `award_pot` (`to`, `pot`, `amount?` — null means the
whole pot), `share_pot` (`winners`, `pot` — a tie), `adjust_stack` (`player`, `delta`), `create_pot` (`name?`),
`set_config` (`config`), `kick` (`player`), `transfer_seat` (`from`, `to`),
`set_banker` (`player`, bank games), `settle` (`player`, `outcome`, `amount?`,
`hand?`, bank games), `settle_all` (`outcome`, bank games), `fund_house`
(`amount`, blackjack), `start_hand`, `close_round`, `split_pots` (poker),
`undo`. `settle`, `settle_all` and `fund_house` also come from whoever holds
the bank: the rules, not the frame, decide who may send them.

**No frame carries the id of who sent it.** The server takes that from the
connection, so a client cannot act on behalf of another player by editing a
frame.

## Server to client

| type | fields | meaning |
|---|---|---|
| `state` | `state`, `you`, `undoDepth` | the whole table; `you` tells a new client the id it was given |
| `error` | `code` | the last command was refused |
| `kicked` | `reason` | this connection no longer holds a place at the table |

`kicked.reason` is a code too: `kicked` (the host threw this player out),
`seat_transferred` (the seat went to another identity) or `table_closed` (the
whole table is over). The client stops reconnecting on any of them — rejoining
would only be refused again — and says which one it was, because silence looks
like a network drop, which is the one thing it is not.

`error.code` is a code, never a sentence: `insufficient_chips`, `not_host`,
`seat_taken`, `not_joined`, `unsupported_version`… The client renders it in the
reader's language and in the vocabulary of the game being played. The full list
is `RuleError` in `:core` plus `ProtocolError` in `:server`.

## Identity and reconnection

Each client keeps its `playerId` locally: preferences in the app, `localStorage`
in the browser. On reconnect it sends the same id in `join` and gets its seat,
its stack and its history back. A client with no stored id gets a fresh one and
learns it from the `you` field of the first `state`.

Connection state is **not** part of the ledger: `connected` on a player is
presence, it never lands on the undo stack, and undoing a move never changes who
is online. Losing a phone to a dead battery must not be undoable.

## Games

`config.mode` decides which moves mean anything: `manual`, `seven_half`,
`blackjack`, `poker`. The app deals no cards in any of them — what changes with
the mode is the shape of the money.

**Bank games** (`seven_half`, `blackjack`). One seated player holds the bank
(`state.banker`). Everybody else puts chips up with `stake`; those chips leave
the stack and sit in `player.stake`, on the table but nobody's yet, played as
`player.hands` (one, until a blackjack hand is split). Whoever holds the bank,
or the host, ends each hand with `settle`, whose `outcome` is `win`, `lose`,
`push`, `natural` or `surrender`: `natural` is paid at `config.naturalPays`
(3:2 for blackjack, usually 2:1 for set i mig), rounded down, and `surrender`
gives half the stake back. `hand` settles one hand of a split stake; `amount`
still settles part of a stake by hand. `settle_all` ends every stake still
waiting with `win`, `lose` or `push`. The bank must be able to cover what it
owes, or the command is refused with `bank_cannot_pay`.

At blackjack the bank is the house: `state.house`, chips bought in with
`fund_house` that sit nowhere and are nobody's. `state.banker` is whoever deals
for it, and their own stack never moves. At set i mig the bank is the banker's
stack, and with `config.naturalTakesBank` a `natural` hands the bank to that
player once every stake has been settled (`state.pendingBanker` until then).

Each player keeps `lastStake`, what they staked last time, to stake it again in
one tap, and `settled`, how their hands ended, until they stake again.

**Poker.** `start_hand` moves the button, posts `config.smallBlind` and
`config.bigBlind` (capped by the stacks behind them) and clears the last hand.
A bet also adds to `player.committed` and `player.roundBet`, so `state.currentBet`
minus a player's `roundBet` is what a call costs. `close_round` ends a street,
`fold` takes somebody out of the hand, and `split_pots` cuts the pot into the
pots that can actually be won: each `Pot` then carries `eligible`, and chips
nobody could call come back as a pot only their owner may be given. A tie is
`share_pot`: the pot split evenly between the winners, the chips that do not
divide going one each to the winners nearest the dealer's left. When
`award_pot` or `share_pot` takes the last chips out of the pots, the same
command deals the next hand — the button moves and the blinds go in — as long
as at least two seated players still have chips. Players sitting with no chips
are dealt out: the button and the blinds pass them by.

When a `fold` leaves a single player in the hand (not folded, and with chips
in front of them or behind), there is no showdown and nothing for the host to
judge: the same command gives that player every pot and deals the next hand.
It does not when the survivor could not win every pot, which only a split made
by hand can cause. An all-in player is still in the hand.

Whose turn it is is deliberately not modelled. At a real table that is settled
by the people sitting at it, and software that disagreed with them would only
get in the way.

## Persistence

The host writes the whole ledger to storage after every accepted command, under
the same lock that broadcasts it: a move the players have seen and a move on
disk are the same move. A table therefore survives the death of the process
hosting it — the phone is killed for memory, or runs out of battery — and comes
back by replaying the ledger, with its room code, its chips, its seats and its
log. The clients reconnect on their own and find their seats, because the id
they hold is the same id the ledger knows.

Closing the table is the one thing that throws the ledger away, and it says
goodbye first: every connection gets `kicked` with `table_closed` before the
server stops.

## Version

A frame whose `v` does not match the server's is refused with
`unsupported_version`. Guests always run the client the host serves them, so the
version can only drift for the installed app — bump `PROTOCOL_VERSION` whenever
an older app would misread a newer table.
