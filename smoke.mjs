import {createServer} from 'node:http'
import {readFile} from 'node:fs/promises'
import {extname,resolve} from 'node:path'
import {chromium} from '@playwright/test'
const root=resolve('apps/desktop/dist')
const server=createServer(async(req,res)=>{
 try{const path=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!path.startsWith(root+'/'))throw Error();const data=await readFile(path);res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml'}[extname(path)]||'application/octet-stream'));res.end(data)}catch{res.statusCode=404;res.end()}
})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']})
const page=await browser.newPage({viewport:{width:412,height:915}})
const errors=[];page.on('pageerror',e=>errors.push(e.message))
await page.addInitScript(()=>{
 window.HermesNative={onmessage:null,postMessage(raw){
  const c=JSON.parse(raw)
  const result=c.method==='connection'?{baseUrl:'https://offline.test.ts.net',token:'',wsUrl:'wss://offline.test.ts.net/api/ws',profile:'default',mode:'remote',authMode:'token',connectionId:'android-remote',registryScoped:true,sharedRemote:true,isFullscreen:false,nativeOverlayWidth:0,windowButtonPosition:null,logs:[]}:true
  setTimeout(()=>window.HermesNative.onmessage({data:JSON.stringify({id:c.id,...(c.method==='api'||c.method==='socket.open'?{error:'Offline test: no backend connection attempted'}:{result})})}),0)
 }}
})
try{
 await page.goto(`http://127.0.0.1:${server.address().port}`)
 await page.waitForTimeout(9000)
 const text=await page.locator('body').innerText()
 console.log(JSON.stringify({errors,body:text.slice(0,4000)},null,2))
 await page.screenshot({path:'offline-phone.png',fullPage:true})
 if(errors.length || !/Starting Hermes|Reconnecting to the remote Hermes backend|Could not connect/.test(text))process.exitCode=1
}finally{await browser.close();server.close()}
