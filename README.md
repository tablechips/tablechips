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

## Releasing

Releases are cut by [release-please](https://github.com/googleapis/release-please),
the same way as in [s7-opcua-bridge](https://github.com/victormico/s7-opcua-bridge).
Nobody edits a version number by hand.

- **The version lives in `version.txt`**, and nowhere else. The app's
  `versionName` is read from it and its `versionCode` derived from it:
  `MAJOR·10000 + MINOR·100 + PATCH`, so 0.1.0 is 100 and 1.2.3 is 10203.
- **Commit messages decide the next version**, in
  [Conventional Commits](https://www.conventionalcommits.org/) form:
  `fix:` is a patch, `feat:` a minor, and `feat!:` or a `BREAKING CHANGE:`
  footer a major — a minor while the app is below 1.0. `docs:`, `build:`,
  `ci:`, `test:`, `refactor:` and `chore:` release nothing on their own. A
  scope is welcome: `feat(web): …`, `fix(core): …`. The text after the colon
  keeps the house style; it is the line that ends up in the changelog.
- **Every push to `main` updates a release PR** with the next version and its
  `CHANGELOG.md` entry. Merging it tags `vX.Y.Z`, creates the GitHub release,
  and builds the signed APK and attaches it, with its SHA-256 and the signing
  certificate's fingerprint in the notes.
- **A release whose APK step failed** can have it built again without a new
  version: *Actions → Release → Run workflow*, with the release's tag
  (`v0.1.0`). It replaces the APK, its checksum and the Install section of the
  notes.
- **Every pull request runs `./gradlew build`**, tests and an unsigned release
  APK included, so the release PR is never where a broken build turns up.

The signing key is the second irreversible decision after the app id: Android
only installs an update signed with the same key as the version already on the
phone. It lives in four repository secrets, never in the repository:

| Secret | What |
|---|---|
| `TABLECHIPS_KEYSTORE_BASE64` | The keystore, as `base64 -w0 release.jks` |
| `TABLECHIPS_KEYSTORE_PASSWORD` | The keystore's password |
| `TABLECHIPS_KEY_ALIAS` | The key's alias |
| `TABLECHIPS_KEY_PASSWORD` | The key's password |

To make one, once, and keep a copy somewhere safe outside GitHub:

```sh
keytool -genkeypair -keystore release.jks -alias tablechips \
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=TableChips"
```

Without those secrets a release build is simply unsigned — which is what
F-Droid wants, since it signs with its own key — and the release workflow
stops with an error instead of publishing an APK nobody could install.

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
- [x] **F5 — Link bridge.** The host shows a code, `/join` bridges to the app
      or to the browser, and the app scans one itself — with no Play Services,
      so F-Droid stays possible. See [docs/deeplinks.md](docs/deeplinks.md).
- [x] **F6 — Robustness.** The ledger is written to storage after every accepted
      command, so a table survives the death of the process hosting it; the host
      can throw somebody out or hand a seat over, and closing the table tells
      everybody before the door shuts.
- [x] **F7 — Modes.** 7½ and blackjack as bank games — a stake out of the stack
      and a payout out of the bank, at a house ratio — and poker with a moving
      button, blinds, call amounts and side pots. Whose turn it is stays with
      the people at the table.
- [ ] **F8 — Release.** Versions and signed APKs on GitHub releases are
      automated (see Releasing); F-Droid metadata and reproducible builds are not
      done yet.

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
- **F5** — the checklist in `docs/deeplinks.md`, on two phones from different
  manufacturers. What a code carries and what a scanner reads back out of it is
  a JVM test; what a particular phone's camera app does with it is not.
- **F6** — kill the host app mid-game and pick the table back up. The ledger
  round trip, the restart and a browser reconnecting to a restarted host are
  covered on the JVM and in a real browser; a phone actually killed by Android
  for memory is not.
- **F7** — a night of each game with real people, who are the only ones who can
  say whether the host's four buttons are the right four.

## Decisions worth not reopening

- **No WebRTC, no PeerJS.** Signalling needs internet and WebRTC needs a secure
  context. Both kill the offline scenario.
- **No Bluetooth.** Web Bluetooth is not networking, Android's PAN is not
  reachable, and Nearby Connections or Wi-Fi Direct would force a native app on
  both sides.
- **No Termux.** The embedded server avoids native arm64 binaries, gets a
  supported lifecycle, and spares everyone a terminal.
- **No card dealing.** Play with a real deck.
- **Not every game gets a mode.** Which ones do, and why the manual mode covers
  the rest, is argued game by game in
  [docs/primitives-i-jocs.md](docs/primitives-i-jocs.md).
- **No online mode.** It would turn a local tool into an operated service, with
  the availability, abuse and risk profile that implies.

The app id is `io.github.tablechips`. It is the one irreversible
decision in the project — everything else, the repository name included, can be
changed tomorrow.

## Licence

GPLv3. See [LICENSE](LICENSE). Third-party assets and their licences are listed
in [NOTICE](NOTICE); the fonts and their OFL texts live in `third_party/fonts/`. For a consumer app this is what stops the work
coming back as a proprietary fork, which is exactly what this corner of the
world has too much of already.
