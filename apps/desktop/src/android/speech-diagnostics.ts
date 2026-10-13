import { atom } from 'nanostores'

// Bounded, memory-only metadata. Never retain reply text, tokens, URLs or
// native exception details. Kept visible so phone-only failures aren't silent.
export const $phoneSpeechTrace = atom<string[]>([])
export function tracePhoneSpeech(stage: string, profile?: string | null) {
  if (window.hermesDesktop?.platform !== 'android') return
  const line = `${new Date().toLocaleTimeString()} · ${stage}${profile ? ` · profile ${profile}` : ''}`
  $phoneSpeechTrace.set([...$phoneSpeechTrace.get().slice(-7), line])
}
