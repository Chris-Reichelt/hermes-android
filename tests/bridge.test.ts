import {afterEach,describe,expect,it} from 'vitest'
import {createNativeRpc,installAndroidBridge,type NativePort} from '../apps/desktop/src/android/bridge'
import {JsonRpcGatewayClient} from '../apps/shared/src/json-rpc-gateway'
import {WebSocket as NodeSocket,WebSocketServer} from 'ws'
const original = window.WebSocket
const resources: (()=>void)[]=[]
afterEach(()=>{window.WebSocket=original;for(const cleanup of resources.splice(0))cleanup()})
describe('Android adapter contracts',()=>{
 it('reports native sign-in without exposing credentials or claiming a legacy token',async()=>{
  const port:NativePort={onmessage:null,postMessage(raw){const c=JSON.parse(raw);queueMicrotask(()=>port.onmessage!({data:JSON.stringify({id:c.id,result:{baseUrl:'http://100.73.128.120:9119',authMode:'oauth',token:'',profile:c.params.profile||'codex',wsUrl:'ws://100.73.128.120:9119/api/ws?profile=codex'}})}))}}
  const bridge=installAndroidBridge(port)
  expect((await bridge.connections.list()).connections[0]).toMatchObject({authMode:'oauth',tokenSet:false,tokenPreview:null})
  expect((await bridge.getConnectionConfig()).remote).toMatchObject({authMode:'oauth',tokenSet:false})
  expect((await bridge.getGatewayWsUrl('codex')).wsUrl).not.toMatch(/token=|ticket=/)
  expect((await bridge.getVersion()).version).toBe('0.3.5-android')
 })
 it('saves owner-pinned files and returns cancellation without rejection',async()=>{
  const calls:any[]=[]
  const port:NativePort={onmessage:null,postMessage(raw){const c=JSON.parse(raw);calls.push(c);queueMicrotask(()=>port.onmessage!({data:JSON.stringify({id:c.id,result:c.method==='download.image'?false:{saved:false,canceled:true}})}))}}
  const bridge=installAndroidBridge(port)
  const payload={connectionId:'android-remote',profile:'file-owner',sessionId:'fixture-session',path:'/fixture/a b.apk',suggestedName:'a b.apk'}
  expect(await bridge.saveGatewayFile(payload)).toEqual({saved:false,canceled:true})
  expect(calls[0]).toMatchObject({method:'download.file',params:payload})
  expect(await bridge.saveImageFromUrl('data:image/png;base64,AA==')).toBe(false)
  expect(calls[1].method).toBe('download.image')
 })
 it('correlates replies out of order and propagates native failures',async()=>{
  const calls:any[]=[]
  const port:NativePort={onmessage:null,postMessage(data){calls.push(JSON.parse(data))}}
  const rpc=createNativeRpc(port)
  const first=rpc.request('one'),second=rpc.request('two')
  port.onmessage!({data:JSON.stringify({id:calls[1].id,result:'two'})})
  port.onmessage!({data:JSON.stringify({id:calls[0].id,result:'one'})})
  expect(await first).toBe('one');expect(await second).toBe('two')
  const rejected=rpc.request('three');port.onmessage!({data:JSON.stringify({id:calls[2].id,error:'refused'})});await expect(rejected).rejects.toThrow('refused')
 })
 it('preserves profile routing and refuses unknown connection IDs instead of falling back',async()=>{
  const requests:any[]=[]
  const port:NativePort={onmessage:null,postMessage(data){const call=JSON.parse(data);requests.push(call);queueMicrotask(()=>port.onmessage!({data:JSON.stringify({id:call.id,result:{profile:call.params.profile,wsUrl:'wss://private.test.ts.net/api/ws'}})}))}}
  const bridge=installAndroidBridge(port)
  expect((await bridge.getConnectionFor({connectionId:'android-remote',profile:'ari'})).profile).toBe('ari')
  expect((await bridge.getConnectionFor({connectionId:'android-remote',profile:'vee'})).profile).toBe('vee')
  await expect(bridge.getConnectionFor({connectionId:'unknown',profile:'q'})).rejects.toThrow('refusing to retarget')
  expect(requests).toHaveLength(2)
  // `selectPaths` is the native SAF picker now (apk39), not the old
  // 'Not available' stub: it routes an `attachment.pick` through the bridge and
  // returns [] when the picker yields no handles.
  expect(await bridge.selectPaths()).toEqual([])
  expect(requests[2]).toMatchObject({method:'attachment.pick'})
 })
 it('reports native socket-open rejection to property handlers used by speech',async()=>{
  const port:NativePort={onmessage:null,postMessage(raw){const c=JSON.parse(raw);queueMicrotask(()=>port.onmessage!({data:JSON.stringify({id:c.id,error:'TEST FIXTURE open refused'})}))}}
  installAndroidBridge(port)
  const ws=new window.WebSocket('wss://private.test.ts.net/api/audio/speak-stream')
  let propertyErrors=0,listenerErrors=0,propertyCloses=0
  ws.onerror=()=>{propertyErrors++};ws.onclose=()=>{propertyCloses++}
  ws.addEventListener('error',()=>{listenerErrors++})
  await new Promise(resolve=>setTimeout(resolve,20))
  expect({propertyErrors,listenerErrors,propertyCloses,state:ws.readyState}).toEqual({propertyErrors:1,listenerErrors:1,propertyCloses:1,state:WebSocket.CLOSED})
 })
 it('delivers native binary PCM frames as ArrayBuffers without changing bytes',async()=>{
  const port:NativePort={onmessage:null,postMessage(raw){const c=JSON.parse(raw);queueMicrotask(()=>port.onmessage!({data:JSON.stringify({id:c.id,result:true})}))}}
  installAndroidBridge(port)
  const ws=new window.WebSocket('wss://private.test.ts.net/api/audio/speak-stream')
  ws.binaryType='arraybuffer'
  const frame=new Promise<MessageEvent>(resolve=>ws.addEventListener('message',resolve,{once:true}))
  port.onmessage!({data:JSON.stringify({socket:1,event:'message',binary:true,data:btoa(String.fromCharCode(0,127,128,255))})})
  expect(Array.from(new Uint8Array((await frame).data))).toEqual([0,127,128,255])
  port.onmessage!({data:JSON.stringify({socket:1,event:'close',code:1000})})
 })
 it('runs the unchanged shared JSON-RPC client across a real loopback WebSocket through the port adapter',async()=>{
  // Protocol-only fixture, NOT a simulated Hermes chat/backend or live account.
  const server=new WebSocketServer({host:'127.0.0.1',port:0})
  await new Promise<void>(resolve=>server.once('listening',resolve))
  const sockets=new Map<number,NodeSocket>()
  resources.push(()=>{for(const s of sockets.values())s.terminate();server.close()})
  server.on('connection',ws=>ws.on('message',raw=>{const request=JSON.parse(raw.toString());ws.send(JSON.stringify({jsonrpc:'2.0',id:request.id,result:{echo:request.params,method:request.method}}))}))
  const port:NativePort={onmessage:null,postMessage(data){
   const c=JSON.parse(data),p=c.params
   const respond=(message:any)=>queueMicrotask(()=>port.onmessage!({data:JSON.stringify(message)}))
   if(c.method==='socket.open'){
    const ws=new NodeSocket(p.url);sockets.set(p.id,ws)
    ws.on('open',()=>respond({event:'open',socket:p.id}))
    ws.on('message',raw=>respond({event:'message',socket:p.id,data:raw.toString()}))
    ws.on('close',code=>respond({event:'close',socket:p.id,code}))
   }else if(c.method==='socket.send')sockets.get(p.id)!.send(p.data)
   else if(c.method==='socket.close')sockets.get(p.id)?.close()
   respond({id:c.id,result:true})
  }}
  installAndroidBridge(port)
  const gateway=new JsonRpcGatewayClient({requestTimeoutMs:2000})
  resources.push(()=>gateway.close())
  await gateway.connect(`ws://127.0.0.1:${(server.address() as any).port}/api/ws`)
  expect(await gateway.request('transport.probe',{profile:'ari',session_id:'test-session'})).toEqual({method:'transport.probe',echo:{profile:'ari',session_id:'test-session'}})
  gateway.close()
 })
})
