// Built renderer + Android adapter, isolated Chromium clipboard and synthetic history.
// Browser Ctrl+V exercises Chromium's trusted editing path, NOT Android's floating toolbar.
import {createServer} from 'node:http'
import {readFile,writeFile} from 'node:fs/promises'
import {extname,resolve} from 'node:path'
import {chromium,expect} from '@playwright/test'
import {installPhoneFixture} from './phone-fixture.mjs'
const baseline=process.env.BASELINE==='1'
const root=resolve('apps/desktop/dist')
const server=createServer(async(req,res)=>{try{const path=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!path.startsWith(root+'/'))throw Error();res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml'}[extname(path)]||'application/octet-stream'));res.end(await readFile(path))}catch{res.statusCode=404;res.end()}})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'/usr/bin/google-chrome',args:['--no-sandbox']})
try {
 const origin=`http://127.0.0.1:${server.address().port}`
 const context=await browser.newContext({viewport:{width:393,height:852},isMobile:true,hasTouch:true,permissions:['clipboard-read','clipboard-write']})
 const page=await context.newPage(),errors=[],passed=[]
 page.on('pageerror',e=>errors.push(e.message))
 await page.route('**/*',route=>route.request().url().startsWith(origin)?route.continue():route.abort())
 await page.addInitScript(installPhoneFixture)
 await page.addInitScript(()=>{
  const original=window.HermesNative.postMessage
  const isolatedBrowserWrite=navigator.clipboard.writeText.bind(navigator.clipboard)
  window.HermesNative.postMessage=raw=>{
   const c=JSON.parse(raw)
   if(c.method==='clipboard.write'){
    window.fixtureCalls.push(c)
    // This is a fresh headless browser clipboard, never the user's clipboard.
    isolatedBrowserWrite(c.params.text).then(()=>window.HermesNative.onmessage?.({data:JSON.stringify({id:c.id,result:true})}))
    return
   }
   if(c.method==='clipboard.read')throw Error('Unexpected renderer clipboard read')
   original(raw)
  }
 })
 await page.goto(origin)
 await expect(page.getByText('codex · Connected',{exact:true})).toBeVisible({timeout:20000})
 await page.getByRole('button',{name:'Sessions',exact:true}).click()
 await page.locator('#phone-sessions').getByText('TEST FIXTURE — shared history',{exact:true}).click()
 await expect(page.getByText('TEST FIXTURE historical answer',{exact:true})).toBeVisible()
 await page.getByRole('button',{name:'Copy',exact:true}).last().click({force:true})
 await expect.poll(()=>page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='clipboard.write').length)).toBe(1)
 const copied=await page.evaluate(()=>window.fixtureCalls.find(c=>c.method==='clipboard.write').params.text)
 expect(copied).toBe('TEST FIXTURE historical answer')
 passed.push('actual session message Copy button writes exact text through Android native adapter')
 const editor=page.getByRole('textbox',{name:'Message',exact:true})
 await editor.fill('existing draft')
 const gesture=async()=>editor.evaluate(el=>{const e=new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:100,clientY:500});el.dispatchEvent(e);return e.defaultPrevented})
 if(baseline){
  await gesture()
  await page.getByRole('menuitem',{name:/^Paste/}).click()
  await page.waitForTimeout(150)
  await expect(editor).toHaveText('existing draft')
  expect(await page.evaluate(()=>typeof window.hermesDesktop.contextMenuEdit)).toBe('undefined')
  passed.push('REPRODUCED: enabled app Paste silently does nothing; Android contextMenuEdit missing')
 }else{
  const select=async(start,end)=>editor.evaluate((el,{start,end})=>{
   el.focus();const node=el.firstChild;const range=document.createRange();range.setStart(node,start);range.setEnd(node,end);const sel=window.getSelection();sel.removeAllRanges();sel.addRange(range)
  },{start,end})
  const selection=()=>page.evaluate(()=>{const s=window.getSelection();return [s.anchorOffset,s.focusOffset]})
  await select(9,14)
  expect(await gesture()).toBe(false)
  await expect(page.getByRole('menu')).toHaveCount(0)
  expect(await selection()).toEqual([9,14])
  await expect(editor).toBeFocused()
  await page.keyboard.press('Control+v')
  await expect(editor).toHaveText('existing '+copied)
  passed.push('Android editable gesture leaves native default, focus and selection intact; trusted paste replaces selection once')
  await page.keyboard.type('!')
  await expect(editor).toHaveText('existing '+copied+'!')
  await page.getByRole('button',{name:'Sessions',exact:true}).click()
  await page.getByRole('button',{name:'Back to chat',exact:true}).click()
  await expect(editor).toHaveText('existing '+copied+'!')
  passed.push('controlled composer state survives typing and navigation-panel rerender')
  await editor.fill('prefix suffix');await select(7,7);await gesture();await page.keyboard.press('Control+v')
  await expect(editor).toHaveText('prefix '+copied+'suffix')
  passed.push('collapsed cursor inserts without losing surrounding draft or duplicating text')
  await editor.fill('cancel keeps draft');await select(2,6);await gesture();await page.keyboard.press('Escape')
  await expect(editor).toHaveText('cancel keeps draft');expect(await selection()).toEqual([2,6])
  await page.getByRole('button',{name:'Sessions',exact:true}).click();await page.waitForTimeout(50)
  await page.getByRole('button',{name:'Back to chat',exact:true}).click()
  await expect(editor).toHaveText('cancel keeps draft')
  passed.push('cancel and focus-away do not insert, read clipboard or mutate draft')
  await editor.fill('');await editor.focus();expect(await gesture()).toBe(false);await page.keyboard.press('Control+v')
  await expect(editor).toHaveText(copied)
  passed.push('empty composer paste works through normal input event path')
  // The same shared renderer must retain the existing Electron menu on other platforms.
  await page.evaluate(()=>{window.hermesDesktop.platform='win32'})
  await gesture();await expect(page.getByRole('menuitem',{name:/^Paste/})).toBeVisible();await page.keyboard.press('Escape')
  passed.push('non-Android context menu retained; no Windows production change')
 }
 expect(await page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='clipboard.read').length)).toBe(0)
 expect(await page.evaluate(()=>window.fixtureCalls.filter(c=>c.rpc?.method==='prompt.submit').length)).toBe(0)
 expect(errors).toEqual([])
 passed.push('zero renderer clipboard reads, zero prompt submissions, zero page errors')
 const result={fixtureOnly:true,baseline,androidNativeToolbarTest:false,passed}
 await writeFile(`artifacts/clipboard-${baseline?'baseline':'fixed'}-receipt.json`,JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2))
}finally{await browser.close();server.close()}
