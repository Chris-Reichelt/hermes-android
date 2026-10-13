package com.hermes.privateapp;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Isolated built-renderer fixture; actual MainActivity clipboard bridge and WebView toolbar.
 * No gateway, credentials or user clipboard. Run on an explicitly selected disposable emulator. */
public class ClipboardInstrumentation extends Instrumentation {
 private MainActivity activity;
 private WebView web;
 private String eval(String script) throws Exception {
  CountDownLatch done=new CountDownLatch(1);String[] result={null};
  runOnMainSync(()->web.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));
  if(!done.await(30,TimeUnit.SECONDS))throw new AssertionError("WebView JS timeout");
  return result[0];
 }
 private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private void stage(String message){Bundle b=new Bundle();b.putString("stage",message);sendStatus(0,b);}
 private void await(String condition) throws Exception {
  for(int i=0;i<90;i++){if("true".equals(eval(condition)))return;Thread.sleep(1000);}
  throw new AssertionError("Fixture condition timed out: "+condition);
 }
 private boolean clickText(AccessibilityNodeInfo node,String text){
  if(node==null)return false;
  if(node.getText()!=null && text.equalsIgnoreCase(node.getText().toString()) && node.isClickable())return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
  for(int i=0;i<node.getChildCount();i++)if(clickText(node.getChild(i),text))return true;
  return false;
 }
 private void press(float x,float y,long duration)throws Exception{
  long start=SystemClock.uptimeMillis();
  sendPointerSync(MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0));Thread.sleep(duration);
  sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0));
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  String fixture;
  try(InputStream in=getContext().getAssets().open("clipboard-fixture.js")){fixture=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  final String script=fixture;
  runOnMainSync(()->{try{
   Method open=MainActivity.class.getDeclaredMethod("openRenderer");open.setAccessible(true);open.invoke(activity);
   Field f=MainActivity.class.getDeclaredField("web");f.setAccessible(true);web=(WebView)f.get(activity);
   web.stopLoading();
   WebViewCompat.addDocumentStartJavaScript(web,script,Collections.singleton("https://appassets.androidplatform.net"));
   web.loadUrl("https://appassets.androidplatform.net/index.html");
  }catch(Exception e){throw new RuntimeException(e);}});
  stage("Built renderer starting with synthetic gateway fixture");
  await("!!document.querySelector('[aria-label=Sessions]')");
  eval("document.querySelector('[aria-label=Sessions]').click()");
  await("[...document.querySelectorAll('#phone-sessions *')].some(e=>e.textContent==='TEST FIXTURE — shared history')");
  eval("[...document.querySelectorAll('#phone-sessions *')].filter(e=>e.textContent==='TEST FIXTURE — shared history').pop().click()");
  await("!!document.querySelector('[aria-label=Copy]') && !!document.querySelector('[role=textbox][contenteditable=true]')");
  eval("[...document.querySelectorAll('[aria-label=Copy]')].pop().click()");
  await("window.fixtureNativeWrites===1");
  // Read only after our own fixture wrote; no preexisting clipboard is inspected.
  final String[] text={null};runOnMainSync(()->{ClipboardManager c=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);text[0]=c.getPrimaryClip().getItemAt(0).getText().toString();});
  check("TEST FIXTURE historical answer".equals(text[0]),"Native Copy mismatch");
  stage("Actual native Copy verified; opening Android long-press toolbar");
  eval("window.fixtureEditor=document.querySelector('[role=textbox][contenteditable=true]'); fixtureEditor.focus();fixtureEditor.scrollIntoView({block:'center'})");
  Thread.sleep(2000);
  JSONArray pos=new JSONArray(eval("(()=>{let r=fixtureEditor.getBoundingClientRect();return [(r.x+Math.min(20,r.width/2))*devicePixelRatio,(r.y+r.height/2)*devicePixelRatio]})()"));
  int[] offset=new int[2];runOnMainSync(()->web.getLocationOnScreen(offset));
  float x=(float)pos.getDouble(0)+offset[0],y=(float)pos.getDouble(1)+offset[1];
  press(x,y,100);Thread.sleep(1500);press(x,y,1200);
  boolean pasted=false;for(int i=0;i<20;i++){if(clickText(getUiAutomation().getRootInActiveWindow(),"Paste")){pasted=true;break;}Thread.sleep(500);}
  check(pasted,"Native Paste toolbar item unavailable");
  await("fixtureEditor.textContent==='TEST FIXTURE historical answer'");
  check("0".equals(eval("window.fixtureNativeReads")),"Unexpected renderer clipboard read");
  check("0".equals(eval("fixtureCalls.filter(c=>c.rpc?.method==='prompt.submit').length")),"Unexpected submission");
  check("0".equals(eval("document.querySelectorAll('[role=menu]').length")),"Competing app menu");
  result.putString("receipt","PASS: built renderer session Copy -> actual MainActivity clipboard.write -> Android long-press native Paste -> actual composer; exact text once; no JS clipboard reads or prompt sends. Disposable emulator only.");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}finally{
  if(activity!=null)runOnMainSync(()->activity.finish());
 }}
}
