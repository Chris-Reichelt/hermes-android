// One lazily-created AudioContext for the app's synthesized UI cues (wake
// chime, turn-completion sound, thinking blips). Browsers cap how many
// contexts a page may open, and these cues are short oscillator bursts that
// need no isolation from each other, so they share one instead of each holding
// its own. Nothing closes it — it lives for the window.

// One lazily-created AudioContext for the app's synthesized UI cues (wake
// chime, turn-completion sound, thinking blips). Browsers cap how many
// contexts a page may open, and these cues are short oscillator bursts that
// need no isolation from each other, so they share one instead of each holding
// its own. Nothing closes it — it lives for the window.
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

function ensureMediaSink(ac: AudioContext): AudioNode {
  if (soundDest) {
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

    return ctx
  } catch {
    return null
  }
}
