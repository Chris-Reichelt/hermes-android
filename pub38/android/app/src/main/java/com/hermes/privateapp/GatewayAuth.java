package com.hermes.privateapp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** Memory-only native PKCE. No passwords, cookies, or bearer tokens enter the renderer. */
public final class GatewayAuth {
 private final String base;
 private final OkHttpClient http;
 private volatile ServerSocket listener;
 private volatile boolean cancelled;
 private String verifier, state, callbackPath;
 private String access="", refresh="", provider="";
 private long expires;
 private boolean legacy;
 // Bounded, memory-only diagnostics. Never include URLs, response bodies or auth material.
 private enum SignInStage { STATUS, LOOPBACK_BIND, CALLBACK_WAIT, TOKEN_EXCHANGE, TOKEN_VALIDATE, IDENTITY_CHECK, COMPLETE }
 private volatile SignInStage signInStage=SignInStage.STATUS;
 public String signInDiagnostic(Exception failure) {
  String kind=cancelled?"canceled":failure instanceof AuthException?"http-"+((AuthException)failure).status:
   failure instanceof SocketTimeoutException?"timeout":failure instanceof javax.net.ssl.SSLException?"tls":
   failure instanceof UnknownHostException?"dns":failure instanceof ConnectException?"connect":
   failure instanceof SocketException?"socket":failure instanceof JSONException?"response-format":
   failure instanceof IOException?"io":"other";
  return "Sign-in detail: "+signInStage.name().toLowerCase(Locale.ROOT).replace('_','-')+" / "+kind;
 }
 public GatewayAuth(String base, OkHttpClient http) { this.base=base; this.http=http.newBuilder().dispatcher(new Dispatcher()).connectionPool(new ConnectionPool()).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(300,TimeUnit.SECONDS).build(); }
 public static final class AuthException extends IOException {
  public final int status;
  AuthException(int status) { super(status==401?"Session expired. Disconnect and sign in again.":status==429?"Too many login attempts. Wait and try again.":"Gateway HTTP "+status);this.status=status; }
 }
 public static String safeError(Exception e) {
  if(e instanceof AuthException)return e.getMessage();
  if(e instanceof SocketTimeoutException)return "Sign-in or network request timed out. Try again.";
  return "Sign-in or gateway request failed. Check Tailscale, the gateway URL and browser, then try again.";
 }
 private static String random() {byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 private JSONObject json(Request r) throws Exception {return json(http,r);}
 private JSONObject json(OkHttpClient client,Request r) throws Exception {
  try(Response response=client.newCall(r).execute()) {
   if(!response.isSuccessful())throw new AuthException(response.code());
   return new JSONObject(response.body()==null?"{}":response.body().string());
  }
 }
 private Request post(String path,JSONObject body) {return new Request.Builder().url(base+path).post(RequestBody.create(body.toString(),MediaType.get("application/json"))).build();}
 private JSONObject exchangeToken(String code,String verifier) throws Exception {
  // The exchange uses a fresh, non-reused connection (zero-idle pool) so it never
  // rides a stale keep-alive that the server or Tailscale may have already closed
  // (that reset surfaced as "token-exchange / socket"). A bounded retry on
  // transport-level failure re-issues the one-time code on a new connection; it
  // never retries after an HTTP response (AuthException) or a parsed body
  // (JSONException), where the code has already been consumed.
  // Each attempt uses a brand-new, zero-idle connection so a stale keep-alive can never
  // be reused (a reset on a pooled socket is what produced "token-exchange / socket").
  // A persistent mid-flight reset (Tailscale flap, server dropping the conn) then recovers
  // on the next fresh connection + backoff instead of the caller re-issuing a new code.
  // It never retries after an HTTP response (AuthException) or a parsed body (JSONException),
  // where the one-time code has already been consumed.
  int attempt=0;
  while(true) {
   OkHttpClient fresh=http.newBuilder().connectionPool(new ConnectionPool(0,1,TimeUnit.MINUTES)).build();
   try {
    return json(fresh,post("/auth/native/token",new JSONObject().put("code",code).put("code_verifier",verifier)));
   } catch(AuthException e){throw e;}
    catch(JSONException e){throw e;}
    catch(SocketException e){
     if(++attempt>=5)throw e;
     try{Thread.sleep(120L*Math.min(attempt,4));}catch(InterruptedException ie){Thread.currentThread().interrupt();throw e;}
    }
  }
 }
 // Reliable in-app sign-in. No external browser, no 127.0.0.1 loopback socket, and no
 // one-time code that can expire mid-handoff. We mint a native PKCE broker ourselves
 // (our own verifier + challenge), POST the credentials with that broker in the
 // hermes_session_pkce cookie, and the gateway mints a loopback code for OUR broker. We
 // then redeem that code with OUR verifier. The code is bound to our challenge, so the
 // loopback redirect is only a carrier of a value we can validate and immediately redeem.
 public void loginInApp(String username,String password) throws Exception {
  if(cancelled)throw new IOException("Cancelled");
  signInStage=SignInStage.STATUS;
  OkHttpClient probe=http.newBuilder().connectionPool(new ConnectionPool(0,5,TimeUnit.MINUTES)).build();
  JSONObject status=json(probe,new Request.Builder().url(base+"/api/status").build());
  if(!status.optBoolean("auth_required"))throw new AuthException(400);
  // Confirm a basic provider is configured (the in-app path requires it).
  JSONArray provs=status.optJSONArray("auth_providers");
  boolean basic=status.optString("auth_provider","").equals("basic");
  if(!basic&&provs!=null)for(int i=0;i<provs.length();i++){
   try{
    // auth_providers may be a flat list of names (["basic"]) or a list of
    // {name:...} objects; accept either form.
    if("basic".equals(provs.optString(i,""))){basic=true;break;}
    JSONObject o=provs.getJSONObject(i);
    if("basic".equals(o.optString("name"))){basic=true;break;}
   }catch(Exception ie){}
  }
  if(!basic)throw new AuthException(400);
  // Mint a broker (native PKCE) using our own verifier + challenge.
  signInStage=SignInStage.LOOPBACK_BIND;
  verifier=random();state=random();
  String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
  String broker;
  int attempt=0;
  while(true) {
   OkHttpClient fresh=http.newBuilder().connectionPool(new ConnectionPool(0,1,TimeUnit.MINUTES)).build();
   try {
    String redir="http://127.0.0.1:18080/in-app-callback";
    String url=HttpUrl.get(base+"/auth/native/authorize").newBuilder()
     .addQueryParameter("code_challenge",challenge)
     .addQueryParameter("code_challenge_method","S256")
     .addQueryParameter("state",state)
     .addQueryParameter("redirect_uri",redir)
     .build().toString();
    // OkHttp does NOT auto-follow cross-origin 302s, and the broker cookie
    // (hermes_session_pkce) is set on a hop of the authorize chain. Follow
    // the redirects manually (max 5) and remember the first broker seen.
    String cookie=null,cur=url;
    int finalCode=0;
    Response last=null;
    for(int hop=0;hop<5;hop++){
     Response resp=fresh.newCall(new Request.Builder().url(cur).build()).execute();
     if(last!=null)last.close();
     last=resp;
     String sc=resp.header("set-cookie");if(sc!=null)cookie=sc;
     String loc=resp.header("location");
     int rc=resp.code();
     finalCode=rc;
     if((rc==301||rc==302||rc==303||rc==307||rc==308)&&loc!=null){
      HttpUrl resolved=HttpUrl.get(base).resolve(loc);
      cur=(resolved!=null)?resolved.toString():loc;
      continue;
     }
     break;
    }
    if(last!=null)last.close();
    if(finalCode!=0&&finalCode!=302&&!isSuccess(finalCode))throw new AuthException(finalCode);
    if(cookie==null)throw new IOException("No broker from authorize");
    broker=extractBroker(cookie);
    if(broker==null||broker.isEmpty())throw new IOException("No broker from authorize");
    break;
   } catch(AuthException e){throw e;}
    catch(SocketException e){ if(++attempt>=5)throw e; try{Thread.sleep(120L*Math.min(attempt,4));}catch(InterruptedException ie){Thread.currentThread().interrupt();throw e;} }
  }
  // POST credentials with the broker in the hermes_session_pkce cookie.
  signInStage=SignInStage.TOKEN_EXCHANGE;
  String code;
  attempt=0;
  while(true) {
   OkHttpClient fresh=http.newBuilder().connectionPool(new ConnectionPool(0,1,TimeUnit.MINUTES)).build();
   try {
    Request req=new Request.Builder().url(base+"/auth/password-login")
     .header("Cookie","hermes_session_pkce="+broker)
     .post(RequestBody.create(new JSONObject().put("provider","basic").put("username",username).put("password",password).toString(),MediaType.get("application/json")))
     .build();
    Response r=fresh.newCall(req).execute();
    String bodyStr=r.body()==null?"{}":r.body().string();
    if(!r.isSuccessful()){
     if(r.code()==401)throw new IOException("Incorrect username or password.");
     if(r.code()==429)throw new IOException("Too many login attempts. Wait a moment and try again.");
     throw new AuthException(r.code());
    }
    // The native path 302s to the redirect_uri with ?code=...&state=...
    String location=r.header("location");
    r.close();
    if(location!=null&&!location.isEmpty()){
     HttpUrl loc=HttpUrl.parse(location);
     code=loc!=null?loc.queryParameter("code"):null;
    } else {
     // Some responses return the code in JSON instead: either a top-level "code"
     // field, or embedded in a "next" redirect URL (?code=...&state=...).
     JSONObject body=new JSONObject(bodyStr);
     code=body.optString("code",null);
     if((code==null||code.isEmpty())&&body.has("next")){
      HttpUrl next=HttpUrl.parse(body.optString("next",""));
      code=next!=null?next.queryParameter("code"):null;
     }
    }
    if(code==null||code.isEmpty())throw new IOException("Login did not return a code");
    break;
   } catch(AuthException e){ if(e.status==401)throw new IOException("Incorrect username or password."); throw e; }
    catch(SocketException e){ if(++attempt>=5)throw e; try{Thread.sleep(120L*Math.min(attempt,4));}catch(InterruptedException ie){Thread.currentThread().interrupt();throw e;} }
  }
  // Redeem the code with our verifier -> bearer tokens.
  signInStage=SignInStage.TOKEN_VALIDATE;
  JSONObject tokens=exchangeToken(code,verifier);
  synchronized(this){if(cancelled)throw new IOException("Cancelled");accept(tokens);}
  signInStage=SignInStage.IDENTITY_CHECK;
  request(new Request.Builder().url(base+"/api/auth/me").build()).close();
  signInStage=SignInStage.COMPLETE;
 }
 private static boolean isSuccess(int code){return code>=200&&code<300;}
 private static String extractBroker(String setCookie) {
  if(setCookie==null)return null;
  for(String seg:setCookie.split(",")){
   String s=seg.trim();
   int eq=s.indexOf('=');
   if(eq>0&&s.substring(0,eq).trim().equals("hermes_session_pkce"))return s.substring(eq+1).split(";")[0].trim();
  }
  return null;
 }
 public String beginNative() throws Exception {
  signInStage=SignInStage.STATUS;
  // Do not retain the pre-browser status connection across interactive login. It can
  // outlive the server keep-alive and break the one-use exchange (or identity probe).
  // A separate zero-idle pool closes this probe without enabling unsafe POST retries.
  OkHttpClient probe=http.newBuilder().connectionPool(new ConnectionPool(0,5,TimeUnit.MINUTES)).build();
  JSONObject status=json(probe,new Request.Builder().url(base+"/api/status").build());
  JSONArray flows=status.optJSONArray("auth_flows");boolean supported=false;
  if(flows!=null)for(int i=0;i<flows.length();i++)supported|="native_pkce".equals(flows.optString(i));
  if(!status.optBoolean("auth_required")||!supported)throw new AuthException(400);
  // The gateway supports only literal loopback HTTP callbacks, not Android custom schemes.
  if(cancelled)throw new IOException("Cancelled");
  signInStage=SignInStage.LOOPBACK_BIND;
  listener=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));listener.setSoTimeout(180000);
  verifier=random();state=random();callbackPath="/callback/"+random();
  String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
  return HttpUrl.get(base+"/auth/native/authorize").newBuilder().addQueryParameter("code_challenge",challenge).addQueryParameter("code_challenge_method","S256").addQueryParameter("state",state).addQueryParameter("redirect_uri","http://127.0.0.1:"+listener.getLocalPort()+callbackPath).build().toString();
 }
 public void finishNative() throws Exception {finishNative(null);}
 public void finishNative(String returnUri) throws Exception {
  // This URI only brings the existing Activity forward; it NEVER carries auth data.
  if(returnUri!=null&&!returnUri.matches("[a-zA-Z][a-zA-Z0-9.]*\\.auth-return://complete"))throw new IllegalArgumentException("Invalid return URI");
  signInStage=SignInStage.CALLBACK_WAIT;
  String code=null; long deadline=System.currentTimeMillis()+180000;
  ServerSocket server=listener;
  try {
   while(!cancelled && System.currentTimeMillis()<deadline) {
    server.setSoTimeout((int)Math.max(1,deadline-System.currentTimeMillis()));
    boolean callbackAccepted=false;
    try(Socket socket=server.accept()) {
     socket.setSoTimeout(3000);
     InputStream in=socket.getInputStream();ByteArrayOutputStream line=new ByteArrayOutputStream();int c;
     while(line.size()<8192&&(c=in.read())!=-1&&c!='\n')line.write(c);
     String[] parts=line.toString("US-ASCII").trim().split(" ");
     HttpUrl target=parts.length==3&&"GET".equals(parts[0])&&parts[1].startsWith("/")?HttpUrl.parse("http://127.0.0.1:"+server.getLocalPort()+parts[1]):null;
     boolean valid=target!=null&&target.encodedPath().equals(callbackPath)&&state.equals(target.queryParameter("state"))&&target.queryParameterValues("state").size()==1&&target.queryParameterValues("code").size()==1;
     String candidate=valid?target.queryParameter("code"):null;
     valid=valid&&candidate!=null&&!candidate.isEmpty();
     if(valid) {
      callbackAccepted=true;
      // A browser Back/Return can reset this socket. Do not make credential exchange
      // conditional on successfully delivering a cosmetic browser response.
      try {
       if(cancelled)throw new IOException("Cancelled");
       signInStage=SignInStage.TOKEN_EXCHANGE;
       JSONObject tokens=exchangeToken(candidate,verifier);
       signInStage=SignInStage.TOKEN_VALIDATE;
       synchronized(this){if(cancelled)throw new IOException("Cancelled");accept(tokens);}
       signInStage=SignInStage.IDENTITY_CHECK;
       request(new Request.Builder().url(base+"/api/auth/me").build()).close();
      }catch(Exception failure){callbackResponse(socket,false,"Could not complete sign-in. Return to Hermes and try again. "+signInDiagnostic(failure),returnUri);throw failure;}
      signInStage=SignInStage.COMPLETE;
      code=candidate;
      callbackResponse(socket,true,"Signed in. Return to Hermes to continue.",returnUri);
      break;
     }
     callbackResponse(socket,false,"Invalid sign-in callback. Return to Hermes and retry sign-in.",null);
    } catch(SocketTimeoutException e) { if(callbackAccepted||System.currentTimeMillis()>=deadline)throw e; }
   }
   if(code==null||cancelled)throw new IOException("Cancelled");
  } finally {server.close();listener=null;verifier=null;state=null;callbackPath=null;}
 }
 private static void callbackResponse(Socket socket,boolean ok,String message,String returnUri) {
  String body="<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Hermes sign-in</title></head><body><h1>"+message+"</h1>"
   +(returnUri==null?"":"<p><a href=\""+returnUri+"\">Return to Hermes</a></p>")
   +"<p>If the link does not open the app, switch back to Hermes. "+(ok?"You do not need to sign in again after a successful sign-in.":"Sign-in did not complete. Note the sign-in detail above, if shown, before retrying.")+"</p></body></html>";
  byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
  try {
   socket.getOutputStream().write(("HTTP/1.1 "+(ok?"200 OK":"400 Bad Request")+"\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n\r\n"+body).getBytes(StandardCharsets.UTF_8));
  }catch(IOException browserClosed){/* Response is cosmetic; never discard a valid callback. */}
 }
 public synchronized void useLegacy(String token) throws Exception {
  JSONObject status=json(new Request.Builder().url(base+"/api/status").build());
  if(status.optBoolean("auth_required")||token.isEmpty())throw new AuthException(401);
  legacy=true;access=token;
 }
 private void accept(JSONObject tokens) throws Exception {
  if(cancelled)throw new IOException("Cancelled");
  String a=tokens.getString("access_token"),r=tokens.optString("refresh_token"),p=tokens.getString("provider");long exp=tokens.getLong("expires_at");
  if(a.isEmpty()||p.isEmpty()||exp<=System.currentTimeMillis()/1000)throw new IOException("Invalid session");
  access=a;refresh=r;provider=p;expires=exp;
 }
 private synchronized String bearer(boolean force,String rejected) throws Exception {
  if(cancelled||access.isEmpty())throw new AuthException(401);
  if(!legacy&&((force&&access.equals(rejected))||expires<=System.currentTimeMillis()/1000+30)) {
   if(refresh.isEmpty()){access="";throw new AuthException(401);}
   try {accept(json(post("/auth/native/refresh",new JSONObject().put("refresh_token",refresh).put("provider",provider))));}
   catch(AuthException e){if(e.status==401){access="";refresh="";}throw e;}
  }
  return access;
 }
 public String getBaseUrl() { return base; }
 public String getBearer() { return access; }
 public Response request(Request request) throws Exception {
  EndpointPolicy.requireSameEndpoint(base,request.url().toString());
  String sent=bearer(false,"");
  Response response=http.newCall(request.newBuilder().header("Authorization","Bearer "+sent).build()).execute();
  if(response.code()==401&&!legacy) {
   response.close();String renewed=bearer(true,sent);
   response=http.newCall(request.newBuilder().header("Authorization","Bearer "+renewed).build()).execute();
  }
  if(!response.isSuccessful()){int status=response.code();response.close();throw new AuthException(status);}
  return response;
 }
 public String socketUrl(String requested) throws Exception {
  return socketUrl(requested, false);
 }
 public String socketUrl(String requested, boolean skipEndpointCheck) throws Exception {
  if (!skipEndpointCheck) EndpointPolicy.requireSameEndpoint(base, requested);
  HttpUrl incoming=HttpUrl.get(requested.replaceFirst("^ws","http"));
  String prefix=HttpUrl.get(base).encodedPath().replaceAll("/+$", "");
  String relative=incoming.encodedPath().substring(prefix.length());
  if(!Arrays.asList("/api/ws","/api/audio/speak-stream","/api/console","/api/pty","/api/pub","/api/events","/api/push").contains(relative))throw new IOException("Invalid socket path");
  HttpUrl.Builder url=incoming.newBuilder().removeAllQueryParameters("token").removeAllQueryParameters("ticket").removeAllQueryParameters("internal");
  if(legacy)url.addQueryParameter("token",bearer(false,""));
  else try(Response response=request(post("/api/auth/ws-ticket",new JSONObject()))) {
   String ticket=new JSONObject(response.body().string()).getString("ticket");if(ticket.isEmpty())throw new IOException("Missing ticket");
   url.addQueryParameter("ticket",ticket);
  }
  return url.build().toString().replaceFirst("^http","ws");
 }
 public void cancel() {
  cancelled=true;ServerSocket s=listener;if(s!=null)try{s.close();}catch(IOException ignored){}
  http.dispatcher().cancelAll();
  http.connectionPool().evictAll();
  synchronized(this){access="";refresh="";provider="";}
 }
}
