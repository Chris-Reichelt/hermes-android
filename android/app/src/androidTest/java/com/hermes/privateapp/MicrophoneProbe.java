package com.hermes.privateapp;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.webkit.WebView;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.json.*;

/** Local-only instrumentation: no gateway, auth, prompts or uploaded audio.
 * Uses the installed Activity's unchanged WebView settings and permission handler.
 * Not packaged in the deliverable application. */
public class MicrophoneProbe extends Instrumentation {
 private Activity activity;
 private WebView web;
 private String evaluate(String script) throws Exception {
  CountDownLatch latch=new CountDownLatch(1);String[] result={null};
  runOnMainSync(()->web.evaluateJavascript(script,value->{result[0]=value;latch.countDown();}));
  if(!latch.await(180,TimeUnit.SECONDS))throw new Exception("Javascript timeout");
  return result[0];
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){
  Bundle output=new Bundle();
  try {
   activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   runOnMainSync(()->{try{
    Method open=MainActivity.class.getDeclaredMethod("openRenderer");open.setAccessible(true);open.invoke(activity);
    Field field=MainActivity.class.getDeclaredField("web");field.setAccessible(true);web=(WebView)field.get(activity);web.stopLoading();
    // Replace renderer with inert content on the SAME trusted secure origin.
    // Retains real ChromeClient, native permission bridge and security settings.
    web.loadDataWithBaseURL("https://appassets.androidplatform.net/", "<html><body>Local microphone test; no network</body></html>","text/html","UTF-8",null);
   }catch(Exception e){throw new RuntimeException(e);}});
   Thread.sleep(3000);
   evaluate("window.probeResult=null; (async()=>{const results=[]; for(const audio of [{echoCancellation:true,noiseSuppression:true},{autoGainControl:true,echoCancellation:true,noiseSuppression:true},{echoCancellation:false,noiseSuppression:false,autoGainControl:false},true,{echoCancellation:true,noiseSuppression:true}]) {let stream,context;try {stream=await navigator.mediaDevices.getUserMedia({audio});const track=stream.getAudioTracks()[0];context=new AudioContext();await context.resume();const analyser=context.createAnalyser();context.createMediaStreamSource(stream).connect(analyser);const chunks=[];const recorder=new MediaRecorder(stream);recorder.ondataavailable=e=>chunks.push(e.data);const stopped=new Promise(r=>recorder.onstop=r);recorder.start();await new Promise(r=>setTimeout(r,600));const data=new Float32Array(analyser.fftSize);analyser.getFloatTimeDomainData(data);recorder.stop();await stopped;results.push({audio,ok:true,readyState:track.readyState,muted:track.muted,settings:track.getSettings(),context:context.state,peak:Math.max(...data.map(Math.abs)),bytes:new Blob(chunks).size,mime:recorder.mimeType});}catch(e){results.push({audio,ok:false,name:e.name,message:e.message});}finally {stream?.getTracks().forEach(t=>t.stop());if(context)await context.close();}}window.probeResult={secure:isSecureContext,userAgent:navigator.userAgent,results};})()");
   String result="null";
   for(int i=0;i<120 && "null".equals(result);i++){Thread.sleep(1000);result=evaluate("window.probeResult");}
   output.putString("probe",result);
   if("null".equals(result))throw new Exception("Capture did not finish (check permission dialog)");
   finish(Activity.RESULT_OK,output);
  }catch(Exception e){output.putString("error",e.toString());finish(Activity.RESULT_CANCELED,output);}
  finally {if(activity!=null)runOnMainSync(()->activity.finish());}
 }
}
