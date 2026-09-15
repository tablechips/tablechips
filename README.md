# TableChips

Chips for a card table, without the chips. One phone hosts, everyone else joins
over its hotspot — **with no internet, no account, and nothing to install for
the guests**.

*[Llegiu-ho en català](README.ca.md) · [Léelo en castellano](README.es.md)*

TableChips keeps the score of a real game played with a real deck: 7½,
blackjack, poker, and a manual mode for anything else. It does **not** deal
cards. That is a different product with a much larger trust problem.

## Why another one of these

The niche is crowded and every product in it is proprietary, English-only, and
capped at six players, and all of them make every guest install the app. Some
need a signalling server, which means they need internet.

- **Free software, on F-Droid.** No ads, no in-app purchases, no accounts, no
  telemetry.
- **Guests install nothing.** The host serves a web client; an iPhone and an
  Android at the same table both just open a browser.
- **Not only poker.** 7½ and blackjack as first-class modes, plus a generic mode
  that covers any game the app has never heard of.
- **Up to ten players**, each on their own device.
- **No internet, no small print.** It works in a mountain hut.
- **You can always type the amount.** Gestures, if any, are a shortcut and never
  the only way.
- **Unlimited undo** and a full activity log.
- **Built for a real room:** reconnection, recoverable seats, state that
  survives the process being killed.
- **Catalan, Spanish and English** from the first screen.

## How it works

```
        HOST PHONE                            GUESTS
  ┌───────────────────────────┐
  │ Hotspot on                │        ┌──────────────────┐
  │                           │◄──ws───┤ App (client)     │
  │ Foreground service        │        └──────────────────┘
  │  └ Ktor :8080             │
  │     ├ WS  /ws             │        ┌──────────────────┐
  │     ├ GET /  (web client) │◄──ws───┤ Browser          │
  │     └ GET /health         │        │ (Android or iOS) │
  │                           │        └──────────────────┘
  │ Authoritative state       │
  │ Host UI ──── localhost ───┤
  └───────────────────────────┘
```

One process on the host phone. Its own player interface talks to its own server
over localhost, exactly like a guest: one code path, and the host is not a
special case.

Everything is plain `http://` and `ws://`. A page served over http may open a
WebSocket to the same address with no certificate and no secure context — which
is the whole reason WebRTC was ruled out.

## Modules

| Module | What it is | Depends on Android? |
|---|---|---|
| `:core` | The chip ledger and the rules. Pure Kotlin. | no |
| `:server` | Protocol, transport, the embedded Ktor server, and the client the app plays through. | no |
| `:app` | Compose UI, foreground service. | yes |

The rules engine is deliberately free of Android so it can be tested with
`./gradlew :core:test` in a second, with no emulator anywhere.

The app and the web client share one visual identity, **Refugi** — warm smoke
black, dark wood, a single matt brass accent — specified in
[docs/design-refugi.md](docs/design-refugi.md). It is dark only, on purpose: a
light mode has not been designed and guessing one would break it. Neither side
uses a component library; both draw the same handful of shapes from
primitives, and both bundle their fonts, because a hotspot has no internet to
fetch one from.

The web client is a single file, `server/src/main/resources/web/index.html`: no
framework, no build step, and nothing loaded from outside the phone. It is
served from the classpath, so the same jar works on a desktop JVM and inside the
APK.

Its dictionary carries the table vocabulary **per mode**, not only globally,
because the same concept changes name with the game: what a casino calls the pot
is the "poso" at canari. A mode looks up its own word first and falls back to the
global key, so a new mode only has to name what it says differently.

## Building

Needs JDK 17 or newer and an Android SDK for `:app`.

```sh
./gradlew build                 # everything, tests included
./gradlew :core:test            # the rules, in a second, no emulator
./gradlew :server:test          # protocol, a real http/ws server, and the client
./gradlew :app:testDebugUnitTest # the app's screens, rendered on the JVM
./gradlew :app:assembleDebug
```

The app's screens are drawn and driven in unit tests through Robolectric, so a
screen that would crash on first composition fails the build instead. It is not
a substitute for a phone, but nothing else here can be pointed at the UI.

`:app` is only included when an Android SDK is visible (`ANDROID_HOME`,
`ANDROID_SDK_ROOT` or `sdk.dir` in `local.properties`). A checkout without one
still builds and tests `:core` and `:server`.

`compileSdk` is 36 because API 37 has no published platform yet; move both it
and `targetSdk` up when it ships.

## Where the project is

Phases, as laid out in the plan:

- [x] **F0 — Skeleton.** Three modules, licence, i18n in three languages.
- [x] **F1 — Connectivity.** Foreground service with Ktor, `/health`, WebSocket
      echo, every detected address on screen.
- [x] **F2 — Ledger and protocol.** The ledger in `:core` with JVM tests,
      authoritative state, `join`, `sit`, full-state broadcast.
- [x] **F3 — Web client.** Manual mode playable end to end, in three languages:
      betting with a typed amount, extra pots, the host awarding pots whole or
      in parts, host corrections, and undo.
- [x] **F4 — App client.** The app plays: the same protocol, the same screens,
      and the host connected to its own table over localhost like anybody else.
      Joining by typing an address; scanning a QR waits for F5, which is where
      the code that produces one lives.
- [ ] **F5 — Link bridge.** QR, `/join` page, intent URI, custom scheme.
- [ ] **F6 — Robustness.** Reconnection, seat recovery, persistence, kicking.
- [ ] **F7 — Modes.** Blackjack, then poker.
- [ ] **F8 — Release.** F-Droid metadata, reproducible builds.

The acceptance criteria that need real devices and real people are not ticked
off by a test suite:

- **F1** — with the hotspot on, a second phone opens `/health` and the echo
  socket works from a page served in the clear. The `web/` resource is packaged
  in the APK; that it is *served* from inside the APK is part of this check.
- **F2** — two browser tabs see the same table.
- **F3** — three people play a hand of 7½ with browsers only, at least one of
  them from an iPhone. Everything below that bar is covered by the JVM tests and
  by a scripted three-player hand in a real browser; the iPhone is not.
- **F4** — a game of app and browsers together, eight players or more. Eight app
  clients at one table is a JVM test, and the screens are rendered and driven on
  the JVM too; the mixed table of real phones is not.

## Decisions worth not reopening

- **No WebRTC, no PeerJS.** Signalling needs internet and WebRTC needs a secure
  context. Both kill the offline scenario.
- **No Bluetooth.** Web Bluetooth is not networking, Android's PAN is not
  reachable, and Nearby Connections or Wi-Fi Direct would force a native app on
  both sides.
- **No Termux.** The embedded server avoids native arm64 binaries, gets a
  supported lifecycle, and spares everyone a terminal.
- **No card dealing.** Play with a real deck.
- **No online mode.** It would turn a local tool into an operated service, with
  the availability, abuse and risk profile that implies.

The app id is `io.github.victormico.tablechips`. It is the one irreversible
decision in the project — everything else, the repository name included, can be
changed tomorrow.

## Licence

GPLv3. See [LICENSE](LICENSE). Third-party assets and their licences are listed
in [NOTICE](NOTICE); the fonts and their OFL texts live in `third_party/fonts/`. For a consumer app this is what stops the work
coming back as a proprietary fork, which is exactly what this corner of the
world has too much of already.
