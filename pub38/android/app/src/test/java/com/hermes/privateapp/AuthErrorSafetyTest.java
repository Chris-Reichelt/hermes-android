package com.hermes.privateapp;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.SocketTimeoutException;

public class AuthErrorSafetyTest {
 @Test public void arbitraryExceptionsCannotExposeSecrets(){
  String secret="test-only-password-refresh-token-ticket";
  for(Exception e:new Exception[]{new IOException("http://host/api/ws?ticket="+secret),new IllegalArgumentException("Bearer "+secret),new RuntimeException("password="+secret)}){
   assertFalse(GatewayAuth.safeError(e).contains(secret));assertFalse(GatewayAuth.safeError(e).contains("http://host"));
  }
 }
 @Test public void errorsAreActionableWithoutServerBodies(){
  assertTrue(GatewayAuth.safeError(new GatewayAuth.AuthException(401)).contains("sign in again"));
  assertTrue(GatewayAuth.safeError(new GatewayAuth.AuthException(429)).contains("Wait"));
  assertTrue(GatewayAuth.safeError(new SocketTimeoutException("secret-url")).contains("timed out"));
  assertFalse(GatewayAuth.safeError(new SocketTimeoutException("secret-url")).contains("secret-url"));
 }
}
