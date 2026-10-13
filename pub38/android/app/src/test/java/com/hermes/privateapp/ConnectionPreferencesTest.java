package com.hermes.privateapp;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import okhttp3.*;

public class ConnectionPreferencesTest {
 // A new adapter instance sees only what commit wrote, never Activity/auth fields.
 private SharedPreferences preferences(Map<String,String> disk,boolean commitWorks){
  Map<String,String> staged=new HashMap<>();
  Object editor=Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},(p,m,a)->{
   if(m.getName().equals("putString")){staged.put((String)a[0],(String)a[1]);return p;}
   if(m.getName().equals("commit")){if(commitWorks)disk.putAll(staged);return commitWorks;}
   throw new AssertionError("Unexpected storage operation: "+m.getName());
  });
  return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
   if(m.getName().equals("edit"))return editor;
   if(m.getName().equals("getString"))return disk.getOrDefault((String)a[0],(String)a[1]);
   throw new AssertionError("Unexpected preference read: "+m.getName());
  });
 }
 @Test public void restoresEndpointProfileAcrossRecreationButNeverSession()throws Exception {
  Map<String,String> disk=new HashMap<>();
  assertTrue(new ConnectionPreferences(preferences(disk,true)).save(" https://fixture.ts.net/ "," fixture-owner "));
  assertEquals(new HashSet<>(Arrays.asList("endpoint","profile")),disk.keySet());
  String[] restored=new ConnectionPreferences(preferences(disk,true)).restore();
  assertArrayEquals(new String[]{"https://fixture.ts.net","fixture-owner"},restored);
  GatewayAuth recreated=new GatewayAuth(restored[0],new OkHttpClient());
  try{recreated.request(new Request.Builder().url(restored[0]+"/api/auth/me").build());fail("No native session may survive recreation");}
  catch(GatewayAuth.AuthException expected){assertEquals(401,expected.status);}finally{recreated.cancel();}
 }
 @Test public void blankProfileDefaultsAndValidSelectionCanChange(){
  Map<String,String> disk=new HashMap<>();ConnectionPreferences p=new ConnectionPreferences(preferences(disk,true));
  p.save("http://100.64.1.2:8080","");assertEquals("default",p.restore()[1]);
  p.save("https://other.ts.net/gateway","owner-2");assertArrayEquals(new String[]{"https://other.ts.net/gateway","owner-2"},p.restore());
 }
 @Test public void rejectsCredentialUrlsAndArbitraryHostsWithoutChangingSavedSelection(){
  Map<String,String> disk=new HashMap<>();ConnectionPreferences p=new ConnectionPreferences(preferences(disk,true));p.save("https://fixture.ts.net","default");
  for(String bad:Arrays.asList("https://user:password@fixture.ts.net","https://fixture.ts.net?token=secret","https://fixture.ts.net#secret","https://example.com")){
   try{p.save(bad,"new-owner");fail();}catch(IllegalArgumentException expected){}
  }
  assertArrayEquals(new String[]{"https://fixture.ts.net","default"},p.restore());
 }
 @Test public void corruptPersistedSettingsAreNotTrustedAndDoNotAutoConnect(){
  Map<String,String> disk=new HashMap<>();disk.put("endpoint","https://fixture.ts.net?token=secret");disk.put("profile","owner");
  assertArrayEquals(new String[]{"","default"},new ConnectionPreferences(preferences(disk,true)).restore());
 }
 @Test public void invalidProfileAndDiskFailureAreReported(){
  Map<String,String> disk=new HashMap<>();ConnectionPreferences p=new ConnectionPreferences(preferences(disk,false));
  assertFalse(p.save("https://fixture.ts.net","owner"));assertTrue(disk.isEmpty());
  for(String bad:Arrays.asList("..","a/b","a\\b","a\nb","a\nb\nc")){try{p.save("https://fixture.ts.net",bad);fail();}catch(IllegalArgumentException expected){}}
 }
}
