// Labeled synthetic protocol fixtures ONLY. Never connects to a real gateway.
export function installPhoneFixture() {
 window.fixtureCalls=[]
 const emit=m=>setTimeout(()=>window.HermesNative.onmessage?.({data:JSON.stringify(m)}),0)
 const row={id:'fixture-history',title:'TEST FIXTURE — shared history',source:'cli',profile:'codex',message_count:2,started_at:1780000000,last_active:1780000001,ended_at:null,input_tokens:0,output_tokens:0,tool_call_count:0,is_active:false,model:'test-model',preview:'TEST FIXTURE history preview'}
 window.HermesNative={onmessage:null,postMessage(raw){
  const c=JSON.parse(raw), p=c.params||{};window.fixtureCalls.push(c)
  if(c.method==='api' && p.path.startsWith('/api/profiles/sessions/sidebar') && window.fixtureFailHistory){emit({id:c.id,error:'TEST FIXTURE history unavailable'});return}
  let result=true
  if(c.method==='connection')result={baseUrl:'https://fixture.test.ts.net',token:'',wsUrl:`wss://fixture.test.ts.net/api/ws?profile=${p.profile||'codex'}`,profile:p.profile||'codex',mode:'remote',authMode:'oauth',connectionId:'android-remote',registryScoped:true,sharedRemote:true,isFullscreen:false,nativeOverlayWidth:0,windowButtonPosition:null,logs:[]}
  if(c.method==='api') {
   const path=p.path.split('?')[0]
   result=path==='/api/profiles'?{profiles:[{name:'codex',is_active:true}]}:path==='/api/status'?{status:'ok',model:'test-model',provider:'test',profile:'codex'}:path.includes('config')?{model:{default:'test-model',provider:'custom'},display:{language:'en'},onboarding:{completed:true}}:path.includes('sessions')?{sessions:[row],total:1}:{}
  }
  if(c.method==='api' && p.path.startsWith('/api/profiles/sessions/sidebar')) result={recents:{sessions:[row],total:1,has_more:false},cron:{sessions:[]},messaging:{sessions:[]}}
  if(c.method==='api' && p.path.startsWith('/api/model/options')) result={providers:[],models:[]}
  if(c.method==='api' && p.path.startsWith('/api/model/info')) result={model:'test-model',provider:'custom'}
  if(c.method==='api' && p.path.startsWith('/api/tools/terminal/backends')) result={backends:[]}
  if(c.method==='socket.open')emit({event:'open',socket:p.id})
  if(c.method==='socket.send') {
   const q=JSON.parse(p.data);window.fixtureCalls.push({rpc:q})
   let r={}
   if(q.method==='setup.status')r={provider_configured:true}
   if(q.method==='setup.runtime_check')r={ok:true,provider:'custom'}
   if(q.method==='model.options')r={providers:[],models:[]}
   if(q.method==='session.control.read')r={control:{goal:null,loop:null,heartbeat:null,revision:'fixture',updated_at:1780000000}}
   if(q.method==='session.list')r={sessions:[row],total:1,has_more:false}
   if(q.method==='profiles.list')r={profiles:[{name:'codex'}]}
   if(q.method==='status.get')r={model:'test-model',provider:'test',session_id:null}
   if(q.method==='session.create')r={session_id:'fixture-new',stored_session_id:'fixture-new',messages:[],info:{model:'test-model',provider:'custom',cwd:'',started_at:1780000000}}
   if(q.method==='session.resume')r={session_id:'fixture-runtime',stored_session_id:'fixture-history',messages:[{role:'user',content:'TEST FIXTURE historical question'},{role:'assistant',content:'TEST FIXTURE historical answer'}]}
   if(q.method==='session.history')r={messages:[{role:'user',content:'TEST FIXTURE historical question'},{role:'assistant',content:'TEST FIXTURE historical answer'}]}
   if(q.method==='commands.catalog')r={commands:[]}
   if(q.method==='model.list')r={models:[]}
   emit({event:'message',socket:p.id,data:JSON.stringify({jsonrpc:'2.0',id:q.id,result:r})})
  }
  if(c.method==='socket.close')emit({event:'close',socket:p.id,code:1000})
  emit({id:c.id,result})
 }}
}
