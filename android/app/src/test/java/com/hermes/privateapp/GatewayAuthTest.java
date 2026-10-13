package com.hermes.privateapp;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import okhttp3.*;
import org.json.*;

public class GatewayAuthTest {
 private String base;private OkHttpClient browser;private GatewayAuth auth;
 @Before public void setup(){base=System.getenv("ANDROID_AUTH_TEST_URL");Assume.assumeNotNull(base);browser=new OkHttpClient.Builder().followRedirects(false).cookieJar(new CookieJar(){final List<Cookie> cookies=new ArrayList<>();public void saveFromResponse(HttpUrl u,List<Cookie> c){cookies.addAll(c);}public List<Cookie> loadForRequest(HttpUrl u){List<Cookie> r=new ArrayList<>();for(Cookie c:cookies)if(c.matches(u)&&c.expiresAt()>System.currentTimeMillis())r.add(c);return r;}}).build();auth=new GatewayAuth(base,new OkHttpClient());}
 @After public void teardown(){if(auth!=null)auth.cancel();if(browser!=null){browser.dispatcher().executorService().shutdown();browser.connectionPool().evictAll();}}
 private Response get(String url)throws Exception{return browser.newCall(new Request.Builder().url(url).build()).execute();}
 private Response post(String path,JSONObject body)throws Exception{return browser.newCall(new Request.Builder().url(base+path).post(RequestBody.create(body.toString(),MediaType.get("application/json"))).build()).execute();}
 private String passwordLogin(String authorize)throws Exception{
  try(Response r=get(authorize)){assertEquals(302,r.code());assertEquals("/login",r.header("Location"));}
  try(Response r=get(base+"/login")){assertTrue(r.body().string().contains("type=\"password\""));}
  JSONObject body=new JSONObject().put("provider","basic").put("username","android-disposable").put("password",System.getenv("ANDROID_AUTH_TEST_PASSWORD"));
  try(Response r=post("/auth/password-login",body)){assertEquals(200,r.code());return new JSONObject(r.body().string()).getString("next");}
 }
 private void login()throws Exception{
  String authorize=auth.beginNative();ExecutorService executor=Executors.newSingleThreadExecutor();Future<?> result=executor.submit(()->{try{auth.finishNative();}catch(Exception e){throw new RuntimeException(GatewayAuth.safeError(e));}});
  try{String callback=passwordLogin(authorize);try(Response r=get(callback)){assertEquals(200,r.code());}result.get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
 }
 private boolean socket(String url)throws Exception{
  CountDownLatch latch=new CountDownLatch(1);boolean[] ok={false};
  WebSocket socket=browser.newWebSocket(new Request.Builder().url(url).build(),new WebSocketListener(){public void onMessage(WebSocket w,String text){try{JSONObject j=new JSONObject(text);ok[0]="basic".equals(j.getString("provider"))&&"codex".equals(j.getString("profile"));}catch(Exception e){}latch.countDown();}public void onFailure(WebSocket w,Throwable t,Response r){latch.countDown();}});
  assertTrue(latch.await(10,TimeUnit.SECONDS));socket.cancel();return ok[0];
 }
 @Test public void nativeLoginBearerRefreshAndSingleUseSockets()throws Exception{
  login();
  try(Response r=auth.request(new Request.Builder().url(base+"/api/auth/me").build())){JSONObject j=new JSONObject(r.body().string());assertEquals("android-disposable",j.getString("user_id"));}
  String one=auth.socketUrl(base.replaceFirst("http","ws")+"/api/ws?profile=codex&token=must-not-survive");assertFalse(one.contains("token="));assertTrue(socket(one));assertFalse(socket(one));
  String two=auth.socketUrl(base.replaceFirst("http","ws")+"/api/ws?profile=codex");assertNotEquals(one,two);assertTrue(socket(two));
  java.lang.reflect.Field expiry=GatewayAuth.class.getDeclaredField("expires");expiry.setAccessible(true);expiry.setLong(auth,0);
  try(Response r=auth.request(new Request.Builder().url(base+"/api/auth/me").build())){assertEquals(200,r.code());}
  java.lang.reflect.Field access=GatewayAuth.class.getDeclaredField("access");access.setAccessible(true);access.set(auth,"expired-test-access");
  try(Response r=auth.request(new Request.Builder().url(base+"/api/auth/me").build())){assertEquals(200,r.code());}
  auth.cancel();try{auth.socketUrl(base.replaceFirst("http","ws")+"/api/ws");fail();}catch(GatewayAuth.AuthException e){assertEquals(401,e.status);}
 }
 @Test public void authenticatedArtifactDownloadRefreshesAndKeepsScope()throws Exception {
  login();java.lang.reflect.Field access=GatewayAuth.class.getDeclaredField("access");access.setAccessible(true);access.set(auth,"expired-test-access");
  JSONObject p=new JSONObject().put("connectionId","android-remote").put("path","/synthetic/test artifact.apk").put("sessionId","fixture-session");
  ByteArrayOutputStream out=new ByteArrayOutputStream();GatewayDownload.file(auth,GatewayDownload.fileUrl(base,p,"fixture-owner"),out,()->true);
  byte[] expected="ANDROID SYNTHETIC ARTIFACT".getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] all=java.util.Arrays.copyOf(expected,expected.length+3);all[expected.length+1]=1;all[expected.length+2]=(byte)255;
  assertArrayEquals(all,out.toByteArray());
 }
 @Test public void passwordFailureAndLegacyRejected()throws Exception{
  try(Response r=post("/auth/password-login",new JSONObject().put("provider","basic").put("username","android-disposable").put("password","wrong-disposable"))){assertEquals(401,r.code());assertFalse(r.body().string().contains("wrong-disposable"));}
  try{auth.useLegacy("never-use-this-as-password");fail();}catch(GatewayAuth.AuthException e){assertEquals(401,e.status);assertFalse(e.getMessage().contains("never-use"));}
  try(Response r=get(base+"/api/auth/me")){assertEquals(401,r.code());}
 }
 @Test public void rejectsWrongStateThenAcceptsCorrectCallback()throws Exception{
  String authorize=auth.beginNative();String callback=passwordLogin(authorize);ExecutorService ex=Executors.newSingleThreadExecutor();Future<?> f=ex.submit(()->{try{auth.finishNative();}catch(Exception e){throw new RuntimeException(GatewayAuth.safeError(e));}});
  try{HttpUrl bad=HttpUrl.get(callback).newBuilder().setQueryParameter("state","wrong").build();try(Response r=get(bad.toString())){assertEquals(400,r.code());}try(Response r=get(callback)){assertEquals(200,r.code());}f.get(10,TimeUnit.SECONDS);}finally{ex.shutdownNow();}
 }
 @Test public void serverRejectsCustomSchemeBadPkceAndCodeReplay()throws Exception{
  String authorize=auth.beginNative();HttpUrl bad=HttpUrl.get(authorize).newBuilder().setQueryParameter("redirect_uri","hermes://callback").build();try(Response r=get(bad.toString())){assertEquals(400,r.code());}
  String callback=passwordLogin(authorize);String code=HttpUrl.get(callback).queryParameter("code");
  JSONObject exchange=new JSONObject().put("code",code).put("code_verifier","wrong");
  try(Response r=post("/auth/native/token",exchange)){assertEquals(400,r.code());}try(Response r=post("/auth/native/token",exchange)){assertEquals(400,r.code());}
 }
 @Test public void refreshFailureRedactedAndCrossEndpointBlocked()throws Exception{
  login();java.lang.reflect.Field access=GatewayAuth.class.getDeclaredField("access");access.setAccessible(true);access.set(auth,"expired-test-access");java.lang.reflect.Field rt=GatewayAuth.class.getDeclaredField("refresh");rt.setAccessible(true);rt.set(auth,"invalid-test-refresh");
  try{auth.request(new Request.Builder().url(base+"/api/auth/me").build());fail();}catch(GatewayAuth.AuthException e){assertEquals(401,e.status);assertFalse(GatewayAuth.safeError(e).contains("invalid-test"));}
  try{auth.socketUrl("http://example.com/api/ws?token=secret");fail();}catch(IllegalArgumentException expected){}
 }
}
