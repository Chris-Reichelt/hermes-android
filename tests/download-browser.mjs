// Production-renderer chat download click; synthetic port, no real backend.
import {createServer} from 'node:http'
import {readFile,writeFile} from 'node:fs/promises'
import {extname,resolve} from 'node:path'
import {chromium,expect} from '@playwright/test'
import {installPhoneFixture} from './phone-fixture.mjs'
const root=resolve('apps/desktop/dist')
const server=createServer(async(req,res)=>{try{const path=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!path.startsWith(root+'/'))throw Error();res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml'}[extname(path)]||'application/octet-stream'));res.end(await readFile(path))}catch{res.statusCode=404;res.end()}})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']})
try{
 const page=await browser.newPage({viewport:{width:393,height:852},isMobile:true,hasTouch:true})
 const origin=`http://127.0.0.1:${server.address().port}`,errors=[]
 page.on('pageerror',e=>errors.push(e.message))
 await page.route('**/*',route=>route.request().url().startsWith(origin)?route.continue():route.abort())
 await page.addInitScript(installPhoneFixture)
 await page.addInitScript(()=>{
  const original=window.HermesNative.postMessage
  window.fixtureSaveResult={saved:false,canceled:true}
  window.HermesNative.postMessage=raw=>{
   const c=JSON.parse(raw),p=c.params||{},q=c.method==='socket.send'?JSON.parse(p.data):{}
   const emit=m=>setTimeout(()=>window.HermesNative.onmessage?.({data:JSON.stringify(m)}),0)
   if(['session.resume','session.history'].includes(q.method)){
    window.fixtureCalls.push(c)
    emit({event:'message',socket:p.id,data:JSON.stringify({jsonrpc:'2.0',id:q.id,result:{session_id:'fixture-runtime',stored_session_id:'fixture-history',messages:[{role:'assistant',content:'TEST FIXTURE artifact\n\nMEDIA:/synthetic/fixture.apk'}]}})})
    emit({id:c.id,result:true});return
   }
   if(c.method==='download.file'){window.fixtureCalls.push(c);emit({id:c.id,...(window.fixtureSaveError?{error:window.fixtureSaveError}:{result:window.fixtureSaveResult})});return}
   original(raw)
  }
 })
 await page.goto(origin)
 await expect(page.getByText('codex · Connected',{exact:true})).toBeVisible({timeout:20000})
 await page.getByRole('button',{name:'Sessions',exact:true}).click()
 await page.locator('#phone-sessions').getByText('TEST FIXTURE — shared history',{exact:true}).click()
 await expect(page.getByText('fixture.apk',{exact:true}).first()).toBeVisible()
 await page.getByRole('button',{name:'Download',exact:true}).click()
 await expect.poll(()=>page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='download.file').length)).toBe(1)
 await expect(page.getByText('Download failed',{exact:true})).toHaveCount(0)
 const call=await page.evaluate(()=>window.fixtureCalls.find(c=>c.method==='download.file'))
 expect(call.params).toMatchObject({path:'/synthetic/fixture.apk',connectionId:'android-remote',profile:'codex'})
 await page.evaluate(()=>{window.fixtureSaveResult={saved:true,canceled:false}})
 await page.getByRole('button',{name:'Download',exact:true}).click()
 await expect.poll(()=>page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='download.file').length)).toBe(2)
 await page.evaluate(()=>{window.fixtureSaveError='Download failed [DL_DESTINATION_OPEN]. Could not open the selected document for writing. Choose local Downloads.'})
 await page.getByRole('button',{name:'Download',exact:true}).click()
 await expect(page.getByText('Download failed',{exact:true})).toBeVisible()
 await expect(page.getByText(/Download failed \[DL_DESTINATION_OPEN\]/)).toBeVisible()
 expect(errors).toEqual([])
 const result={fixtureOnly:true,nativePickerTest:false,passed:['actual chat artifact click calls native download.file','owner/path preserved','cancel quiet','success accepted','safe native stage identifier visible in failure toast'],call,pageErrors:errors}
 await writeFile(process.env.DOWNLOAD_RECEIPT||'artifacts/download-renderer-receipt.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2))
}finally{await browser.close();server.close()}
