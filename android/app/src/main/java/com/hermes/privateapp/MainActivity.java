package com.hermes.privateapp;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.media.AudioManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

/** Private native shell: packaged renderer, existing REST/JSON-RPC, no local agent. */
public class MainActivity extends Activity {
 public static volatile boolean isForeground = false;
 public static volatile boolean isAlive = false;
 private WebView web;
 private String base="", primaryProfile="default";
 private volatile GatewayAuth auth;
 private boolean tokenMode=false;
 private volatile int setupGeneration=0;
 private final SignInLifecycle signIn=new SignInLifecycle();
 private ConnectionPreferences connectionPreferences;
 private Runnable pendingBrowser, pendingEntry;
 private AudioManager audioFocus;
 private final AudioManager.OnAudioFocusChangeListener focusListener=focusChange->{
  // Renderer cues are short; nothing to pause beyond letting the system duck.
 };
 /** Ringer state for the renderer's UI cues: 0 silent, 1 vibrate, 2 normal. */
 private final class AudioRouting {
  @android.webkit.JavascriptInterface public int ringerMode() {
   try { return ((AudioManager)getSystemService(Context.AUDIO_SERVICE)).getRingerMode(); } catch (Exception e) { return 2; }
  }
 }
 private TextView setupStatus;
 private final ExecutorService executor=Executors.newFixedThreadPool(6);
 private final Map<Integer,WebSocket> sockets=new ConcurrentHashMap<>();
 private final Set<Integer> cancelledSockets=ConcurrentHashMap.newKeySet();
 private final OkHttpClient http=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build();
 private JavaScriptReplyProxy reply;
 private PermissionRequest microphoneRequest;
 private JSONObject saveCall, saveParams;
 private GatewayAuth saveAuth;
 private String saveBase, saveProfile;
 private final Handler saveTimer=new Handler(Looper.getMainLooper());
 private final Runnable cancelSavePicker=()->{if(saveCall!=null){finishActivity(81);finishSave(false,true,null);}};
 private JSONObject microphoneRpc;
 private AttachmentStore attachments;
 private volatile JSONObject pickCall;
 private GatewayAuth pickAuth;
 private String pickProfile;
 private final Runnable cancelAttachmentPicker=()->{if(pickCall!=null){finishActivity(82);finishAttachmentPick(null,null);}};
 private static final String ORIGIN="https://appassets.androidplatform.net";
 private boolean trustedOrigin(Uri origin) { return "https".equals(origin.getScheme()) && "appassets.androidplatform.net".equals(origin.getHost()) && (origin.getPort()==-1 || origin.getPort()==443); }
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  isAlive = true;
  try{attachments=new AttachmentStore(getCacheDir());}catch(IOException ignored){}
  // TEMPORARILY DISABLED for screenshot debugging
  // getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
  applyGreySystemBars();
  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  getWindow().getDecorView().setOnApplyWindowInsetsListener((v,insets)->{
   if(Build.VERSION.SDK_INT>=30){
    android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
    v.setPadding(safe.left,safe.top,safe.right,safe.bottom);
    return WindowInsets.CONSUMED;
   }
   return v.onApplyWindowInsets(insets);
  });
  connectionPreferences=new ConnectionPreferences(getSharedPreferences("connection",MODE_PRIVATE));
  String[] saved=connectionPreferences.restore();base=saved[0];primaryProfile=saved[1];
  showSetup();
  if(state!=null)setupStatus.setText("Session was cleared when Android recreated the app. Your gateway and profile are remembered; tap Sign in to reconnect.");
 }
 @Override protected void onResume(){isForeground=true;super.onResume();signIn.resume();continueSignIn();if(audioFocus!=null){try{audioFocus.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);}catch(Exception ignored){}}}
 @Override protected void onPause(){isForeground=false;if(audioFocus!=null){try{audioFocus.abandonAudioFocus(focusListener);}catch(Exception ignored){}}signIn.pause();super.onPause();}
 private void continueSignIn(){
  if(isFinishing()||isDestroyed())return;
  if(pendingBrowser!=null&&signIn.launch(setupGeneration)){Runnable launch=pendingBrowser;pendingBrowser=null;launch.run();}
  if(pendingEntry!=null&&signIn.enter(setupGeneration)){Runnable enter=pendingEntry;pendingEntry=null;enter.run();}
 }
 private void setIntField(Object o, String name, int value) {
  try { java.lang.reflect.Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.setInt(o, value); }
  catch (Exception e) { try { java.lang.reflect.Field f = o.getClass().getSuperclass().getDeclaredField(name); f.setAccessible(true); f.setInt(o, value); } catch (Exception e2) {} }
 }
 private void applyGreySystemBars() {
  int grey = Color.parseColor("#3a3a3a");
  try {
   android.view.Window w = getWindow();
   w.setBackgroundDrawable(new ColorDrawable(grey));
   setIntField(w.getDecorView().getLayoutParams(), "statusBarColor", grey);
   setIntField(w.getDecorView().getLayoutParams(), "navigationBarColor", grey);
   try { if (Build.VERSION.SDK_INT >= 30) { android.view.WindowInsetsController c = w.getInsetsController(); if (c != null) { java.lang.reflect.Method m = c.getClass().getMethod("setSystemBarsColor", boolean.class, int.class, boolean.class, int.class); m.invoke(c, true, grey, true, grey); } } } catch (Exception e) {}
  } catch (Exception e) { }
 }
 private void showSetup() {
  if(pickCall!=null)finishAttachmentPick(null,null);
  ++setupGeneration;signIn.invalidate();pendingBrowser=null;pendingEntry=null;cancelledSockets.clear();
  if(attachments!=null)executor.execute(()->attachments.close());
  LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,40,24,16); box.setBackgroundColor(Color.parseColor("#f8faff"));
  TextView title=new TextView(this); title.setText("Hermes Private — Android preview\n\nConnect Tailscale first. Sign in with your existing username and password in your phone's browser. Never put a password in the legacy token field. Native session tokens stay in memory. HTTP is only for your trusted Tailscale endpoint; HTTPS is preferred."); box.addView(title);
  EditText url=new EditText(this); url.setHint("https://your-device.your-tailnet.ts.net"); url.setText(base); url.setSingleLine(true); url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI); box.addView(url);
  Spinner mode=new Spinner(this); mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Sign in in-app (type username & password)","Sign in with browser","Legacy gateway token (ungated servers only)"}));box.addView(mode);
  EditText user=new EditText(this); user.setHint("Username"); user.setSingleLine(true); user.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PERSON_NAME); user.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES); box.addView(user);
  EditText secret=new EditText(this); secret.setHint("Password (or gateway token for legacy mode)"); secret.setSingleLine(true); secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); secret.setSaveEnabled(false); secret.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES); box.addView(secret);
  mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){user.setVisibility(position==0?View.VISIBLE:View.GONE);secret.setHint(position==2?"Gateway session token — NOT your password":"Password");}});
  mode.setSelection(0);
  EditText profile=new EditText(this); profile.setHint("Backend profile (default if blank)"); profile.setText(primaryProfile);profile.setSingleLine(true); box.addView(profile);
  TextView error=new TextView(this);setupStatus=error;box.addView(error);
  Button connect=new Button(this); connect.setText("Sign in / Connect"); box.addView(connect);
  Button cancel=new Button(this);cancel.setText("Cancel sign-in");cancel.setVisibility(View.GONE);box.addView(cancel);
  Runnable restoreControls=()->{connect.setEnabled(true);mode.setEnabled(true);url.setEnabled(true);profile.setEnabled(true);cancel.setVisibility(View.GONE);};
  cancel.setOnClickListener(v->{signIn.invalidate();if(auth!=null)auth.cancel();auth=null;showSetup();setupStatus.setText("Sign-in canceled. Your gateway and profile are remembered. Tap Sign in to retry.");});
  connect.setOnClickListener(v -> {
   try {
    String endpoint=EndpointPolicy.validateBase(url.getText().toString());
    int modePos=mode.getSelectedItemPosition();
    boolean tokenMode=modePos==2,inApp=modePos==0;
    if(tokenMode&&secret.getText().length()==0){error.setText("Enter a gateway token only for a legacy ungated server, or select in-app sign-in.");return;}
    if(inApp&&(user.getText().length()==0||secret.getText().length()==0)){error.setText("Enter your username and password, then tap Sign in.");return;}
    String owner=ConnectionPreferences.profile(profile.getText().toString());
    if(!connectionPreferences.save(endpoint,owner)){error.setText("Could not save the gateway and profile on this device. Please try again.");return;}
    base=endpoint;primaryProfile=owner;
    final int generation=++setupGeneration;signIn.begin(generation);
    final String u=user.getText().toString(),pw=secret.getText().toString();
    user.setText("");secret.setText("");
    final GatewayAuth attempt=new GatewayAuth(base,http);auth=attempt;
    connect.setEnabled(false);mode.setEnabled(false);url.setEnabled(false);user.setEnabled(false);profile.setEnabled(false);cancel.setVisibility(View.VISIBLE);
    error.setText(inApp?"Signing in in-app…":"Opening your browser. Sign in once, then tap Return to Hermes (or return to this app). Connection finishes automatically. You can cancel here; sign-in times out after 3 minutes.");
    executor.execute(()->{
     try {
      if(tokenMode)attempt.useLegacy(pw);
      else if(inApp)attempt.loginInApp(u,pw);
      else {
       String authorize=attempt.beginNative();
       runOnUiThread(()->{
        if(!signIn.active(generation)||auth!=attempt)return;
        pendingBrowser=()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(authorize)));}catch(Exception e){signIn.invalidate();attempt.cancel();auth=null;error.setText("No browser available. Install or enable a browser and try again.");restoreControls.run();}};
        continueSignIn();
       });
       attempt.finishNative(getPackageName()+".auth-return://complete");
      }
      runOnUiThread(()->{
       if(!signIn.active(generation)||auth!=attempt)return;
       signIn.complete(generation);error.setText("Signed in. Opening Hermes…");
       pendingEntry=()->{try{openRenderer();}catch(Exception e){signIn.invalidate();attempt.cancel();auth=null;if(web!=null){web.destroy();web=null;}showSetup();setupStatus.setText("Update Android System WebView, then try again.");}};
       continueSignIn();
      });
     } catch(Exception e) {final String detail=attempt.signInDiagnostic(e);attempt.cancel();runOnUiThread(()->{if(signIn.active(generation)&&auth==attempt){signIn.invalidate();pendingBrowser=null;pendingEntry=null;auth=null;error.setText(GatewayAuth.safeError(e)+"\n"+detail);restoreControls.run();}});}
    });
   } catch(Exception e) { error.setText("Enter a valid Tailscale gateway URL without credentials, query or fragment, and a profile name without slashes or control characters."); }
  });
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.parseColor("#f8faff"));scroll.addView(box);setContentView(scroll);
 }
 private void openRenderer() {
  if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) throw new IllegalStateException("Update Android System WebView to use this app");
  web=new WebView(this); web.setSaveEnabled(false);
  // UI cues route through a media-stream sink in the renderer; take media
  // audio focus so they pause with calls and route to Bluetooth like media —
  // TRANSIENT_MAY_DUCK, not GAIN: GAIN is exclusive and indefinite, so opening
  // the app alone paused every other app's playback until the app backgrounded.
  audioFocus=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
  try { audioFocus.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK); } catch (Exception ignored) {}
  web.addJavascriptInterface(new AudioRouting(), "androidAudio");
  WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMediaPlaybackRequiresUserGesture(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  WebView.setWebContentsDebuggingEnabled(false);
  WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebViewClient(new WebViewClient() {
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r) {
    if(r.getUrl().toString().startsWith(ORIGIN+"/")) return assets.shouldInterceptRequest(r.getUrl());
    // All authenticated network access belongs to the scoped native transport.
    return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r) { return !r.getUrl().toString().startsWith(ORIGIN+"/"); }
  });
  web.setWebChromeClient(new WebChromeClient() {
   @Override public boolean onJsAlert(WebView view,String message,String title,JsResult result){
    // Debug breadcrumbs from the renderer land here as native toasts.
    android.widget.Toast.makeText(view.getContext(),message,android.widget.Toast.LENGTH_SHORT).show();
    result.confirm();
    return true;
   }
   @Override public void onPermissionRequest(PermissionRequest request) {
    if(!trustedOrigin(request.getOrigin()) || !Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {request.deny();return;}
    microphoneRequest=request;
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});microphoneRequest=null;}
    else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7);
   }
  });
  WebViewCompat.addWebMessageListener(web,"HermesNative",Collections.singleton(ORIGIN),(view,message,origin,mainFrame,proxy) -> {
   if(view!=web || !mainFrame || !trustedOrigin(origin)) return;
   reply=proxy;
   try {
    JSONObject call=new JSONObject(message.getData());call.put("_generation",setupGeneration);
    // Socket writes must retain JS submission order; REST runs concurrently.
    if(call.optString("method").startsWith("socket.")&&!call.optString("method").equals("socket.open")) handle(call);
    else executor.execute(() -> handle(call));
   }
   catch(Exception ignored) { /* Malformed untrusted message cannot invoke native code. */ }
  });
  setContentView(web); web.loadUrl(ORIGIN+"/index.html");
 }
 private JSONObject object(Object... pairs) throws JSONException {
  JSONObject result=new JSONObject(); for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]); return result;
 }
 private void post(JSONObject message) {runOnUiThread(() -> {if(reply!=null) reply.postMessage(message.toString());});}
 private void success(JSONObject call,Object value) {if(call.optInt("_generation",-1)!=setupGeneration)return;try {post(object("id",call.getInt("id"),"result",value==null?JSONObject.NULL:value));}catch(JSONException ignored){}}
 private void failure(JSONObject call,String message) {if(call.optInt("_generation",-1)!=setupGeneration)return;try{post(object("id",call.getInt("id"),"error",message));}catch(JSONException ignored){}}
 private String profile(JSONObject params) {String p=params.optString("profile","");return p.isEmpty()||p.equals("null")?primaryProfile:p;}
 private void scope(JSONObject p) {
  String id=p.optString("connectionId",""); if(!id.isEmpty()&&!id.equals("null")&&!id.equals("android-remote")) throw new IllegalArgumentException("Unknown connection: refusing cross-profile retargeting");
 }
 private static final class GatewayHttpException extends IOException {
  final int status;
  GatewayHttpException(int status) { super("Gateway HTTP " + status); this.status=status; }
 }
 private void handle(JSONObject call) {
  if(call.optInt("_generation",-1)!=setupGeneration)return;
  try {
   String method=call.getString("method"); JSONObject p=call.optJSONObject("params");if(p==null)p=new JSONObject();
   switch(method) {
    case "connection": {
     scope(p); String profile=profile(p);
     String ws=base.replaceFirst("^http","ws")+"/api/ws?profile="+Uri.encode(profile);
     success(call,object("baseUrl",base,"token","","wsUrl",ws,"profile",profile,"mode","remote","authMode",tokenMode?"token":"oauth","remoteKind","url","source","settings","connectionId","android-remote","registryScoped",true,"sharedRemote",true,"isFullscreen",false,"nativeOverlayWidth",0,"windowButtonPosition",JSONObject.NULL,"logs",new JSONArray())); break;
    }
    case "download.file": case "download.image":
     if(call.optJSONObject("params")==null){failure(call,DownloadFailure.safe(DownloadFailure.Stage.INPUT,new IllegalArgumentException()));break;}
     startSave(call,p);break;
    case "media.read": {
     scope(p); final GatewayAuth session=auth; final int generation=setupGeneration;
     success(call,GatewayMedia.read(session,base,p,profile(p),()->auth==session&&setupGeneration==generation));break;
    }
    case "api": success(call,api(p));break;
    case "attachment.pick": startAttachmentPick(call,p);break;
    case "attachment.read": {
     scope(p);final GatewayAuth session=auth;final int generation=setupGeneration;
     try {if(attachments==null)throw new IOException();success(call,attachments.read(p.getString("path"),profile(p),session,()->auth==session&&generation==setupGeneration));}
     catch(Exception e){failure(call,"Attachment unavailable for this owner; choose the file again");}break;
    }
    case "socket.open": openSocket(call,p);break;
    case "socket.send": {WebSocket socket=sockets.get(p.getInt("id"));if(socket==null||!socket.send(p.getString("data")))throw new IOException("Socket unavailable");success(call,true);break;}
    case "socket.close": {synchronized(sockets){int id=p.getInt("id");cancelledSockets.add(id);WebSocket socket=sockets.remove(id);if(socket!=null)socket.close(p.optInt("code",1000),p.optString("reason",""));}success(call,true);break;}
    case "clipboard.read": runOnUiThread(() -> {android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);success(call,c.hasPrimaryClip()?c.getPrimaryClip().getItemAt(0).coerceToText(this).toString():"");});break;
    case "clipboard.write": {String text=p.getString("text");runOnUiThread(() -> {((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Hermes",text));success(call,true);});break;}
    case "external": {Uri uri=Uri.parse(p.getString("url"));if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))throw new IllegalArgumentException("Only HTTP links are allowed");runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Open external link?").setMessage(uri.getHost()).setNegativeButton("Cancel",(d,w)->success(call,false)).setPositiveButton("Open",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,uri));success(call,true);}catch(Exception e){failure(call,"No browser available");}}).show());break;}
    case "keepAwake": {boolean enabled=p.optBoolean("enabled");runOnUiThread(()->{web.setKeepScreenOn(enabled);success(call,true);});break;}
    case "microphone": runOnUiThread(() -> {if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)success(call,true);else {microphoneRpc=call;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7);}});break;
    case "push.enable": case "push.disable": {
     boolean on = "push.enable".equals(method);
     GatewayAuth session=auth;
     if(session==null){failure(call,"Not signed in yet");break;}
     PushService.PushServiceBridge.set(session);
     String prof=profile(p);
     Intent i=new Intent(this,PushService.class).putExtra(PushService.KEY_PROFILE,prof).putExtra(PushService.KEY_ENABLED,on);
     if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
     success(call,object("status",on?"started":"stopped"));break;
    }
    case "push.log": {
     // Read the PushService log file and return it
     try {
      java.io.File dir = getExternalFilesDir(null);
      if (dir == null) dir = getFilesDir();
      java.io.File logFile = new java.io.File(dir, "push_service.log");
      if (!logFile.exists()) {
       success(call, "No log file yet (PushService may not have started)");
       break;
      }
      java.util.Scanner sc = new java.util.Scanner(logFile, "UTF-8");
      StringBuilder sb = new StringBuilder();
      while (sc.hasNextLine()) sb.append(sc.nextLine()).append("\n");
      sc.close();
      // Cap at 8000 chars to keep the RPC response small
      String content = sb.toString();
      if (content.length() > 8000) content = content.substring(content.length() - 8000);
      success(call, content);
     } catch (Exception e) {
      failure(call, "Could not read push log: " + e.getMessage());
     }
     break;
    }
    default: failure(call,"Native capability is not implemented: "+method);
   }
  } catch(Exception e) {
   // Never return exception URLs/headers: WS URLs contain session credentials.
   failure(call,e instanceof GatewayAuth.AuthException?GatewayAuth.safeError(e):e instanceof GatewayHttpException?"Gateway HTTP "+((GatewayHttpException)e).status:e instanceof IllegalArgumentException?"Request refused by Android endpoint policy":"Native request failed ("+e.getClass().getSimpleName()+")");
  }
 }
 private void startAttachmentPick(JSONObject call,JSONObject p) {
  scope(p);final GatewayAuth session=auth;final String owner=profile(p);
  runOnUiThread(()->{
   if(call.optInt("_generation",-1)!=setupGeneration||session==null||session!=auth)return;
   if(pickCall!=null){failure(call,"Another attachment selection is already open");return;}
   if(p.optBoolean("directories")){failure(call,"Choose individual files on Android");return;}
   try {
    if(attachments==null)attachments=new AttachmentStore(getCacheDir());
    pickCall=call;pickAuth=session;pickProfile=owner;
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
      .setType(p.optBoolean("images")?"image/*":"*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    // One document per pick bounds memory and keeps failures atomic.
    startActivityForResult(intent,82);saveTimer.postDelayed(cancelAttachmentPicker,300000);
   }catch(Exception e){finishAttachmentPick(null,"Could not open the Android file picker");}
  });
 }
 private void finishAttachmentPick(String handle,String error) {
  JSONObject call=pickCall;pickCall=null;pickAuth=null;saveTimer.removeCallbacks(cancelAttachmentPicker);
  if(call==null)return;
  if(error!=null)failure(call,error);else {JSONArray result=new JSONArray();if(handle!=null)result.put(handle);success(call,result);}
 }
 private void attachmentPickerResult(int resultCode,Intent data) {
  if(pickCall==null)return;
  if(resultCode==RESULT_CANCELED){finishAttachmentPick(null,null);return;}
  if(resultCode!=RESULT_OK||data==null||data.getData()==null||!"content".equals(data.getData().getScheme())){finishAttachmentPick(null,"Could not read the selected document");return;}
  final JSONObject call=pickCall;final GatewayAuth session=pickAuth;final String owner=pickProfile;final Uri uri=data.getData();
  executor.execute(()->{
   String handle=null,error=null;
   java.util.function.BooleanSupplier active=()->pickCall==call&&auth==session&&call.optInt("_generation",-1)==setupGeneration;
   try {
    if(!active.getAsBoolean())throw new IOException();
    String name="attachment";
    try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE},null,null,null)){
     if(cursor!=null&&cursor.moveToFirst()){
      int n=cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(n>=0)name=cursor.getString(n);
      int size=cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);if(size>=0&&!cursor.isNull(size)&&cursor.getLong(size)>AttachmentStore.MAX_BYTES)throw new IOException("Attachment too large (limit 8388608 bytes)");
     }
    }
    handle=attachments.stage(getContentResolver().openInputStream(uri),name,getContentResolver().getType(uri),owner,session,active);
   }catch(Exception e){error=e instanceof IOException&&"Attachment too large (limit 8388608 bytes)".equals(e.getMessage())?e.getMessage():"Could not stage attachment (8 MiB per file, 32 MiB per connection); choose the file again";}
   final String selected=handle,message=error;
   runOnUiThread(()->{if(pickCall==call&&active.getAsBoolean())finishAttachmentPick(selected,message);else {if(selected!=null)executor.execute(()->attachments.discard(selected));if(pickCall==call)finishAttachmentPick(null,null);}});
  });
 }
 private void startSave(JSONObject call,JSONObject p) {
  final GatewayAuth session=auth;final String endpoint=base,owner=profile(p);
  runOnUiThread(()->{
   if(call.optInt("_generation",-1)!=setupGeneration||session!=auth)return;
   if(saveCall!=null){failure(call,DownloadFailure.safe(DownloadFailure.Stage.BUSY,new IllegalStateException()));return;}
   saveCall=call;saveParams=p;saveAuth=session;saveBase=endpoint;saveProfile=owner;
   DownloadFailure.Stage stage=DownloadFailure.Stage.INPUT;
   try {
    GatewayDownload.text(p,"connectionId",false);GatewayDownload.text(p,"profile",false);scope(p);
    boolean image=call.optString("method").equals("download.image");
    if(!image)GatewayDownload.fileUrl(endpoint,p,owner);
    String source=image?GatewayDownload.imageName(GatewayDownload.text(p,"url",true)):GatewayDownload.text(p,"path",true);
    String name=GatewayDownload.filename(GatewayDownload.text(p,"suggestedName",false),source);
    stage=DownloadFailure.Stage.PICKER_LAUNCH;
    if(isFinishing()||isDestroyed())throw new IllegalStateException();
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(image?"image/*":"application/octet-stream").putExtra(Intent.EXTRA_TITLE,name);
    startActivityForResult(intent,81);saveTimer.postDelayed(cancelSavePicker,300000);
   }catch(Exception e){finishSave(false,false,DownloadFailure.safe(stage,e));}
  });
 }
 private void finishSave(boolean saved,boolean canceled,String error) {
  JSONObject call=saveCall;saveCall=null;saveParams=null;saveAuth=null;saveTimer.removeCallbacks(cancelSavePicker);
  if(call==null)return;
  if(error!=null){failure(call,error);return;}
  try{success(call,call.optString("method").equals("download.image")?saved:object("saved",saved,"canceled",canceled));}catch(JSONException ignored){}
 }
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode==82){attachmentPickerResult(resultCode,data);return;}
  if(requestCode!=81||saveCall==null)return;
  saveTimer.removeCallbacks(cancelSavePicker);
  if(resultCode==RESULT_CANCELED){finishSave(false,true,null);return;}
  if(resultCode!=RESULT_OK||data==null||data.getData()==null){finishSave(false,false,DownloadFailure.safe(DownloadFailure.Stage.PICKER_RESULT,new IOException()));return;}
  final Uri destination=data.getData();final JSONObject call=saveCall,p=saveParams;final GatewayAuth session=saveAuth;final String endpoint=saveBase,owner=saveProfile;
  if(!"content".equals(destination.getScheme())){finishSave(false,false,DownloadFailure.safe(DownloadFailure.Stage.PICKER_RESULT,new IOException()));return;}
  try {executor.execute(()->{
   String error=null;boolean saved=false;
   java.util.function.BooleanSupplier active=()->auth==session&&call.optInt("_generation",-1)==setupGeneration;
   try {
    GatewaySave.save(getCacheDir(),out->{
     if(call.optString("method").equals("download.image"))GatewayDownload.image(session,endpoint,p.getString("url"),owner,out,active);
     else GatewayDownload.file(session,GatewayDownload.fileUrl(endpoint,p,owner),out,active);
    },()->getContentResolver().openOutputStream(destination,"wt"),active);
    saved=true;
   }catch(Exception e){error=DownloadFailure.safe(DownloadFailure.Stage.WORKER,e);}
   finally {
    if(!saved)try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}
   }
   final boolean ok=saved;final String message=error;
   runOnUiThread(()->{if(saveCall==call)finishSave(ok,false,message);});
  });}catch(Exception e){finishSave(false,false,DownloadFailure.safe(DownloadFailure.Stage.WORKER,e));}
 }
 private Object api(JSONObject p) throws Exception {
  scope(p); String path=p.getString("path");
  if(!path.startsWith("/api/")||path.contains("..")||path.contains("\\"))throw new IllegalArgumentException("Invalid API path");
  HttpUrl.Builder url=HttpUrl.get(base+path).newBuilder(); if(!p.isNull("profile")&&p.has("profile"))url.setQueryParameter("profile",profile(p));
  EndpointPolicy.requireSameEndpoint(base,url.build().toString());
  if(p.has("upload"))throw new IllegalArgumentException("File upload adapter not implemented");
  String method=p.optString("method","GET");RequestBody body=null;
  if(!method.equals("GET")&&!method.equals("HEAD"))body=RequestBody.create(p.has("body")?p.get("body").toString():"",MediaType.get("application/json"));
  Request request=new Request.Builder().url(url.build()).method(method,body).build();
  try(Response response=auth.request(request)) {
   if(!response.isSuccessful())throw new GatewayHttpException(response.code());
   String data=response.body()==null?"":response.body().string();
   return data.isEmpty()?JSONObject.NULL:new JSONTokener(data).nextValue();
  }
 }
 private void openSocket(JSONObject call,JSONObject p) throws Exception {
  String url=p.getString("url"); EndpointPolicy.requireSameEndpoint(base,url);
  int id=p.getInt("id");
  GatewayAuth session=auth;
  Request.Builder request=new Request.Builder().url(session.socketUrl(url));
  Object protocols=p.opt("protocols");
  if(protocols instanceof JSONArray) {JSONArray a=(JSONArray)protocols;List<String> values=new ArrayList<>();for(int i=0;i<a.length();i++)values.add(a.getString(i));request.header("Sec-WebSocket-Protocol",String.join(", ",values));}
  else if(protocols instanceof String)request.header("Sec-WebSocket-Protocol",(String)protocols);
  synchronized(sockets) {
  if(auth!=session||cancelledSockets.remove(id))throw new IOException("Socket cancelled");
  WebSocket socket=http.newWebSocket(request.build(),new WebSocketListener() {
   private void event(String type,Object... extra) {if(auth!=session)return;try{JSONObject m=object("socket",id,"event",type);for(int i=0;i<extra.length;i+=2)m.put((String)extra[i],extra[i+1]);post(m);}catch(JSONException ignored){}}
   @Override public void onOpen(WebSocket ws,Response response){event("open");}
   @Override public void onMessage(WebSocket ws,String text){event("message","data",text);}
   @Override public void onMessage(WebSocket ws,okio.ByteString bytes){event("message","data",bytes.base64(),"binary",true);}
   @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);}
   @Override public void onClosed(WebSocket ws,int code,String reason){sockets.remove(id,ws);event("close","code",code,"reason",reason);}
   @Override public void onFailure(WebSocket ws,Throwable error,Response response){sockets.remove(id,ws);event("error");event("close","code",1006,"reason","Private gateway connection failed");}
  });
  sockets.put(id,socket);success(call,true);
  // Sign-in is complete and auth is valid: start the background push listener.
  ensurePushService(p);
  }
  }
 /** Start the background reply-notification service. Requests the Android 13+
  *  notification permission first (a one-time system prompt), then hands the
  *  live auth object to PushService and starts it. Called on socket.open and
  *  via the push.enable bridge. Safe to call repeatedly. */
 private void ensurePushService(JSONObject params) {
  if (Looper.myLooper() != Looper.getMainLooper()) {
   runOnUiThread(() -> ensurePushService(params));
   return;
  }
  if (auth == null || isFinishing() || isDestroyed()) return;
  String prof = profile(params);
  PushService.PushServiceBridge.set(auth);
  if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
   requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8);
   // Start the service anyway; it shows the low-key "running" indicator and
   // will post reply notifications once the permission is granted.
  }
  Intent i = new Intent(this, PushService.class).putExtra(PushService.KEY_PROFILE, prof).putExtra(PushService.KEY_ENABLED, true);
  if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
  else startService(i);
 }
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
  super.onRequestPermissionsResult(requestCode,permissions,results);
  boolean allowed=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
  if(microphoneRequest!=null){if(allowed)microphoneRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else microphoneRequest.deny();microphoneRequest=null;}
  if(microphoneRpc!=null){success(microphoneRpc,allowed);microphoneRpc=null;}
 }
 @Override public void onBackPressed(){if(web!=null){web.evaluateJavascript("document.documentElement.dataset.phoneBack === 'true'",result->{if("true".equals(result))web.evaluateJavascript("window.dispatchEvent(new Event('hermes:phone-back'))",null);else confirmDisconnect();});}else confirmDisconnect();}
 private void confirmDisconnect(){new AlertDialog.Builder(this).setMessage("Disconnect and clear this app's in-memory session?").setNegativeButton("Cancel",null).setPositiveButton("Disconnect",(d,w)->{finishSave(false,true,null);setupGeneration++;PushService.PushServiceBridge.set(null);stopService(new Intent(this,PushService.class));if(auth!=null)auth.cancel();auth=null;reply=null;synchronized(sockets){for(WebSocket s:sockets.values())s.cancel();sockets.clear();}if(web!=null){web.destroy();web=null;}showSetup();}).show();}
 @Override protected void onDestroy(){isAlive=false;isForeground=false;finishAttachmentPick(null,null);signIn.invalidate();pendingBrowser=null;pendingEntry=null;finishSave(false,true,null);setupGeneration++;PushService.PushServiceBridge.releaseActivity(auth);auth=null;reply=null;synchronized(sockets){for(WebSocket s:sockets.values())s.cancel();sockets.clear();}executor.shutdownNow();if(attachments!=null){Thread cleanup=new Thread(()->attachments.close(),"attachment-cleanup");cleanup.setDaemon(true);cleanup.start();}if(audioFocus!=null){try{audioFocus.abandonAudioFocus(focusListener);}catch(Exception ignored){}}if(web!=null)web.destroy();super.onDestroy();}
}
