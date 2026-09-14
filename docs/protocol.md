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
(`to`, `amount`), `rename` (`name`), `stand_up`.

`hostCommand` is one of `award_pot` (`to`, `pot`, `amount?` — null means the
whole pot), `adjust_stack` (`player`, `delta`), `create_pot` (`name?`),
`set_config` (`config`), `undo`.

**No frame carries the id of who sent it.** The server takes that from the
connection, so a client cannot act on behalf of another player by editing a
frame.

## Server to client

| type | fields | meaning |
|---|---|---|
| `state` | `state`, `you`, `undoDepth` | the whole table; `you` tells a new client the id it was given |
| `error` | `code` | the last command was refused |
| `kicked` | `reason` | the server is about to drop this connection (from F6) |

`error.code` is a code, never a sentence: `insufficient_chips`, `not_host`,
`seat_taken`, `not_joined`, `unsupported_version`… The client renders it in the
reader's language and in the vocabulary of the game being played. The full list
is `RuleError` in `:core` plus `ProtocolError` in `:server`.

## Identity and reconnection

Each client keeps its `playerId` locally: DataStore in the app, `localStorage`
in the browser. On reconnect it sends the same id in `join` and gets its seat,
its stack and its history back. A client with no stored id gets a fresh one and
learns it from the `you` field of the first `state`.

Connection state is **not** part of the ledger: `connected` on a player is
presence, it never lands on the undo stack, and undoing a move never changes who
is online. Losing a phone to a dead battery must not be undoable.

## Version

A frame whose `v` does not match the server's is refused with
`unsupported_version`. Guests always run the client the host serves them, so the
version can only drift for the installed app — bump `PROTOCOL_VERSION` whenever
an older app would misread a newer table.
