// Production APK renderer; synthetic protocol only, no live messages.
import {createServer} from 'node:http'
import {readFile,writeFile,readdir} from 'node:fs/promises'
import {extname,resolve} from 'node:path'
import {chromium,expect} from '@playwright/test'
import {installPhoneFixture} from './phone-fixture.mjs'
const root=resolve(process.env.RENDERER_ROOT||'pub38/android/app/src/main/assets')
const server=createServer(async(req,res)=>{try{const path=resolve(root,'.'+(req.url==='/'?'/index.html':req.url.split('?')[0]));if(!path.startsWith(root+'/'))throw Error();res.setHeader('content-type',({'.html':'text/html','.js':'text/javascript','.css':'text/css'}[extname(path)]||'application/octet-stream'));res.end(await readFile(path))}catch{res.statusCode=404;res.end()}})
await new Promise(r=>server.listen(0,'127.0.0.1',r))
const browser=await chromium.launch({headless:true,executablePath:'/usr/bin/google-chrome',args:['--no-sandbox']})
const origin=`http://127.0.0.1:${server.address().port}`,results=[]
const files=await readdir(root+'/assets')
const profileModule=files.find(f=>f.startsWith('profile-')&&f==='profile-Ct-7qZNE.js')
try{
 for(const bot of ['vee','ari','q']){
  const page=await browser.newPage({viewport:{width:393,height:852},isMobile:true,hasTouch:true})
  const errors=[];page.on('pageerror',e=>errors.push(e.message))
  await page.route('**/*',route=>route.request().url().startsWith(origin)?route.continue():route.abort())
  await page.addInitScript({content:'window.fixtureHistoryProfile='+JSON.stringify(bot)+';('+installPhoneFixture.toString().replaceAll('codex','q').replaceAll("[{name:'q',is_active:true}]","[{name:'q',is_active:true},{name:'vee'},{name:'ari'}]").replaceAll("[{name:'q'}]","[{name:'q'},{name:'vee'},{name:'ari'}]").replaceAll("profile:'q'","profile:(window.fixtureHistoryProfile||'q')")+')()'})
  await page.goto(origin)
  await expect(page.locator('.phone-connection')).toHaveText('q · Connected',{timeout:20000})
  await page.getByRole('combobox',{name:'Select bot'}).selectOption(bot)
  await expect(page.locator('.phone-connection')).toHaveText(`${bot} · Connected`,{timeout:15000})
  await page.getByRole('button',{name:'Sessions',exact:true}).click()
  await page.locator('#phone-sessions').getByText('TEST FIXTURE — shared history',{exact:true}).click()
  await expect(page.getByText('TEST FIXTURE historical answer',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'New chat',exact:true}).click()
  await page.getByRole('button',{name:'New chat',exact:true}).click()
  await expect(page.locator('.phone-connection')).toHaveText(`${bot} · Connected`,{timeout:5000})
  await page.getByRole('textbox',{name:'Message',exact:true}).fill('TEST FIXTURE current bot')
  await page.getByRole('button',{name:'Send',exact:true}).click()
  await expect.poll(()=>page.evaluate(()=>window.fixtureCalls.filter(c=>c.rpc?.method==='prompt.submit').length)).toBe(1)
  await expect(page.getByText('TEST FIXTURE historical answer',{exact:true})).toBeHidden()
  const calls=await page.evaluate(()=>window.fixtureCalls)
  const create=calls.findLast(c=>c.method==='socket.send'&&JSON.parse(c.params.data).method==='session.create')
  const send=calls.find(c=>c.method==='socket.send'&&JSON.parse(c.params.data).method==='prompt.submit')
  await writeFile('/home/will/.openclaw/workspace/archive/reports/hermes-android-newchat-20261002/fixture-calls.json',JSON.stringify(calls,null,2))
  const sockets=new Map(calls.filter(c=>c.method==='socket.open').map(c=>[c.params.id,c.params.url]))
  expect(JSON.parse(create.params.data).params.profile || new URL(sockets.get(create.params.id)).searchParams.get('profile')).toBe(bot)
  expect(JSON.parse(send.params.data).params.profile || new URL(sockets.get(send.params.id)).searchParams.get('profile')).toBe(bot)
  expect(errors).toEqual([])
  results.push({bot,createProfile:bot,submitProfile:bot,pageErrors:errors})
  await page.close()
 }
 await writeFile(process.env.RESULT_PATH||'artifacts/new-chat-current-bot.json',JSON.stringify({passed:true,fixtureOnly:true,root,results},null,2))
 console.log(JSON.stringify({passed:true,results}))
}finally{await browser.close();server.close()}
