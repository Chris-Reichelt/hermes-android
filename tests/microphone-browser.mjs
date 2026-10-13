// Linux Chromium control ONLY: synthetic audio, actual recorder hook, no gateway.
import {build} from 'esbuild'
import {createServer} from 'node:http'
import {writeFile} from 'node:fs/promises'
import {resolve} from 'node:path'
import {chromium,expect} from '@playwright/test'
const compiled=await build({stdin:{contents:`
import React from 'react'; import {createRoot} from 'react-dom/client';
import {useMicRecorder} from './apps/desktop/src/app/chat/composer/hooks/use-mic-recorder';
const copy={microphoneAccessDenied:'denied',microphoneConstraintsUnsupported:'constraints',microphoneInUse:'in use',microphonePermissionDenied:'permission',microphoneStartFailed:'start failed',microphoneUnsupported:'unsupported',noMicrophone:'no mic'};
function Probe(){window.mic=useMicRecorder(copy).handle;return null}
createRoot(document.getElementById('root')).render(React.createElement(Probe));
`,resolveDir:process.cwd(),loader:'tsx'},bundle:true,write:false,alias:{'@':resolve('apps/desktop/src')},format:'iife'})
const server=createServer((req,res)=>{res.setHeader('Content-Type','text/html');res.end(`<div id="root"></div><script>${compiled.outputFiles[0].text}</script>`)})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox','--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream','--autoplay-policy=no-user-gesture-required']})
try{
 const page=await browser.newPage();await page.goto(`http://127.0.0.1:${server.address().port}`)
 await page.waitForFunction(()=>window.mic)
 const results=await page.evaluate(async()=>{
  const takes=[];const tracks=[];const gum=navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
  navigator.mediaDevices.getUserMedia=async constraints=>{const s=await gum(constraints);tracks.push(...s.getTracks());return s}
  for(let cycle=0;cycle<5;cycle++){
   await window.mic.start();await new Promise(r=>setTimeout(r,1200));const recording=await window.mic.stop();
   const context=new AudioContext();const decoded=await context.decodeAudioData(await recording.audio.arrayBuffer());
   const url=URL.createObjectURL(recording.audio);const audio=new Audio(url);const finished=new Promise((resolve,reject)=>{audio.onended=resolve;audio.onerror=reject;setTimeout(()=>reject(Error('playback timeout')),5000)});await audio.play();await finished;URL.revokeObjectURL(url);
   let peak=0;for(const value of decoded.getChannelData(0))peak=Math.max(peak,Math.abs(value));
   takes.push({cycle,bytes:recording.audio.size,mime:recording.audio.type,decodedFrames:decoded.length,decodedDuration:decoded.duration,peak,playbackEnded:audio.ended,ended:tracks.every(t=>t.readyState==='ended')});await context.close();
  }
  await window.mic.start();window.mic.cancel();const cancelledReleased=tracks.every(t=>t.readyState==='ended');
  await window.mic.start();await new Promise(r=>setTimeout(r,250));const afterCancel=await window.mic.stop();
  return {environment:'Linux Chromium synthetic microphone; NOT Android/phone',takes,cancelledReleased,restartAfterCancelBytes:afterCancel.audio.size,allTracksEnded:tracks.every(t=>t.readyState==='ended')}
 })
 for(const t of results.takes){expect(t.bytes).toBeGreaterThan(0);expect(t.decodedFrames).toBeGreaterThan(0);expect(t.peak).toBeGreaterThan(0);expect(t.playbackEnded).toBe(true);expect(t.ended).toBe(true)}
 expect(results.cancelledReleased).toBe(true);expect(results.allTracksEnded).toBe(true);expect(results.restartAfterCancelBytes).toBeGreaterThan(0)
 await writeFile('artifacts/microphone-browser-control.json',JSON.stringify(results,null,2)+'\n');console.log(JSON.stringify(results,null,2))
}finally{await browser.close();server.close()}
