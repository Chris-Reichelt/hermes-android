Hermes Public Preview — v6 (vc16)
=================================

WHY THIS BUILD
The sign-in was hanging because the flow depended on an external browser
plus a 127.0.0.1 loopback socket plus a one-time code that has to be handed
off in a race. When that handoff dropped, the code expired and the app just
re-issued a new one, forever — the "hanging at the same sign-in screen" loop.

THE FIX (in-app sign-in)
v6 adds a new, reliable sign-in mode that is now the DEFAULT option:
   "Sign in in-app (type username & password)"

You enter your username and password right in the app. The app then:
  1. mints its own PKCE broker (its own key material — no browser involved),
  2. POSTs your credentials to the gateway with that broker attached,
  3. the gateway mints a one-time code FOR the app's own broker,
  4. the app redeems that code with its own key -> bearer session.

No external browser, no 127.0.0.1 socket, no timing race. It's a direct
in-app request, so it can't "hang at the sign-in screen" the way the
browser flow did. The old "Sign in with browser" mode is still available as
the 2nd spinner option if you ever want it.

INSTALL
This is a clean in-place upgrade over v5 (same package name + signing key,
higher version code 16). Just install the APK — no uninstall needed.

USAGE
1. Connect Tailscale first (the gateway is only reachable on your tailnet).
2. Open the app. The gateway URL is remembered from last time.
3. The spinner defaults to "Sign in in-app (type username & password)".
   Enter your username and password in the two fields.
4. Tap "Sign in / Connect". It signs in directly and opens Hermes.

If it shows an error, it will say exactly what (e.g. "Incorrect username or
password." or "Too many login attempts."). There is no more silent hang.
