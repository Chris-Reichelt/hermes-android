package com.hermes.privateapp;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.JSONObject;

/** Disposable byte fixture, NOT production chat or production file access. */
public class GatewayDownloadTest {
 ServerSocket server;GatewayAuth auth;String base;AtomicInteger hits=new AtomicInteger();volatile Throwable serverError;
 @Before public void setup()throws Exception {
  server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));base="http://127.0.0.1:"+server.getLocalPort();
  Thread worker=new Thread(()->{while(!server.isClosed())try(Socket s=server.accept()){
   BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream()));String first=in.readLine(),line;String bearer="";
   while((line=in.readLine())!=null&&!line.isEmpty())if(line.startsWith("Authorization: "))bearer=line.substring(15);
   byte[] bytes=new byte[]{0,1,2,127,-128,-1};int status=200;String extra="";long length=bytes.length;
   if(first.contains("/api/status")){bytes="{\"auth_required\":false}".getBytes();length=bytes.length;}
   else {hits.incrementAndGet();assertEquals("Bearer fixture-token",bearer);
    if(first.contains("redirect")){status=302;extra="Location: "+base+"/sink\r\n";length=0;}
    else if(first.contains("missing")){status=404;length=0;}
    else if(first.contains("oversize"))length=GatewayDownload.MAX_BYTES+1;
    else if(first.contains("truncated"))length=bytes.length+8;
   }
   OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 "+status+" Fixture\r\n"+extra+"Content-Length: "+length+"\r\nConnection: close\r\n\r\n").getBytes());if(length==bytes.length)out.write(bytes);out.flush();
  }catch(Throwable e){if(!server.isClosed())serverError=e;}});worker.setDaemon(true);worker.start();
  auth=new GatewayAuth(base,new OkHttpClient());auth.useLegacy("fixture-token");
 }
 @After public void cleanup()throws Exception{auth.cancel();server.close();if(serverError!=null)throw new AssertionError(serverError);}
 JSONObject params(String path)throws Exception{return new JSONObject().put("connectionId","android-remote").put("path",path).put("sessionId","session & space");}
 @Test public void exactContractAndBinaryBytes()throws Exception {
  JSONObject p=params("file:///fixture/a b%+&.apk");HttpUrl u=GatewayDownload.fileUrl(base,p,"owner + &");
  assertEquals("/api/fs/download",u.encodedPath());assertEquals(p.getString("path"),u.queryParameter("path"));assertEquals("owner + &",u.queryParameter("profile"));assertEquals("session & space",u.queryParameter("session_id"));assertNull(u.queryParameter("token"));
  ByteArrayOutputStream out=new ByteArrayOutputStream();GatewayDownload.file(auth,u,out,()->true);assertArrayEquals(new byte[]{0,1,2,127,-128,-1},out.toByteArray());
 }
 @Test public void rejectsWrongOwner()throws Exception {JSONObject p=params("x").put("connectionId","other");assertThrows(IllegalArgumentException.class,()->GatewayDownload.fileUrl(base,p,"demo"));assertEquals(0,hits.get());}
 @Test public void refusesRedirectWithoutFollowingOrLeaking()throws Exception {assertThrows(GatewayAuth.AuthException.class,()->GatewayDownload.file(auth,GatewayDownload.fileUrl(base,params("redirect"),"demo"),new ByteArrayOutputStream(),()->true));assertEquals(1,hits.get());}
 @Test public void reports404()throws Exception {GatewayAuth.AuthException e=assertThrows(GatewayAuth.AuthException.class,()->GatewayDownload.file(auth,GatewayDownload.fileUrl(base,params("missing"),"demo"),new ByteArrayOutputStream(),()->true));assertEquals(404,e.status);}
 @Test public void immediateTruncationHasReceiveStage()throws Exception {
  DownloadFailure e=assertThrows(DownloadFailure.class,()->GatewayDownload.file(auth,GatewayDownload.fileUrl(base,params("truncated"),"demo"),new ByteArrayOutputStream(),()->true));
  assertEquals(DownloadFailure.Stage.RECEIVE,e.stage);assertTrue(DownloadFailure.safe(DownloadFailure.Stage.REQUEST,e).contains("DL_RECEIVE"));
 }
 @Test public void stagingWriteFailureHasReceiveStage()throws Exception {
  DownloadFailure e=assertThrows(DownloadFailure.class,()->GatewayDownload.file(auth,GatewayDownload.fileUrl(base,params("binary.apk"),"demo"),new OutputStream(){public void write(int b)throws IOException{throw new IOException("SECRET private path");}},()->true));
  assertEquals(DownloadFailure.Stage.RECEIVE,e.stage);assertFalse(DownloadFailure.safe(DownloadFailure.Stage.REQUEST,e).contains("SECRET"));
 }
 @Test public void boundsDeclaredLength()throws Exception {assertThrows(IOException.class,()->GatewayDownload.file(auth,GatewayDownload.fileUrl(base,params("oversize"),"demo"),new ByteArrayOutputStream(),()->true));}
 @Test public void boundsUnknownLengthAndCancellation()throws Exception {assertThrows(IOException.class,()->GatewayDownload.copy(new ByteArrayInputStream(new byte[20]),new ByteArrayOutputStream(),10,()->true));assertThrows(IOException.class,()->GatewayDownload.copy(new ByteArrayInputStream(new byte[20]),new ByteArrayOutputStream(),30,()->false));}
 @Test public void savesDataImageAndRejectsOtherSchemes()throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();GatewayDownload.image(auth,base,"data:image/png;base64,AAH/","demo",out,()->true);assertArrayEquals(new byte[]{0,1,-1},out.toByteArray());assertThrows(IllegalArgumentException.class,()->GatewayDownload.image(auth,base,"http://example.com/image.png","demo",out,()->true));assertThrows(IllegalArgumentException.class,()->GatewayDownload.image(auth,base,"file:///private","demo",out,()->true));}
 @Test public void sanitizesNames(){assertEquals("file.apk",GatewayDownload.filename("../file.apk",""));assertEquals("download",GatewayDownload.filename("..",""));assertEquals("x_y.png",GatewayDownload.filename("x\ny.png",""));}
}
