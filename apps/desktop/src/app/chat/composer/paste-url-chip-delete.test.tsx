// @vitest-environment jsdom
import { AssistantRuntimeProvider, useExternalStoreRuntime } from '@assistant-ui/react'
import type { ThreadMessageLike } from '@assistant-ui/react'
import { act, cleanup, fireEvent, render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

import { I18nProvider } from '@/i18n'
import { mainComposerScope } from '@/store/composer'

import { ChatBar } from './index'
import { placeCaretEnd, RICH_INPUT_SLOT } from './rich-editor'
import type { ChatBarState } from './types'

afterEach(cleanup)

// THE INVARIANT (Chris's apk42–apk44 reports): on the phone, a pasted link
// chips instantly (native insert + input-time linkify) AND the phone
// keyboard's backspace deletes the whole chip. The delete rides the NATIVE
// beforeinput listener (React's onBeforeInput polyfill never sees IME
// deletes) and the chip-delete is adjacency-strict, so the repaint must
// normalize Chromium's phantom scaffolding before the caret — this test
// drives the exact Android sequence.
const URL = 'https://www.amazon.com/progress-tracker/package/preship/cancel-items?orderID=112-1842805&x=1'

const state: ChatBarState = {
  model: { canSwitch: false, model: '', provider: '' },
  tools: { enabled: false, label: '' },
  voice: { enabled: false, active: false }
}

function Harness() {
  const runtime = useExternalStoreRuntime({
    convertMessage: (message: ThreadMessageLike) => message,
    isRunning: false,
    messages: [] as ThreadMessageLike[],
    onNew: async () => {}
  })

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <MemoryRouter>
        <I18nProvider configClient={null} initialLocale="en">
          <ChatBar busy={false} disabled={false} gateway={null} onCancel={vi.fn()} onSubmit={vi.fn(async () => true)} state={state} />
        </I18nProvider>
      </MemoryRouter>
    </AssistantRuntimeProvider>
  )
}

describe('phone: pasted link chips, then the keyboard deletes the chip', () => {
  beforeAll(() => {
    // The Android shell stamps `platform`; the composer's paste/delete paths
    // gate on it.
    vi.stubGlobal('hermesDesktop', { platform: 'android' })
  })

  afterAll(() => {
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    mainComposerScope.clear()
  })

  it('input-time linkify chips the natively inserted URL, beforeinput deletes it whole', () => {
    const { container } = render(<Harness />)

    const editor = container.querySelector<HTMLElement>(`[data-slot="${RICH_INPUT_SLOT}"]`)!

    Object.defineProperty(editor, 'isContentEditable', { configurable: true, value: true })
    editor.focus()

    // 1. Android paste: handlePaste does NOT preventDefault; Chromium's
    //    editing pipeline inserts the URL text — but the IME commits the
    //    TAIL (`1`) after the first input event (Chris's apk45 screenshot:
    //    chip ending at `noPtRedirect=` with a stray `1` after it).
    editor.textContent = URL.slice(0, -1)

    act(() => {
      fireEvent.input(editor)
    })

    // The head chipped; the tail has not arrived yet.
    expect(editor.querySelector('[data-ref-kind="url"]')).not.toBeNull()

    // The IME commits the tail as a text node glued after the chip element.
    editor.appendChild(document.createTextNode('1'))

    act(() => {
      fireEvent.input(editor)
    })

    // 2. The residue folded back into the chip: the URL is complete and NO
    //    raw text sits between the caret and the chip.
    const chip = editor.querySelector('[data-ref-kind="url"]')

    expect(chip).not.toBeNull()
    expect(chip?.getAttribute('data-ref-id')).toBe(URL)
    expect(editor.textContent ?? '').not.toContain('https://')

    // 3. Phone backspace: Gboard delivers neither a Backspace keydown nor a
    //    React polyfill event — only the native beforeinput below.
    const backspace = new InputEvent('beforeinput', {
      bubbles: true,
      cancelable: true,
      inputType: 'deleteContentBackward'
    })

    act(() => {
      editor.dispatchEvent(backspace)
    })

    // The whole chip (with its trailing space) is gone — not one label
    // character, not nothing.
    expect(editor.querySelector('[data-ref-kind="url"]')).toBeNull()
    expect(editor.textContent ?? '').not.toContain('https://')
    expect(backspace.defaultPrevented).toBe(true)
  })

  it('the trailing fallback deletes the chip even when scaffolding sits between caret and chip', () => {
    const { container } = render(<Harness />)

    const editor = container.querySelector<HTMLElement>(`[data-slot="${RICH_INPUT_SLOT}"]`)!

    Object.defineProperty(editor, 'isContentEditable', { configurable: true, value: true })
    editor.focus()

    editor.textContent = URL

    act(() => {
      fireEvent.input(editor)
    })

    expect(editor.querySelector('[data-ref-kind="url"]')).not.toBeNull()

    // Chromium leaves a trailing break after the repaint and the caret ends
    // up past it — the adjacency-strict delete sees the break, not the chip.
    editor.appendChild(document.createElement('br'))
    placeCaretEnd(editor)

    const backspace = new InputEvent('beforeinput', {
      bubbles: true,
      cancelable: true,
      inputType: 'deleteContentBackward'
    })

    act(() => {
      editor.dispatchEvent(backspace)
    })

    expect(editor.querySelector('[data-ref-kind="url"]')).toBeNull()
    expect(backspace.defaultPrevented).toBe(true)
  })

  it('backspace during live composition of the paste tail deletes the chip, not one composition character', () => {
    const { container } = render(<Harness />)

    const editor = container.querySelector<HTMLElement>(`[data-slot="${RICH_INPUT_SLOT}"]`)!

    Object.defineProperty(editor, 'isContentEditable', { configurable: true, value: true })
    editor.focus()

    editor.textContent = URL.slice(0, -1)

    act(() => {
      fireEvent.input(editor)
    })

    expect(editor.querySelector('[data-ref-kind="url"]')).not.toBeNull()

    // The tail sits in live IME composition — compositionend has not fired,
    // no input event has run for it. Chris's screenshots: the highlighted
    // residue between the caret and the chip.
    editor.appendChild(document.createTextNode('1'))
    placeCaretEnd(editor)

    const backspace = new InputEvent('beforeinput', {
      bubbles: true,
      cancelable: true,
      inputType: 'deleteContentBackward',
      isComposing: true
    })

    act(() => {
      editor.dispatchEvent(backspace)
    })

    // The chip AND its composed tail are gone — not one character, and the
    // native composition delete was cancelled so the buffer can't re-insert.
    expect(editor.querySelector('[data-ref-kind="url"]')).toBeNull()
    expect(editor.textContent ?? '').not.toContain('https://')
    expect(backspace.defaultPrevented).toBe(true)
  })
})
