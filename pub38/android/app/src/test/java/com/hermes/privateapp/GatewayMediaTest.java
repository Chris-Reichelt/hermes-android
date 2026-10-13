package com.hermes.privateapp;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import okhttp3.*;
import org.json.JSONObject;

/** Real authenticated HTTP byte transport, using a generated synthetic sine-wave fixture. */
public class GatewayMediaTest {
 ServerSocket server;GatewayAuth auth;String base;byte[] clip;volatile Throwable serverError;volatile String target;int hits;
 @Before public void setup()throws Exception {
  clip=Files.readAllBytes(Path.of("../../tests/fixtures/tone.mp3"));
  server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));base="http://127.0.0.1:"+server.getLocalPort();
  Thread t=new Thread(()->{while(!server.isClosed())try(Socket s=server.accept()){
   BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream()));String first=in.readLine(),line,bearer="";
   while((line=in.readLine())!=null&&!line.isEmpty())if(line.startsWith("Authorization: "))bearer=line.substring(15);
   byte[] bytes=clip;long length=bytes.length;int status=200;String extra="";
   if(first.contains("/api/status")){bytes="{\"auth_required\":false}".getBytes();length=bytes.length;}
   else{hits++;target=first.split(" ")[1];assertEquals("Bearer fixture-media-token",bearer);
    if(first.contains("oversize"))length=GatewayMedia.MAX_BYTES+1L;
    if(first.contains("empty"))length=0;
    if(first.contains("redirect")){status=302;length=0;extra="Location: "+base+"/sink\r\n";}
    if(first.contains("missing")){status=404;length=0;}
   }
   OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 "+status+" Fixture\r\n"+extra+"Content-Type: audio/mpeg\r\nContent-Length: "+length+"\r\nConnection: close\r\n\r\n").getBytes());if(length==bytes.length)out.write(bytes);out.flush();
  }catch(Throwable e){if(!server.isClosed())serverError=e;}});t.setDaemon(true);t.start();
  auth=new GatewayAuth(base,new OkHttpClient());auth.useLegacy("fixture-media-token");
 }
 @After public void cleanup()throws Exception{auth.cancel();server.close();if(serverError!=null)throw new AssertionError(serverError);}
 JSONObject params(String path)throws Exception{return new JSONObject().put("connectionId","android-remote").put("path",path).put("sessionId","owned session");}
 @Test public void exactClipBytesAndOwnerContract()throws Exception {
  JSONObject result=GatewayMedia.read(auth,base,params("/synthetic/fixture + &.mp3"),"ari + &",()->true);
  assertArrayEquals(clip,Base64.getDecoder().decode(result.getString("base64")));
  assertEquals(1,result.length());HttpUrl u=HttpUrl.get(base+target);
  assertEquals("/api/fs/download",u.encodedPath());assertEquals("/synthetic/fixture + &.mp3",u.queryParameter("path"));assertEquals("ari + &",u.queryParameter("profile"));assertEquals("owned session",u.queryParameter("session_id"));assertNull(u.queryParameter("token"));
 }
 @Test public void boundsLengthAndEmpty()throws Exception {for(String path:new String[]{"oversize","empty"})assertThrows(IOException.class,()->GatewayMedia.read(auth,base,params(path),"q",()->true));}
 @Test public void rejectsRedirectAndMissing()throws Exception {for(String path:new String[]{"redirect","missing"})assertThrows(GatewayAuth.AuthException.class,()->GatewayMedia.read(auth,base,params(path),"q",()->true));assertEquals(2,hits);}
 @Test public void rejectsWrongOwnerAndStaleGeneration()throws Exception {assertThrows(IllegalArgumentException.class,()->GatewayMedia.read(auth,base,params("clip").put("connectionId","other"),"q",()->true));assertThrows(IOException.class,()->GatewayMedia.read(auth,base,params("clip"),"q",()->false));assertEquals(0,hits);}
}
