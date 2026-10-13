package com.hermes.privateapp;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;
/** Real native auth, socket transport, production dispatcher/attach handlers;
 * disposable server and generated documents, no agent or model. */
public class AttachmentGatewayTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 @Test public void authenticatedExistingRpcStagesBytesOnlyInOwningSession()throws Exception {
  Assume.assumeNotNull(System.getenv("ANDROID_ATTACHMENT_TEST"));
  GatewayAuthTest fixture=new GatewayAuthTest();fixture.setup();
  OkHttpClient client=new OkHttpClient();WebSocket socket=null;
  try(AttachmentStore store=new AttachmentStore(temp.getRoot())) {
   Method login=GatewayAuthTest.class.getDeclaredMethod("login");login.setAccessible(true);login.invoke(fixture);
   Field f=GatewayAuthTest.class.getDeclaredField("auth");f.setAccessible(true);GatewayAuth auth=(GatewayAuth)f.get(fixture);
   String base=System.getenv("ANDROID_AUTH_TEST_URL");
   BlockingQueue<JSONObject> replies=new LinkedBlockingQueue<>();CountDownLatch opened=new CountDownLatch(1);
   socket=client.newWebSocket(new Request.Builder().url(auth.socketUrl(base.replaceFirst("http","ws")+"/api/ws?profile=owner-a")).build(),new WebSocketListener(){
    public void onOpen(WebSocket w,Response r){opened.countDown();}
    public void onMessage(WebSocket w,String text){try{JSONObject j=new JSONObject(text);if(j.has("id"))replies.add(j);}catch(Exception e){throw new RuntimeException(e);}}
   });
   assertTrue(opened.await(10,TimeUnit.SECONDS));
   String handle=store.stage(new ByteArrayInputStream("SYNTHETIC ATTACHMENT".getBytes()),"notes.txt","text/plain","owner-a",auth,()->true);
   assertThrows(IOException.class,()->store.read(handle,"owner-b",auth,()->true));
   JSONObject params=new JSONObject().put("session_id","fixture-a").put("name","notes.txt").put("path","").put("data_url",store.read(handle,"owner-a",auth,()->true));
   socket.send(new JSONObject().put("jsonrpc","2.0").put("id",1).put("method","file.attach").put("params",params).toString());
   JSONObject response=replies.poll(15,TimeUnit.SECONDS);assertNotNull(response);assertFalse(response.toString(),response.has("error"));assertTrue(response.getJSONObject("result").getBoolean("attached"));assertTrue(response.getJSONObject("result").getBoolean("uploaded"));
   params.put("session_id","missing-session");socket.send(new JSONObject().put("jsonrpc","2.0").put("id",2).put("method","file.attach").put("params",params).toString());
   assertTrue(replies.poll(15,TimeUnit.SECONDS).has("error"));
   String png="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN1sAAAAASUVORK5CYII=";
   socket.send(new JSONObject().put("jsonrpc","2.0").put("id",3).put("method","image.attach_bytes").put("params",new JSONObject().put("session_id","fixture-a").put("filename","pixel.png").put("content_base64",png)).toString());
   JSONObject image=replies.poll(15,TimeUnit.SECONDS);assertNotNull(image);assertFalse(image.toString(),image.has("error"));assertTrue(image.getJSONObject("result").getBoolean("attached"));
  }finally{if(socket!=null)socket.cancel();client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();fixture.teardown();}
 }
}
