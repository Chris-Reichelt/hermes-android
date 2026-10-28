// Synthetic clipboard only; actual built renderer, isolated native-port fixture.
import {createServer} from 'node:http'
import {readFile,writeFile} from 'node:fs/promises'
import {extname,resolve} from 'node:path'
import {chromium,expect} from '@playwright/test'
import {installPhoneFixture} from './phone-fixture.mjs'
const root=resolve(process.env.RENDERER_ROOT || 'apps/desktop/dist')
const server=createServer(async(req,res)=>{try{const p=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!p.startsWith(root+'/'))throw Error();res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml'}[extname(p)]||'application/octet-stream'));res.end(await readFile(p))}catch{res.statusCode=404;res.end()}})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']})
const origin=`http://127.0.0.1:${server.address().port}`, results=[]
const url='https://example.test/package/preship/cancel-items?orderID=fixture&noPtRedirect=1'
try{
 const context=await browser.newContext({viewport:{width:393,height:852},permissions:['clipboard-read','clipboard-write']})
 const page=await context.newPage(); const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.dismiss())
 await page.route('**/*',r=>r.request().url().startsWith(origin)?r.continue():r.abort())
 await page.addInitScript(()=>{window.fixtureBrowserClipboardWrite=navigator.clipboard.writeText.bind(navigator.clipboard)})
 await page.addInitScript(installPhoneFixture);await page.goto(origin)
 const editor=page.getByRole('textbox',{name:'Message',exact:true});await expect(editor).toBeVisible({timeout:20000})
 async function paste(text){await page.evaluate(t=>window.fixtureBrowserClipboardWrite(t),text);await editor.focus();await page.keyboard.press('Control+v')}
 await paste(url)
 await expect(editor).toHaveText(url)
 await expect(editor.locator('[contenteditable=false]')).toHaveCount(0)
 await page.keyboard.press('Backspace');await expect(editor).toHaveText(url.slice(0,-1))
 await page.keyboard.type('2 then words');await expect(editor).toHaveText(url.slice(0,-1)+'2 then words')
 await page.keyboard.press('Control+a');await page.keyboard.press('Backspace');await expect(editor).toHaveText('')
 results.push('trusted paste, character backspace, typing, select-all delete')
 await editor.fill('before replace after');await editor.evaluate(e=>{const r=document.createRange();r.setStart(e.firstChild,7);r.setEnd(e.firstChild,14);const s=getSelection();s.removeAllRanges();s.addRange(r)})
 await paste(url);await expect(editor).toHaveText('before '+url+' after')
 await expect(editor.locator('[contenteditable=false]')).toHaveCount(0)
 results.push('trusted paste replaces selection without losing surrounding draft')
 await page.getByRole('button',{name:'Sessions',exact:true}).click();await page.getByRole('button',{name:'Back to chat',exact:true}).click();await expect(editor).toHaveText('before '+url+' after')
 await editor.focus();await page.keyboard.press('Control+a');await page.keyboard.press('Backspace');await expect(editor).toHaveText('')
 results.push('navigation rerender retains editable draft')
 await paste('first '+url+'\nsecond https://example.test/b#fragment');await expect(editor.locator('[contenteditable=false]')).toHaveCount(0)
 await page.keyboard.press('Control+a');await page.keyboard.press('Backspace');await expect(editor).toHaveText('')
 results.push('multiple multiline links remain editable and deletable')
 const calls=await page.evaluate(()=>window.fixtureCalls)
 expect(calls.filter(c=>c.rpc?.method==='prompt.submit')).toHaveLength(0)
 expect(calls.filter(c=>/readClipboard|clipboard\.read/i.test(c.method||''))).toHaveLength(0)
 expect(errors).toEqual([])
 const receipt={passed:true,fixtureOnly:true,nativeAndroidImeTest:false,results,pageErrors:errors}
 await writeFile('artifacts/link-editing-browser.json',JSON.stringify(receipt,null,2));console.log(JSON.stringify(receipt,null,2))
}finally{await browser.close();server.close()}
