package com.hermes.privateapp;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.*;

/** Production broker + actual loopback; injected faults are NOT phone diagnoses. */
public class FirstLoginBoundaryTest {
 String base; GatewayAuth auth; OkHttpClient browser; ExecutorService worker;
 AtomicInteger exchanges=new AtomicInteger();
 @Before public void setup(){
  base=System.getenv("ANDROID_AUTH_TEST_URL");Assume.assumeNotNull(base);
  worker=Executors.newSingleThreadExecutor();
  browser=new OkHttpClient.Builder().followRedirects(false).cookieJar(new CookieJar(){
   final Map<String,Cookie> cookies=new HashMap<>();
   public void saveFromResponse(HttpUrl u,List<Cookie> cs){for(Cookie c:cs)cookies.put(c.name(),c);}
   public List<Cookie> loadForRequest(HttpUrl u){List<Cookie> out=new ArrayList<>();for(Cookie c:cookies.values())if(c.matches(u)&&c.expiresAt()>System.currentTimeMillis())out.add(c);return out;}
  }).build();
  fresh(null);
 }
 void fresh(Interceptor fault){
  if(auth!=null)auth.cancel();
  OkHttpClient.Builder b=new OkHttpClient.Builder().addInterceptor(chain->{if(chain.request().url().encodedPath().equals("/auth/native/token"))exchanges.incrementAndGet();return chain.proceed(chain.request());});
  if(fault!=null)b.addInterceptor(fault);auth=new GatewayAuth(base,b.build());
 }
 @After public void cleanup(){if(auth!=null)auth.cancel();if(worker!=null)worker.shutdownNow();if(browser!=null){browser.dispatcher().executorService().shutdownNow();browser.connectionPool().evictAll();}}
 String callback()throws Exception {
  try(Response r=browser.newCall(new Request.Builder().url(auth.beginNative()).build()).execute()){assertEquals(302,r.code());}
  JSONObject body=new JSONObject().put("provider","basic").put("username","android-disposable").put("password",System.getenv("ANDROID_AUTH_TEST_PASSWORD"));
  try(Response r=browser.newCall(new Request.Builder().url(base+"/auth/password-login").post(RequestBody.create(body.toString(),MediaType.get("application/json"))).build()).execute()){assertEquals(200,r.code());return new JSONObject(r.body().string()).getString("next");}
 }
 Future<Exception> finish(){final GatewayAuth attempt=auth;return worker.submit(()->{try{attempt.finishNative("com.hermes.privateapp.auth-return://complete");return null;}catch(Exception e){return e;}});}
 String page(String url,int status)throws Exception {try(Response r=browser.newCall(new Request.Builder().url(url).build()).execute()){assertEquals(status,r.code());return r.body().string();}}
 void successfulRetry()throws Exception {fresh(null);String cb=callback();Future<Exception> done=finish();assertTrue(page(cb,200).contains("Signed in."));assertNull(done.get(10,TimeUnit.SECONDS));}
 @Test public void preliminaryEofIdleFaviconWrongMethodAndStateDoNotProduceReportedPage()throws Exception {
  String cb=callback();HttpUrl u=HttpUrl.get(cb);Future<Exception> done=finish();
  // No request bytes: accepted EOF and browser speculative idle connection.
  try(Socket eof=new Socket(u.host(),u.port())){eof.shutdownOutput();}
  try(Socket idle=new Socket(u.host(),u.port())){idle.setSoTimeout(5000);assertEquals(-1,idle.getInputStream().read());}
  for(String bad:Arrays.asList(u.newBuilder().encodedPath("/favicon.ico").build().toString(),u.newBuilder().setQueryParameter("state","wrong").build().toString())){
   String p=page(bad,400);assertTrue(p.contains("Invalid sign-in callback"));assertFalse(p.contains("Could not complete sign-in"));
  }
  try(Response r=browser.newCall(new Request.Builder().url(cb).post(RequestBody.create("",MediaType.get("text/plain"))).build()).execute()){assertEquals(400,r.code());assertTrue(r.body().string().contains("Invalid sign-in callback"));}
  assertEquals(0,exchanges.get());assertTrue(page(cb,200).contains("Signed in."));assertNull(done.get(10,TimeUnit.SECONDS));assertEquals(1,exchanges.get());
 }
 @Test public void invalidCodeProducesExactFailureThenFreshAttemptSucceeds()throws Exception {
  String cb=callback();String bad=HttpUrl.get(cb).newBuilder().setQueryParameter("code","synthetic-invalid-code").build().toString();Future<Exception> done=finish();
  String p=page(bad,400);assertTrue(p.contains("Could not complete sign-in. Return to Hermes and try again."));assertFalse(p.contains("synthetic-invalid-code"));
  Exception e=done.get(10,TimeUnit.SECONDS);assertTrue(e instanceof GatewayAuth.AuthException);assertEquals(400,((GatewayAuth.AuthException)e).status);assertTrue(p.contains("Sign-in detail: token-exchange / http-400"));assertFalse(p.contains("You do not need to sign in again"));assertEquals(1,exchanges.get());successfulRetry();
 }
 @Test public void identityFailureProducesSamePageAfterSuccessfulExchangeThenRetrySucceeds()throws Exception {
  fresh(chain->{if(chain.request().url().encodedPath().equals("/api/auth/me"))return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("synthetic").body(ResponseBody.create("secret-body",MediaType.get("text/plain"))).build();return chain.proceed(chain.request());});
  String cb=callback();Future<Exception> done=finish();String p=page(cb,400);assertTrue(p.contains("Could not complete sign-in. Return to Hermes and try again."));assertFalse(p.contains("secret-body"));
  Exception e=done.get(10,TimeUnit.SECONDS);assertTrue(e instanceof GatewayAuth.AuthException);assertEquals(503,((GatewayAuth.AuthException)e).status);assertTrue(p.contains("Sign-in detail: identity-check / http-503"));assertEquals(1,exchanges.get());successfulRetry();
 }
 @Test public void preliminaryResetAbortsWithoutGeneratingReportedHtml()throws Exception {
  HttpUrl authorize=HttpUrl.get(auth.beginNative());HttpUrl cb=HttpUrl.get(authorize.queryParameter("redirect_uri"));
  try(Socket reset=new Socket(cb.host(),cb.port())){reset.setSoLinger(true,0);}
  Exception e=finish().get(10,TimeUnit.SECONDS);assertTrue(e instanceof SocketException);assertEquals(0,exchanges.get());
 }
 @Test public void malformedTokenResponseHasRedactedResponseStage()throws Exception {
  fresh(chain->{if(chain.request().url().encodedPath().equals("/auth/native/token"))return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("synthetic-secret-not-json",MediaType.get("application/json"))).build();return chain.proceed(chain.request());});
  String cb=callback();Future<Exception> done=finish();String p=page(cb,400);assertNotNull(done.get(10,TimeUnit.SECONDS));assertTrue(p.contains("Sign-in detail: token-exchange / response-format"));assertFalse(p.contains("synthetic-secret-not-json"));
 }
 @Test public void expiredTokenResponseHasRedactedValidationStage()throws Exception {
  fresh(chain->{if(chain.request().url().encodedPath().equals("/auth/native/token"))return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("{\"access_token\":\"secret-access\",\"refresh_token\":\"secret-refresh\",\"provider\":\"basic\",\"expires_at\":1}",MediaType.get("application/json"))).build();return chain.proceed(chain.request());});
  String cb=callback();Future<Exception> done=finish();String p=page(cb,400);assertNotNull(done.get(10,TimeUnit.SECONDS));assertTrue(p.contains("Sign-in detail: token-validate / io"));assertFalse(p.contains("secret-access"));assertFalse(p.contains("secret-refresh"));
 }
 @Test public void browserDelayBeyondServerKeepAliveDoesNotFailFirstLogin()throws Exception {
  // Uvicorn default keep-alive is 5s. This tests a hypothesis, not a timing repair.
  String cb=callback();Thread.sleep(6100);Future<Exception> done=finish();
  try(Response r=browser.newCall(new Request.Builder().url(cb).build()).execute()){
   String body=r.body().string();Exception failure=done.get(10,TimeUnit.SECONDS);
   if(failure!=null){System.out.println("delay-failure-class="+failure.getClass().getSimpleName()+" unexpected-end="+String.valueOf(failure.getMessage()).contains("unexpected end of stream"));}
   if("1".equals(System.getenv("ANDROID_AUTH_EXPECT_STALE_FAILURE"))){
    assertEquals(400,r.code());assertTrue(body.contains("Could not complete sign-in. Return to Hermes and try again."));assertTrue(failure instanceof IOException);assertTrue(failure.getMessage().contains("unexpected end of stream"));
   }else{assertEquals(failure==null?"success":auth.signInDiagnostic(failure),200,r.code());assertTrue(body.contains("Signed in."));assertNull(failure);}
  }
  assertEquals(1,exchanges.get());
  if("1".equals(System.getenv("ANDROID_AUTH_EXPECT_STALE_FAILURE")))successfulRetry();
 }
}
