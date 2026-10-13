Q's public-preview v5 build — vCode 15, package com.hermes.privateapp.publicpreview, signed with the public-preview key (eba19114, CN "Public Developer Preview").

Installs over the v4 you already have (same package + key, higher versionCode = clean upgrade, not a reinstall).

What changed vs v4 (which was logging you in fine but then looping at the token-exchange step):
- The sign-in token POST now uses a brand-new connection on every retry with a short backoff (up to 5 attempts), so a network hiccup during the one-time code exchange recovers on its own instead of the app re-issuing a new code in a loop.
- Nothing else changed: same knight icon, grey system bars, light login panel, image attach, auto-return after sign-in.

If it still hangs at login after installing, force-close the app once (the loop is client-side) and sign in again.
