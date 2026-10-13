import {createServer} from 'node:http';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {chromium,expect} from '@playwright/test';
import WebSocket from 'ws';
const base='http://100.73.128.120:9119';
const env=Object.fromEntries((await readFile('/home/will/.hermes/.env','utf8')).split('\n').filter(x=>/^[A-Z_]+=/.test(x)).map(x=>{const i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1).replace(/^(['"])(.*)\1$/,'$2')]}));
const login=await fetch(base+'/auth/password-login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({provider:'basic',username:env.HERMES_DASHBOARD_BASIC_AUTH_USERNAME,password:env.HERMES_DASHBOARD_BASIC_AUTH_PASSWORD})});
if(!login.ok)throw Error('Login failed '+login.status);
const cookie=login.headers.getSetCookie().map(x=>x.split(';')[0]).join('; ');
async function api(path,method='GET',body){const r=await fetch(base+path,{method,headers:{cookie,...(body?{'content-type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});if(!r.ok)throw Error('API '+r.status+' '+path.split('?')[0]);return r.json()}
const root=resolve(process.env.RENDERER_ROOT||'pub38/android/app/src/main/assets');
const server=createServer(async(req,res)=>{try{const path=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!path.startsWith(root+'/'))throw Error();res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css'}[extname(path)]||'application/octet-stream'));res.end(await readFile(path))}catch{res.statusCode=404;res.end()}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']});
const page=await browser.newPage({viewport:{width:393,height:852},isMobile:true,hasTouch:true});const sockets=new Map(),calls=[],errors=[];page.on('pageerror',e=>errors.push(e.message));
const emit=m=>page.evaluate(m=>window.HermesNative.onmessage?.({data:JSON.stringify(m)}),m).catch(()=>{});
await page.exposeBinding('nativeCall',async(_,raw)=>{const c=JSON.parse(raw),p=c.params||{};try{let result=true;
if(c.method==='connection'){const profile=p.profile||'default';result={baseUrl:base,token:'',wsUrl:base.replace('http','ws')+'/api/ws?profile='+encodeURIComponent(profile),profile,mode:'remote',authMode:'oauth',connectionId:'android-remote',registryScoped:true,sharedRemote:true,isFullscreen:false,nativeOverlayWidth:0,windowButtonPosition:null,logs:[]}}
if(c.method==='api')result=await api(p.path,p.method||'GET',p.body);
if(c.method==='socket.open'){const u=new URL(p.url),t=await api('/api/auth/ws-ticket','POST');u.searchParams.set('ticket',t.ticket);const s=new WebSocket(u,{origin:base});sockets.set(p.id,s);s.on('open',()=>emit({event:'open',socket:p.id}));s.on('message',d=>emit({event:'message',socket:p.id,data:d.toString()}));s.on('close',code=>emit({event:'close',socket:p.id,code}));s.on('error',()=>emit({event:'error',socket:p.id}));}
if(c.method==='socket.send'){const q=JSON.parse(p.data);calls.push({method:q.method,profile:q.params?.profile,session_id:q.params?.session_id});if(q.method==='prompt.submit'&&!process.env.ALLOW_SUBMIT)throw Error('Live prompt disabled');sockets.get(p.id).send(p.data)}
if(c.method==='socket.close')sockets.get(p.id)?.close();
await emit({id:c.id,result});}catch(e){await emit({id:c.id,error:e.message})}});
await page.addInitScript(()=>window.HermesNative={onmessage:null,postMessage:raw=>void window.nativeCall(raw)});
try{await page.goto('http://127.0.0.1:'+server.address().port);await expect(page.getByRole('combobox',{name:'Select bot'})).toBeEnabled({timeout:45000});const options=await page.getByRole('combobox',{name:'Select bot'}).locator('option').evaluateAll(xs=>xs.map(x=>({value:x.value,label:x.textContent})));console.log('Roster',JSON.stringify(options));const ari=options.find(x=>x.label===(process.env.TARGET_LABEL||'Ari'));if(!ari)throw Error('Missing requested bot');await page.getByRole('combobox',{name:'Select bot'}).selectOption(ari.value);await expect.poll(()=>page.getByRole('combobox',{name:'Select bot'}).inputValue(),{timeout:45000}).toBe(ari.value);await page.waitForTimeout(1500);
console.log('before',await page.locator('.phone-connection').innerText());await page.getByRole('button',{name:'New chat',exact:true}).click();await page.waitForTimeout(1200);console.log('after',await page.locator('.phone-connection').innerText(), 'selected',await page.getByRole('combobox',{name:'Select bot'}).inputValue());
if(process.env.ALLOW_SUBMIT){await page.getByRole('button',{name:'New chat',exact:true}).click();await page.getByRole('textbox',{name:'Message',exact:true}).fill('Diagnostic new-chat routing check. Reply exactly ARI_NEWCHAT_ROUTE_OK. Do not use tools.');await page.getByRole('button',{name:'Send',exact:true}).click();await expect.poll(()=>calls.some(x=>x.method==='prompt.submit'),{timeout:45000}).toBe(true);await expect(page.getByText('ARI_NEWCHAT_ROUTE_OK',{exact:true})).toBeVisible({timeout:120000})}
if(!process.env.EXPECT_BROKEN)expect(await page.getByRole('combobox',{name:'Select bot'}).inputValue()).toBe(ari.value);
const report={liveGateway:true,packagedRenderer:true,targetLabel:process.env.TARGET_LABEL||'Ari',submitted:!!process.env.ALLOW_SUBMIT,options,beforeAndAfterCalls:calls,errors};await writeFile(process.env.RESULT_PATH||'/home/will/.openclaw/workspace/archive/reports/hermes-android-newchat-followup-20261002/live.json',JSON.stringify(report,null,2));console.log('routing',JSON.stringify(calls.filter(x=>['session.resume','session.create','prompt.submit'].includes(x.method))));console.log('errors',JSON.stringify(errors));
}finally{for(const s of sockets.values())s.close();await browser.close();server.close()}
