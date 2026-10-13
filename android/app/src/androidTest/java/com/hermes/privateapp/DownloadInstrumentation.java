package com.hermes.privateapp;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.json.JSONObject;

/** Real Android picker + native transport, isolated synthetic byte server.
 * Deliberately does not assert WebView/chat or live-account correctness. */
public class DownloadInstrumentation extends Instrumentation {
 MainActivity activity;ServerSocket server;GatewayAuth auth;AtomicInteger hits=new AtomicInteger();volatile String request="";
 final byte[] payload="HERMES SYNTHETIC DOWNLOAD FIXTURE\n\u0000\u0001".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 byte[] shell(String command)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 boolean clickText(AccessibilityNodeInfo n,String text){if(n==null)return false;if(n.getText()!=null&&text.equalsIgnoreCase(n.getText().toString())&&n.isClickable())return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);for(int i=0;i<n.getChildCount();i++)if(clickText(n.getChild(i),text))return true;return false;}
 void awaitSave()throws Exception{for(int i=0;i<90;i++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();clickText(root,"Wait");if(clickText(root,"Save"))return;Thread.sleep(1000);}throw new AssertionError("Save button unavailable");}
 void stage(String text){Bundle b=new Bundle();b.putString("stage",text);sendStatus(0,b);}
 void awaitDone()throws Exception{for(int i=0;i<90;i++){final boolean[] done={false};runOnMainSync(()->{try{done[0]=field("saveCall")==null;}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;Thread.sleep(1000);}throw new AssertionError("Save did not finish");}
 void begin(String method,JSONObject params)throws Exception{JSONObject call=new JSONObject().put("id",1).put("method",method).put("_generation",field("setupGeneration"));Method m=MainActivity.class.getDeclaredMethod("startSave",JSONObject.class,JSONObject.class);m.setAccessible(true);runOnMainSync(()->{try{m.invoke(activity,call,params);}catch(Exception e){throw new RuntimeException(e);}});}
 @Override public void onStart(){Bundle result=new Bundle();try{
  server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));String base="http://127.0.0.1:"+server.getLocalPort();
  Thread thread=new Thread(()->{while(!server.isClosed())try(Socket s=server.accept()){
   BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream()));String first=reader.readLine(),line;String bearer="";while((line=reader.readLine())!=null&&!line.isEmpty())if(line.startsWith("Authorization: "))bearer=line.substring(15);
   boolean status=first.contains("/api/status");if(!status){request=first;hits.incrementAndGet();check(bearer.equals("Bearer synthetic-download-token"),"Missing native bearer");}
   byte[] bytes=status?"{\"auth_required\":false}".getBytes():payload;OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes());out.write(bytes);out.flush();
  }catch(Exception ignored){}});thread.setDaemon(true);thread.start();
  auth=new GatewayAuth(base,new OkHttpClient());auth.useLegacy("synthetic-download-token");
  activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  runOnMainSync(()->{try{set("base",base);set("auth",auth);set("primaryProfile","different-selected-profile");}catch(Exception e){throw new RuntimeException(e);}});
  String name="hermes-fixture-"+System.currentTimeMillis()+".txt";
  JSONObject p=new JSONObject().put("connectionId","android-remote").put("profile","fixture-owner").put("sessionId","fixture-session").put("path","/synthetic/a b.txt").put("suggestedName",name);
  stage("Native file picker starting");begin("download.file",p);awaitSave();stage("Save tapped");awaitDone();stage("Native save finished");
  byte[] saved=shell("cat /sdcard/Download/"+name);check(Arrays.equals(saved,payload),"Saved bytes differ: "+new String(saved));check(hits.get()==1,"Unexpected request count");check(request.contains("profile=fixture-owner")&&request.contains("session_id=fixture-session")&&request.contains("/api/fs/download?"),"Owner/session/endpoint incorrect");
  shell("rm /sdcard/Download/"+name);
  stage("Binary bytes verified; cancel test");begin("download.file",p);Thread.sleep(2500);shell("input keyevent BACK");awaitDone();check(hits.get()==1,"Cancel downloaded bytes");
  String imageName="hermes-image-fixture-"+System.currentTimeMillis()+".png";
  begin("download.image",new JSONObject().put("url","data:image/png;base64,AAH/").put("suggestedName",imageName));awaitSave();awaitDone();check(Arrays.equals(shell("cat /sdcard/Download/"+imageName),new byte[]{0,1,-1}),"Image save differs");shell("rm /sdcard/Download/"+imageName);
  result.putString("receipt","PASS: native authenticated binary save through real Android document picker; exact bytes; owner/session contract; cancel makes no request; data-image save. Synthetic fixture only, no WebView or phone claim.");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(auth!=null)auth.cancel();try{if(server!=null)server.close();}catch(Exception ignored){}if(activity!=null)runOnMainSync(()->activity.finish());}}
}
