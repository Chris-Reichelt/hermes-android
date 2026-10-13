package com.hermes.privateapp;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import okhttp3.*;
import org.json.*;
public class AuthDiagnosticSafetyTest {
 @Test public void diagnosticsOnlyExposeAllowlistedStageCategoryAndStatus(){
  GatewayAuth auth=new GatewayAuth("http://fixture.ts.net",new OkHttpClient());
  String secret="https://private.ts.net/callback?code=private-code&state=private-state Bearer private-token <script>";
  Exception[] errors={new IOException(secret),new SocketTimeoutException(secret),new UnknownHostException(secret),new ConnectException(secret),new SocketException(secret),new javax.net.ssl.SSLException(secret),new JSONException(secret),new IllegalStateException(secret),new GatewayAuth.AuthException(429)};
  String[] kinds={"io","timeout","dns","connect","socket","tls","response-format","other","http-429"};
  for(int i=0;i<errors.length;i++)assertEquals("Sign-in detail: status / "+kinds[i],auth.signInDiagnostic(errors[i]));
  auth.cancel();assertEquals("Sign-in detail: status / canceled",auth.signInDiagnostic(new IOException(secret)));
 }
 @Test public void attemptOwnsPoolAndCancellationDoesNotEvictAnotherSession()throws Exception {
  OkHttpClient parent=new OkHttpClient();GatewayAuth a=new GatewayAuth("http://fixture.ts.net",parent),b=new GatewayAuth("http://fixture.ts.net",parent);
  java.lang.reflect.Field f=GatewayAuth.class.getDeclaredField("http");f.setAccessible(true);OkHttpClient ah=(OkHttpClient)f.get(a),bh=(OkHttpClient)f.get(b);
  assertNotSame(parent.connectionPool(),ah.connectionPool());assertNotSame(ah.connectionPool(),bh.connectionPool());
  assertFalse(ah.retryOnConnectionFailure());assertFalse(bh.retryOnConnectionFailure());a.cancel();b.cancel();
 }
}
