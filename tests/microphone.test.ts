import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { micError, type MicRecorderErrorCopy } from '../apps/desktop/src/app/chat/composer/hooks/use-mic-recorder'

const copy: MicRecorderErrorCopy = {
  microphoneAccessDenied: 'access denied', microphoneConstraintsUnsupported: 'constraints',
  microphoneInUse: 'already in use', microphonePermissionDenied: 'permission denied',
  microphoneStartFailed: 'Could not start microphone.', microphoneUnsupported: 'unsupported', noMicrophone: 'no mic'
}
describe('Android capture contract', () => {
  it('declares both permissions required by Chromium Android audio-device selection', () => {
    const manifest = readFileSync('android/app/src/main/AndroidManifest.xml', 'utf8')
    for (const permission of ['RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS']) {
      expect(manifest).toContain(`android:name="android.permission.${permission}"`)
    }
  })
  it.each(['NotReadableError', 'TrackStartError'])('does not misdiagnose %s as another app owning the mic', name => {
    const error = micError(new DOMException('private device label / raw details', name), copy)
    expect(error.message).toBe(`Could not start microphone. (${name})`)
    expect(error.message).not.toContain('private')
    expect(error.message).not.toContain('in use')
  })
  it('preserves permission, device and constraint distinctions', () => {
    expect(micError(new DOMException('', 'NotAllowedError'), copy).message).toBe('permission denied')
    expect(micError(new DOMException('', 'NotFoundError'), copy).message).toBe('no mic')
    expect(micError(new DOMException('', 'OverconstrainedError'), copy).message).toBe('constraints')
  })
  it('preserves non-capture errors rather than mislabeling backend failures', () => {
    const error = new Error('backend failed')
    expect(micError(error, copy)).toBe(error)
  })
})
