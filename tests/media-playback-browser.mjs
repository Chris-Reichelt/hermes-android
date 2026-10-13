// Actual packaged renderer and attachment controls; isolated synthetic native port.
import {createServer} from 'node:http'
import {readFile,writeFile} from 'node:fs/promises'
import {extname,resolve} from 'node:path'
import {chromium,expect} from '@playwright/test'
import {installPhoneFixture} from './phone-fixture.mjs'
const clip=await readFile('/home/will/.hermes/profiles/codex/audio_cache/tts_20260928_184919_710541.mp3')
const root=resolve('apps/desktop/dist'), baseline=process.env.BASELINE==='1'
const server=createServer(async(req,res)=>{try{const p=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!p.startsWith(root+'/'))throw Error();res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css'}[extname(p)]||'application/octet-stream'));res.end(await readFile(p))}catch{res.statusCode=404;res.end()}})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox','--autoplay-policy=user-gesture-required']})
try {
 const page=await browser.newPage({viewport:{width:393,height:852}}),origin=`http://127.0.0.1:${server.address().port}`
 await page.route('**/*',r=>r.request().url().startsWith(origin)?r.continue():r.abort())
 await page.addInitScript(installPhoneFixture)
 await page.addInitScript(base64=>{
  const post=window.HermesNative.postMessage
  const emit=m=>setTimeout(()=>window.HermesNative.onmessage({data:JSON.stringify(m)}),0)
  window.HermesNative.postMessage=raw=>{
   const c=JSON.parse(raw),p=c.params||{},q=c.method==='socket.send'?JSON.parse(p.data):{}
   if(['session.resume','session.history'].includes(q.method)){
    emit({event:'message',socket:p.id,data:JSON.stringify({jsonrpc:'2.0',id:q.id,result:{session_id:'fixture-runtime',stored_session_id:'fixture-history',messages:[{role:'assistant',content:'TEST FIXTURE playable attachment\n\nMEDIA:/synthetic/fixture.mp3'}]}})})
    emit({id:c.id,result:true});return
   }
   if(c.method==='media.read'){window.fixtureCalls.push(c);emit({id:c.id,result:{base64}});return}
   post(raw)
  }
 },clip.toString('base64'))
 await page.goto(origin)
 await expect(page.getByText('codex · Connected',{exact:true})).toBeVisible({timeout:20000})
 await page.getByRole('button',{name:'Sessions',exact:true}).click()
 await page.locator('#phone-sessions').getByText('TEST FIXTURE — shared history',{exact:true}).click()
 const audio=page.locator('audio').first();await expect(audio).toBeVisible()
 let result
 if(baseline){
  await expect.poll(()=>audio.evaluate(a=>a.error?.code)).toBe(4)
  result=await audio.evaluate(a=>({src:a.src,error:a.error?.code,readyState:a.readyState}))
  expect(result.src).toMatch(/^hermes-media:\/\/remote\//)
 }else{
  await expect.poll(()=>audio.evaluate(a=>a.readyState)).toBeGreaterThanOrEqual(2)
  // Exercise the native HTML controls, not just new Audio() in a test page.
  const rect=await audio.boundingBox();await page.mouse.click(rect.x+20,rect.y+rect.height/2)
  await expect.poll(()=>audio.evaluate(a=>a.currentTime)).toBeGreaterThan(0.1)
  await audio.evaluate(a=>{a.pause();a.currentTime=10;a.playbackRate=4;return a.play()})
  await expect.poll(()=>audio.evaluate(a=>a.ended),{timeout:15000}).toBe(true)
  const call=await page.evaluate(()=>window.fixtureCalls.find(c=>c.method==='media.read'))
  expect(call.params).toMatchObject({path:'/synthetic/fixture.mp3',mime:'audio/mpeg',profile:'codex',connectionId:'android-remote'})
  result=await audio.evaluate(a=>({scheme:new URL(a.src).protocol,duration:a.duration,currentTime:a.currentTime,ended:a.ended,error:a.error?.code||null}))
  expect(result.scheme).toBe('blob:');expect(result.error).toBeNull()
 }
 await writeFile(`artifacts/media-playback-${baseline?'baseline':'fixed'}.json`,JSON.stringify({fixtureOnly:true,androidRuntime:false,clipBytes:clip.length,result},null,2));console.log(JSON.stringify(result))
}finally{await browser.close();server.close()}
