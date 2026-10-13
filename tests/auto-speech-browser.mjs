// Production Android renderer/adapter; synthetic native transport and PCM only.
import {createServer} from 'node:http'
import {readFile,writeFile} from 'node:fs/promises'
import {extname,resolve} from 'node:path'
import {chromium,expect} from '@playwright/test'
import {installPhoneFixture} from './phone-fixture.mjs'
const root=resolve('apps/desktop/dist')
const server=createServer(async(req,res)=>{try{const path=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!path.startsWith(root+'/'))throw Error();res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css'}[extname(path)]||'application/octet-stream'));res.end(await readFile(path))}catch{res.statusCode=404;res.end()}})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox','--autoplay-policy=user-gesture-required']})
const origin=`http://127.0.0.1:${server.address().port}`
const page=await browser.newPage({viewport:{width:393,height:852}})
const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')console.log('CONSOLE',m.text())})
let directBlocked=0
await page.route('**/*',r=>{if(r.request().url().includes('blocked-fixture.invalid/audio/speech'))directBlocked++;return r.request().url().startsWith(origin)?r.continue():r.abort()})
await page.addInitScript(installPhoneFixture)
await page.addInitScript(direct=>{window.fixtureDirect=direct},process.env.DIRECT==='1')
await page.addInitScript(()=>{
 window.audioEvidence=[];window.fixtureAudioMode='pcm'
 const NativeAudio=window.Audio
 window.Audio=function(...args){const a=new NativeAudio(...args);a.addEventListener('playing',()=>window.audioEvidence.push({event:'html-playing',duration:a.duration}));a.addEventListener('ended',()=>window.audioEvidence.push({event:'html-ended',duration:a.duration}));return a}
 const AC=window.AudioContext
 window.AudioContext=class extends AC{constructor(...args){super(...args);window.audioEvidence.push({event:'context',state:this.state});this.addEventListener('statechange',()=>window.audioEvidence.push({event:'state',state:this.state}));const buffer=this.createBufferSource.bind(this);this.createBufferSource=()=>{const s=buffer();s.addEventListener('ended',()=>window.audioEvidence.push({event:'source-ended',time:this.currentTime,state:this.state,duration:s.buffer?.duration,sampleRate:s.buffer?.sampleRate}));return s}}}
 const post=window.HermesNative.postMessage.bind(window.HermesNative),sockets={}
 const emit=m=>setTimeout(()=>window.HermesNative.onmessage({data:JSON.stringify(m)}),0)
 window.fixtureReply=(text)=>{for(const socket of Object.keys(sockets).filter(k=>new URL(sockets[k]).pathname==='/api/ws').map(Number))for(const [type,payload] of [['message.start',{}],['message.delta',{text}],['message.complete',{text}],['session.idle',{}]])emit({event:'message',socket,data:JSON.stringify({jsonrpc:'2.0',method:'event',params:{type,payload,session_id:'fixture-new'}})})}
 window.HermesNative.postMessage=raw=>{
  const c=JSON.parse(raw),p=c.params||{}
  if(c.method==='socket.open'){
   sockets[p.id]=p.url
   if(p.url.includes('speak-stream')&&window.fixtureAudioMode==='rest'){window.fixtureCalls.push(c);emit({id:c.id,error:'TEST FIXTURE native socket open refused'});return}
  }
  if(c.method==='api'&&p.path==='/api/config/schema'){window.fixtureCalls.push(c);emit({id:c.id,result:{fields:{'voice.auto_tts':{type:'boolean',category:'voice'}}}});return}
  if(c.method==='api'&&p.path.split('?')[0]==='/api/config'){window.fixtureCalls.push(c);if(p.method==='PUT')window.fixtureConfig=p.body.config;emit({id:c.id,result:p.method==='PUT'?{ok:true}:(window.fixtureConfig||{model:{default:'test-model',provider:'custom'},display:{language:'en'},onboarding:{completed:true},voice:{auto_tts:false}})});return}
  if(c.method==='api'&&p.path.startsWith('/api/audio/voice-config')){window.fixtureCalls.push(c);emit({id:c.id,result:{ok:true,stt:{mode:'relay'},tts:window.fixtureDirect?{mode:'direct',wire:'openai-speech',base_url:'https://blocked-fixture.invalid',provider:'fixture',api_key:'fixture-not-a-secret',model:'fixture',voice:'fixture'}:{mode:'relay'}}});return}
  if(c.method==='api'&&p.path.startsWith('/api/audio/speak')){window.fixtureCalls.push(c);
   if(window.fixtureAudioMode==='rest'){
    const b=new ArrayBuffer(44+9600),v=new DataView(b),put=(offset,s)=>[...s].forEach((c,i)=>v.setUint8(offset+i,c.charCodeAt(0)));put(0,'RIFF');v.setUint32(4,9636,true);put(8,'WAVEfmt ');v.setUint32(16,16,true);v.setUint16(20,1,true);v.setUint16(22,1,true);v.setUint32(24,24000,true);v.setUint32(28,48000,true);v.setUint16(32,2,true);v.setUint16(34,16,true);put(36,'data');v.setUint32(40,9600,true);for(let i=0;i<4800;i++)v.setInt16(44+i*2,Math.sin(i*2*Math.PI*440/24000)*4000,true);
    emit({id:c.id,result:{ok:true,mime_type:'audio/wav',data_url:'data:audio/wav;base64,'+btoa(String.fromCharCode(...new Uint8Array(b)))}})
   }else emit({id:c.id,error:'TEST FIXTURE synthesis unavailable'});return}
  if(c.method==='socket.send'&&sockets[p.id]?.includes('/api/audio/speak-stream')){
   window.fixtureCalls.push(c);const f=JSON.parse(p.data)
   if(f.done){
    if(window.fixtureAudioMode!=='pcm')emit({event:'message',socket:p.id,data:JSON.stringify({type:'fallback'})})
    else {emit({event:'message',socket:p.id,data:JSON.stringify({type:'start',sample_rate:24000})});const pcm=new Int16Array(4800);for(let i=0;i<pcm.length;i++)pcm[i]=Math.sin(i*2*Math.PI*440/24000)*4000;emit({event:'message',socket:p.id,binary:true,data:btoa(String.fromCharCode(...new Uint8Array(pcm.buffer)))});emit({event:'message',socket:p.id,data:JSON.stringify({type:'end'})})}
   }
   emit({id:c.id,result:true});return
  }
  post(raw)
 }
})
try{
 await page.goto(origin)
 await expect(page.getByText('codex · Connected',{exact:true})).toBeVisible({timeout:20000})
 await page.getByRole('button',{name:'New chat',exact:true}).click()
 await page.getByRole('textbox',{name:'Message',exact:true}).fill('TEST FIXTURE only')
 await page.getByRole('button',{name:'Send',exact:true}).click()
 await expect.poll(()=>page.evaluate(()=>window.fixtureCalls.filter(c=>c.rpc?.method==='prompt.submit').length)).toBe(1)
 await page.evaluate(()=>window.fixtureReply('TEST FIXTURE disabled reply'))
 await expect(page.getByText('TEST FIXTURE disabled reply',{exact:true})).toBeVisible()
 expect(await page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='socket.open'&&c.params.url.includes('speak-stream')).length)).toBe(0)
 await page.evaluate(()=>{location.hash='/settings?tab=config%3Avoice&page=speech'})
 await expect(page.getByText('Read Responses Aloud',{exact:true})).toBeVisible()
 await page.getByRole('switch',{name:'Read Responses Aloud',exact:true}).click()
 await expect(page.getByRole('switch',{name:'Read Responses Aloud',exact:true})).toBeChecked()
 await expect.poll(()=>page.evaluate(()=>localStorage.getItem('hermes.desktop.autoSpeakReplies'))).toBe('true')
 await page.getByRole('button',{name:'Close settings',exact:true}).click()
 await expect(page.getByText('TEST FIXTURE disabled reply',{exact:true})).toBeVisible()
 expect(await page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='socket.open'&&c.params.url.includes('speak-stream')).length)).toBe(0)
 await page.evaluate(()=>window.fixtureReply('TEST FIXTURE fresh after settings toggle'))
 await expect(page.getByText('TEST FIXTURE fresh after settings toggle',{exact:true})).toBeVisible()
 await page.waitForTimeout(1000)
 const result=await page.evaluate(()=>({stored:localStorage.getItem('hermes.desktop.autoSpeakReplies'),backend:window.fixtureConfig?.voice?.auto_tts ?? false,speechSockets:window.fixtureCalls.filter(c=>c.method==='socket.open'&&c.params.url.includes('speak-stream')).length,evidence:window.audioEvidence}))
 expect(result).toMatchObject({stored:'true',speechSockets:1})
 const speechCalls=await page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='socket.open'&&c.params.url.includes('speak-stream')))
 expect(new URL(speechCalls[0].params.url).searchParams.get('profile')).toBe('codex')
 expect(await page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='api'&&c.params.method==='PUT'&&c.params.path.startsWith('/api/config')).length)).toBe(0)
 await page.getByRole('button',{name:'Dismiss notification',exact:true}).click()
 await page.locator('.phone-audio-diagnostics summary').click()
 await expect(page.getByText(/Speech stream ended/)).toBeVisible()
 await page.getByRole('textbox',{name:'Message',exact:true}).fill('TEST FIXTURE error turn')
 await page.getByRole('button',{name:'Send',exact:true}).click()
 await page.evaluate(()=>{window.fixtureAudioMode='fallback';window.fixtureReply('TEST FIXTURE synthesis failure reply')})
 await expect(page.getByText('TEST FIXTURE synthesis unavailable',{exact:true})).toBeVisible()
 await expect(page.getByText(/Read-aloud failed; see error notification/)).toBeVisible()
 const rest=await page.evaluate(()=>window.fixtureCalls.find(c=>c.method==='api'&&c.params.path==='/api/audio/speak'))
 expect(rest.params).toMatchObject({connectionId:'android-remote',profile:'codex',method:'POST',body:{text:'TEST FIXTURE synthesis failure reply'}})
 await page.getByRole('button',{name:'Dismiss notification',exact:true}).click()
 await page.getByRole('textbox',{name:'Message',exact:true}).fill('TEST FIXTURE REST turn')
 await page.getByRole('button',{name:'Send',exact:true}).click()
 await page.evaluate(()=>{window.fixtureAudioMode='rest';window.fixtureReply('TEST FIXTURE REST playable reply')})
 await expect.poll(()=>page.evaluate(()=>window.audioEvidence.filter(e=>e.event==='html-ended').length)).toBe(1)
 await expect(page.locator('.phone-audio-diagnostics summary')).toContainText('idle')
 await page.evaluate(()=>{location.hash='/settings?tab=config%3Avoice&page=speech'})
 await expect(page.getByRole('switch',{name:'Read Responses Aloud',exact:true})).toBeChecked()
 await page.getByRole('switch',{name:'Read Responses Aloud',exact:true}).click()
 await expect.poll(()=>page.evaluate(()=>localStorage.getItem('hermes.desktop.autoSpeakReplies'))).toBe('false')
 await page.getByRole('button',{name:'Close settings',exact:true}).click()
 await page.getByRole('textbox',{name:'Message',exact:true}).fill('TEST FIXTURE disabled again')
 await page.getByRole('button',{name:'Send',exact:true}).click()
 const before=await page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='socket.open'&&c.params.url.includes('speak-stream')).length)
 await page.evaluate(()=>window.fixtureReply('TEST FIXTURE remains silent when off'))
 await expect(page.getByText('TEST FIXTURE remains silent when off',{exact:true})).toBeVisible()
 await page.waitForTimeout(250)
 expect(await page.evaluate(()=>window.fixtureCalls.filter(c=>c.method==='socket.open'&&c.params.url.includes('speak-stream')).length)).toBe(before)
 expect(errors).toEqual([])
 const evidence=await page.evaluate(()=>window.audioEvidence)
 expect(evidence.some(e=>e.event==='source-ended'&&e.state==='running'&&e.duration===0.2&&e.sampleRate===24000)).toBe(true)
 expect(evidence.some(e=>e.event==='html-ended'&&e.duration===0.2)).toBe(true)
 expect(directBlocked>0).toBe(process.env.DIRECT==='1')
 await writeFile(`artifacts/auto-speech-fixed${process.env.DIRECT==='1'?'-direct-blocked':''}.json`,JSON.stringify({fixtureOnly:true,productionBackendContacted:false,result,rest,evidence,errors,directBlocked},null,2));console.log('VERIFIED',JSON.stringify({result,evidence,errors,directBlocked}))
 await page.screenshot({path:'artifacts/auto-speech-settings.png'})
 await writeFile('artifacts/auto-speech-exploration.json',JSON.stringify({errors,calls:await page.evaluate(()=>window.fixtureCalls)},null,2))
}finally{console.log('FINAL',errors,await page.locator('body').innerText());await writeFile('artifacts/auto-speech-exploration.json',JSON.stringify({errors,calls:await page.evaluate(()=>window.fixtureCalls)},null,2));await browser.close();server.close()}
