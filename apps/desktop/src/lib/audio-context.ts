// One lazily-created AudioContext for the app's synthesized UI cues (wake
// chime, turn-completion sound, thinking blips). Browsers cap how many
// contexts a page may open, and these cues are short oscillator bursts that
// need no isolation from each other, so they share one instead of each holding
// its own. Nothing closes it — it lives for the window.

// One lazily-created AudioContext for the app's synthesized UI cues (wake
// chime, turn-completion sound, thinking blips). Browsers cap how many
// contexts a page may open, and these cues are short oscillator bursts that
// need no isolation from each other, so they share one instead of each holding
// its own. On desktop the context closes itself after an idle grace window so
// it never holds the audio output device between cues.
//
// Android WebView routes raw WebAudio output to the ALARM stream (Chromium
// bug): it ignores silent mode and never reaches Bluetooth. To make cues
// behave like normal media audio, the context's output is mirrored through a
// MediaStreamAudioDestinationNode into a hidden <audio> element — the media
// stream honours system volume, ringer silence, and Bluetooth routing.
// getSoundDestination() returns the node cues should connect to instead of
// ctx.destination.

let ctx: AudioContext | null = null
let soundDest: AudioNode | null = null
let mediaSink: HTMLAudioElement | null = null
const mediaSinks: { dest: AudioNode; el: HTMLAudioElement }[] = []

// The media-stream mirror is an Android-WebView-only workaround (see the
// header). On desktop it is a liability: a playing <audio> element — even one
// fed by a silent MediaStream — opens a Chromium playback stream that PulseAudio
// / PipeWire treat as media-role, and that role SUSPENDS every other app's
// playback for as long as the stream lives. A desktop user then loses Spotify
// the moment Hermes plays its first chime, with no way to get it back short of
// quitting the app. So: mirror on Android, plain `ctx.destination` everywhere
// else. `platform` is stamped by the Android shell (same read as the composer's
// `isAndroidPlatform`); the desktop `hermesDesktop` type has no such key.
const isAndroidShell = (): boolean =>
  (window.hermesDesktop as { platform?: string } | undefined)?.platform === 'android'

// Chromium opens the audio output device the moment an AudioContext starts
// rendering and keeps it open for the context's whole life — media-role on
// PipeWire again. Cues are short bursts with long gaps, so the shared context
// closes itself when idle and the next cue re-creates it; holding the output
// device between chimes buys nothing on desktop.
const IDLE_CLOSE_MS = 30_000
let idleTimer: ReturnType<typeof globalThis.setTimeout> | null = null

function closeIdle() {
  idleTimer = null

  for (const sink of mediaSinks.splice(0)) {
    sink.el.pause()
    sink.el.srcObject = null
  }

  mediaSink?.pause()
  mediaSink = null
  soundDest = null

  ctx?.close().catch(() => undefined)
  ctx = null
}

function ensureMediaSink(ac: AudioContext): AudioNode {
  if (soundDest) {
    return soundDest
  }

  if (!isAndroidShell()) {
    soundDest = ac.destination

    return soundDest
  }

  try {
    const dest = ac.createMediaStreamDestination()
    const el = document.createElement('audio')
    el.srcObject = dest.stream
    el.autoplay = true
    el.volume = 1
    void el.play().catch(() => undefined)
    mediaSink = el
    soundDest = dest

    return dest
  } catch {
    // MediaStream mirroring unavailable: fall back to raw context output.
    soundDest = ac.destination

    return soundDest
  }
}

/** Where UI cues connect: the media-stream sink (Android-correct routing),
 *  falling back to the raw context destination where unsupported. */
export function getSoundDestination(): AudioNode | null {
  const ac = getAudioContext()

  return ac ? ensureMediaSink(ac) : null
}

/** Route an arbitrary AudioContext's output through a hidden media-stream <audio>
 *  element (STREAM_MUSIC: system volume, ringer silence, Bluetooth routing). Used
 *  for streamed TTS playback, which runs on its own context for latency. */
export function attachMediaSink(ac: AudioContext): AudioNode {
  if (!isAndroidShell()) {
    return ac.destination
  }

  try {
    const dest = ac.createMediaStreamDestination()
    const el = document.createElement('audio')
    el.srcObject = dest.stream
    el.autoplay = true
    el.volume = 1
    void el.play().catch(() => undefined)
    // Keep the element alive for the context's lifetime; nothing else holds it.
    mediaSinks.push({ el, dest })

    return dest
  } catch {
    return ac.destination
  }
}

/** Whether UI sonification is allowed by the phone's system settings.
 *  The Android shell injects `window.androidAudio.ringerMode()` (0 silent,
 *  1 vibrate, 2 normal); elsewhere (desktop) cues are always allowed. */
export function uiSoundsAllowed(): boolean {
  try {
    const native = (window as unknown as { androidAudio?: { ringerMode?: () => number } }).androidAudio

    if (native?.ringerMode) {
      return native.ringerMode() === 2
    }
  } catch {
    // treat as allowed
  }

  return true
}

/** The shared AudioContext, or null where WebAudio is unavailable. */
export function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    if (!ctx) {
      const Ctor =
        window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext

      if (!Ctor) {
        return null
      }

      ctx = new Ctor()
    }

    // Autoplay policies can leave the context suspended until a gesture; a
    // resume() here recovers it once the user has interacted with the window.
    if (ctx.state === 'suspended') {
      void ctx.resume().catch(() => undefined)
    }

    // Every use rearms (or cancels) the idle close: the device is held for a
    // grace window after the LAST cue, not for the window's life.
    if (idleTimer !== null) {
      globalThis.clearTimeout(idleTimer)
    }

    idleTimer = globalThis.setTimeout(closeIdle, IDLE_CLOSE_MS)

    return ctx
  } catch {
    return null
  }
}
