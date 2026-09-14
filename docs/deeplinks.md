# Opening the app from a scanned link

Conclusions of the investigation the project plan asks for before any code is
written. Read this before touching intent filters.

## The short version

**A plain `http://` link cannot reliably open the app on a device where it has
just been installed.** The plan assumed this; the assumption holds. The bridge
page below is the way around it, and the in-app QR scanner is the path the
interface should push people towards.

## Why verified App Links are impossible here

Android App Links with `android:autoVerify="true"` require, without exception:

- an `https://` scheme, and
- a `/.well-known/assetlinks.json` file served from the link's own domain, which
  Android fetches **over the internet** at install time and re-checks later.

TableChips serves `http://192.168.x.x:8080/…` from a phone with no internet, no
domain, and no certificate. There is nothing to verify and no way to reach a
verifier. Verification will always fail, and a failed verification is not a soft
failure: it is the whole mechanism.

## Why an unverified filter is not enough either

Before Android 12, an unverified web intent filter put the app in the
disambiguation dialog ("Open with…"), and the user could pick it.

From Android 12 (API 31) onward, a web intent filter that is **not** verified is
excluded from that dialog. The link goes straight to the default browser. The
app only becomes a candidate if the user goes into Settings → Apps →
TableChips → Open by default and turns on "Open supported links" by hand. Nobody
does this at a card table, and asking guests to do it defeats the point of the
project.

## What the app does instead

1. The host shows a QR code with `http://<ip>:<port>/join?room=XXXX`.
2. Any camera or QR app opens that URL **in the browser**. That always works and
   needs nothing installed.
3. The server answers with a small bridge page that looks at the user agent:
   - **Android:** a button that navigates to an intent URI
     ```
     intent://join?host=<ip>&port=<port>&room=XXXX#Intent;
       scheme=tablechips;
       package=io.github.victormico.tablechips;
       S.browser_fallback_url=<url of the web client>;
     end
     ```
     If the app is installed it opens; if it is not, the browser follows the
     fallback URL and the guest simply plays in the browser. Chrome, Samsung
     Internet and every Chromium-based browser support `intent://`; Firefox for
     Android supports it too. A browser that does not will sit on the page, so
     the page must also carry a plain link to the web client.
   - **iOS and everything else:** straight to the web client, with no app
     button. `intent://` is Android-only and there is no iOS app to open.
4. The app has its own QR scanner, which skips all of the above and connects
   directly. **This is the fast path and the interface should say so.**

## What gets declared in the manifest, and when

These filters belong to the link bridge phase (F5), not before, and they are
deliberately absent from the manifest until then:

- `tablechips://join?host=…&port=…&room=…` — the custom scheme, which is what
  the intent URI actually launches. Custom schemes need no verification.
- An `http` filter **without** `autoVerify`, for the minority of users who
  turn on "Open supported links" themselves. It costs nothing and helps them.

Neither filter may be trusted with anything: an intent that reaches the app
carries a host, a port and a room code chosen by whoever produced the QR, so the
app must treat all three as untrusted input and show the user what it is about
to connect to.

## What still has to be checked on real devices

The acceptance criterion for F5 is this file, verified on two devices from
different manufacturers:

- [ ] the intent URI opens the app when it is installed (Android 12, 13, 14+)
- [ ] the fallback URL opens the web client when it is not
- [ ] a scan from the system camera app, from Google Lens and from a third-party
      QR reader all reach the bridge page
- [ ] the bridge page is readable and usable on iOS Safari
- [ ] nothing in the flow needs internet at any point
