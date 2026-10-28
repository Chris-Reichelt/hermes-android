// @vitest-environment jsdom
import { AssistantRuntimeProvider, useExternalStoreRuntime } from '@assistant-ui/react'
import type { ThreadMessageLike } from '@assistant-ui/react'
import { act, cleanup, fireEvent, render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { I18nProvider } from '@/i18n'
import { mainComposerScope } from '@/store/composer'
import { ChatBar } from './index'
import { composerPlainText, placeCaretEnd, RICH_INPUT_SLOT } from './rich-editor'
import type { ChatBarState } from './types'
const URL = 'https://example.test/package/preship/cancel-items?orderID=fixture&x=1'
const state: ChatBarState = { model: { canSwitch: false, model: '', provider: '' }, tools: { enabled: false, label: '' }, voice: { enabled: false, active: false } }
function Harness() {
 const runtime = useExternalStoreRuntime({convertMessage: (m: ThreadMessageLike) => m, isRunning:false,messages:[] as ThreadMessageLike[],onNew:async()=>{}})
 return <AssistantRuntimeProvider runtime={runtime}><MemoryRouter><I18nProvider configClient={null} initialLocale="en"><ChatBar busy={false} disabled={false} gateway={null} onCancel={vi.fn()} onSubmit={vi.fn(async()=>true)} state={state}/></I18nProvider></MemoryRouter></AssistantRuntimeProvider>
}
afterEach(()=>{cleanup();mainComposerScope.clear();vi.unstubAllGlobals()})
function mount(){vi.stubGlobal('hermesDesktop',{platform:'android'});const {container}=render(<Harness/>);const e=container.querySelector<HTMLElement>(`[data-slot="${RICH_INPUT_SLOT}"]`)!;Object.defineProperty(e,'isContentEditable',{configurable:true,value:true});e.focus();return e}
describe('Android links remain native editable text, not IME-invalidating chips',()=>{
 it('does not replace the native paste text node or move its selection',()=>{
  const e=mount();e.textContent=URL;placeCaretEnd(e);const node=e.firstChild;const anchor=getSelection()!.anchorNode;const offset=getSelection()!.anchorOffset
  act(()=>{fireEvent.input(e)})
  expect(e.firstChild).toBe(node);expect(getSelection()!.anchorNode).toBe(anchor);expect(getSelection()!.anchorOffset).toBe(offset)
  expect(e.querySelector('[contenteditable=false]')).toBeNull();expect(composerPlainText(e)).toBe(URL)
 })
 it('preserves split IME commits and does not cancel a non-cancelable composition delete',()=>{
  const e=mount();e.textContent=URL.slice(0,-1);const node=e.firstChild!
  act(()=>{fireEvent.input(e);fireEvent.compositionStart(e)})
  node.textContent+='1';placeCaretEnd(e)
  const del=new InputEvent('beforeinput',{bubbles:true,cancelable:false,inputType:'deleteContentBackward',isComposing:true})
  act(()=>{e.dispatchEvent(del)})
  expect(del.defaultPrevented).toBe(false);expect(e.firstChild).toBe(node);expect(composerPlainText(e)).toBe(URL)
  // jsdom cannot perform a native IME edit: emulate only that DOM mutation.
  node.textContent=URL.slice(0,-1)
  act(()=>{fireEvent.input(e);fireEvent.compositionEnd(e)})
  expect(e.firstChild).toBe(node);expect(composerPlainText(e)).toBe(URL.slice(0,-1));expect(e.querySelector('[contenteditable=false]')).toBeNull()
 })
 it('space after a pasted URL does not recreate the non-editable chip',()=>{
  const e=mount();e.textContent=URL;placeCaretEnd(e)
  act(()=>{fireEvent.input(e)})
  expect(fireEvent.keyDown(e,{key:' ',code:'Space'})).toBe(true)
  expect(e.querySelector('[data-ref-kind=url]')).toBeNull();expect(composerPlainText(e)).toBe(URL)
 })
})
