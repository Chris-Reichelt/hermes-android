package com.hermes.privateapp;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public class DownloadFailureTest {
 File cache;
 @Before public void setup()throws Exception {cache=Files.createTempDirectory("download-stage-test-").toFile();}
 @After public void cleanup(){assertEquals("staging files must be removed",0,cache.list().length);cache.delete();}
 DownloadFailure pipelineFailure(GatewaySave.Transfer transfer,GatewaySave.Destination destination) {
  return assertThrows(DownloadFailure.class,()->GatewaySave.save(cache,transfer,destination,()->true));
 }
 @Test public void malformedPayloadsDoNotBecomeStrings()throws Exception {
  for(Object value:new Object[]{JSONObject.NULL,12,true,new JSONObject(),"", " /bad\npath"}) {
   JSONObject p=new JSONObject().put("path",value);
   assertThrows(IllegalArgumentException.class,()->GatewayDownload.fileUrl("http://127.0.0.1",p,"fixture"));
  }
  for(String field:new String[]{"connectionId","sessionId"}) {
   JSONObject p=new JSONObject().put("path","/fixture/file.apk").put(field,new JSONObject());
   assertThrows(IllegalArgumentException.class,()->GatewayDownload.fileUrl("http://127.0.0.1",p,"fixture"));
  }
 }
 @Test public void inlineImageValidationPreservesExistingImageLimit()throws Exception {
  String data="data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(new byte[20000]);
  assertEquals(data,GatewayDownload.text(new JSONObject().put("url",data),"url",true));
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  GatewayDownload.image(null,"https://fixture.test.ts.net",data,"fixture",out,()->true);
  assertEquals(20000,out.size());
  assertThrows(IllegalArgumentException.class,()->GatewayDownload.text(new JSONObject().put("path",data),"path",true));
 }
 @Test public void optionalNullSessionIsAbsentAndLiteralPathIsUnchanged()throws Exception {
  String path="file:///fixture/a %20+&.apk";
  okhttp3.HttpUrl u=GatewayDownload.fileUrl("http://127.0.0.1",new JSONObject().put("path",path).put("sessionId",JSONObject.NULL),"fixture");
  assertEquals(path,u.queryParameter("path"));assertNull(u.queryParameter("session_id"));
 }
 @Test public void errorsNeverExposeExceptionContent() {
  for(DownloadFailure.Stage s:DownloadFailure.Stage.values()) {
   for(Exception e:new Exception[]{new IOException("https://private/?token=SECRET /private/path"),new SecurityException("SECRET"),new RuntimeException("SECRET")}) {
    String text=DownloadFailure.safe(s,e);
    assertTrue(text.contains("["+s.code+(s==DownloadFailure.Stage.REQUEST?"_":"]")));assertFalse(text.contains("SECRET"));assertFalse(text.contains("private"));assertTrue(text.length()<350);
   }
  }
 }
 @Test public void requestCategoriesAreFixedAndDoNotExposeDetails() {
  Exception[] errors={new javax.net.ssl.SSLHandshakeException("SECRET"),new java.net.UnknownHostException("SECRET"),new java.net.ConnectException("SECRET"),new java.net.SocketTimeoutException("SECRET"),new java.net.SocketException("SECRET"),new SecurityException("SECRET"),new IllegalArgumentException("SECRET"),new IllegalStateException("SECRET"),new org.json.JSONException("SECRET"),new NullPointerException("SECRET"),new IOException("SECRET"),new RuntimeException("SECRET")};
  String[] codes={"TLS","DNS","CONNECT","TIMEOUT","SOCKET","PERMISSION","ARGUMENT","STATE","JSON","NULL","IO","OTHER"};
  for(int i=0;i<errors.length;i++) {
   String direct=DownloadFailure.safe(DownloadFailure.Stage.REQUEST,errors[i]);
   String wrapped=DownloadFailure.safe(DownloadFailure.Stage.WORKER,new DownloadFailure(DownloadFailure.Stage.REQUEST,errors[i]));
   assertEquals(direct,wrapped);assertTrue(direct.contains("[DL_REQUEST_"+codes[i]+"]"));assertFalse(direct.contains("SECRET"));
   assertTrue(DownloadFailure.safe(DownloadFailure.Stage.DESTINATION_COPY,errors[i]).contains("[DL_DESTINATION_COPY]"));
  }
  assertTrue(DownloadFailure.safe(DownloadFailure.Stage.REQUEST,new GatewayAuth.AuthException(403)).contains("[DL_REQUEST]"));
 }
 @Test public void temporaryStorageFailureIsNotNetworkFailure() {
  DownloadFailure e=assertThrows(DownloadFailure.class,()->GatewaySave.save(new File(cache,"missing/directory"),o->{fail("requested network");},()->{fail("opened destination");return null;},()->true));
  assertEquals(DownloadFailure.Stage.STAGING,e.stage);
 }
 @Test public void immediateRequestFailureDoesNotOpenDestination() {
  DownloadFailure e=pipelineFailure(o->{throw new IOException("SECRET");},()->{fail("opened destination");return null;});
  assertEquals(DownloadFailure.Stage.REQUEST,e.stage);assertFalse(DownloadFailure.safe(e.stage,e).contains("SECRET"));
 }
 @Test public void httpStatusIsBoundedAndStageSpecific() {
  DownloadFailure e=pipelineFailure(o->{throw new GatewayAuth.AuthException(403);},()->null);
  assertEquals(DownloadFailure.Stage.REQUEST,e.stage);assertTrue(DownloadFailure.safe(e.stage,e).contains("HTTP 403"));
 }
 @Test public void destinationNullAndPermissionFailuresAreDistinctFromRequest() {
  assertEquals(DownloadFailure.Stage.DESTINATION_OPEN,pipelineFailure(o->o.write(1),()->null).stage);
  DownloadFailure e=pipelineFailure(o->o.write(1),()->{throw new SecurityException("content://SECRET");});
  assertEquals(DownloadFailure.Stage.DESTINATION_OPEN,e.stage);assertTrue(DownloadFailure.safe(e.stage,e).contains("Permission denied"));
 }
 @Test public void writeAndCloseErrorsAreDestinationCopyFailures() {
  assertEquals(DownloadFailure.Stage.DESTINATION_COPY,pipelineFailure(o->o.write(1),()->new OutputStream(){public void write(int b)throws IOException{throw new IOException("SECRET");}}).stage);
  assertEquals(DownloadFailure.Stage.DESTINATION_COPY,pipelineFailure(o->o.write(1),()->new ByteArrayOutputStream(){public void close()throws IOException{throw new IOException("SECRET");}}).stage);
 }
 @Test public void changedSessionNeverOpensDestination() {
  AtomicBoolean active=new AtomicBoolean(true);
  DownloadFailure e=assertThrows(DownloadFailure.class,()->GatewaySave.save(cache,o->{o.write(1);active.set(false);},()->{fail("opened destination");return null;},active::get));
  assertEquals(DownloadFailure.Stage.SESSION,e.stage);
 }
 @Test public void missingStagedFileHasReadStage() {
  DownloadFailure e=pipelineFailure(o->{o.write(1);for(File file:cache.listFiles())assertTrue(file.delete());},()->{fail("opened destination");return null;});
  assertEquals(DownloadFailure.Stage.STAGED_READ,e.stage);
 }
 @Test public void successfulSaveClosesAndCopiesExactly()throws Exception {
  AtomicBoolean closed=new AtomicBoolean();ByteArrayOutputStream destination=new ByteArrayOutputStream(){public void close(){closed.set(true);}};
  byte[] bytes={0,1,2,127,-128,-1};GatewaySave.save(cache,o->o.write(bytes),()->destination,()->true);
  assertArrayEquals(bytes,destination.toByteArray());assertTrue(closed.get());
 }
}
