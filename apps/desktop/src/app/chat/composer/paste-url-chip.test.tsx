// @vitest-environment jsdom
import { AssistantRuntimeProvider, useExternalStoreRuntime } from '@assistant-ui/react'
import type { ThreadMessageLike } from '@assistant-ui/react'
import { act, cleanup, fireEvent, render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { I18nProvider } from '@/i18n'
import { mainComposerScope } from '@/store/composer'

import { ChatBar } from './index'
import { composerPlainText, RICH_INPUT_SLOT } from './rich-editor'
import type { ChatBarState } from './types'

afterEach(cleanup)

// THE INVARIANT (Android/Chris report): pasting a link must land as a clean
// `@url:` chip IMMEDIATELY — never raw URL text that the user has to "fix" by
// typing a space and deleting back. The chipping pipeline (linkifyUrls →
// insertComposerContentsAtCaret → chip) is what makes paste read as a link,
// not a wall of URL text; this pins it against regressions on every surface
// that shares this composer (desktop Electron AND the Android WebView shell).
const URL = 'https://example.com/a/b?x=1'

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

function pasteInto(editor: HTMLElement, text: string) {
  Object.defineProperty(editor, 'isContentEditable', { configurable: true, value: true })
  editor.focus()

  const event = new Event('paste', { bubbles: true, cancelable: true }) as ClipboardEvent

  Object.defineProperty(event, 'clipboardData', {
    value: {
      getData: (type: string) => (type === 'text' || type === 'text/plain' ? text : ''),
      files: [],
      items: []
    }
  })

  act(() => {
    fireEvent(editor, event)
  })

  return event
}

describe('a pasted link lands as a chip immediately', () => {
  afterEach(() => {
    mainComposerScope.clear()
  })

  it('chips the URL with no raw directive/URL text left in the editor', () => {
    const { container } = render(<Harness />)

    const editor = container.querySelector<HTMLElement>(`[data-slot="${RICH_INPUT_SLOT}"]`)!

    pasteInto(editor, URL)

    const chip = editor.querySelector('[data-ref-kind="url"]')

    // The chip is there…
    expect(chip).not.toBeNull()
    // …labeled as a link (host+path), not the raw URL…
    expect(chip?.textContent).toContain('example.com/a/b?x=1')
    // …and NO raw text remains — no `@url:` directive text, no bare URL
    // sitting in the editor waiting for a space to commit it.
    expect(editor.textContent).not.toContain('@url:')
    expect(editor.textContent).not.toContain('https://')
    // The draft round-trips to the directive exactly once.
    expect(composerPlainText(editor)).toBe(`@url:\`${URL}\``)
  })
})
