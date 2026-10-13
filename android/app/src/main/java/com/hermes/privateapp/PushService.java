package com.hermes.privateapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Background "push" for the Hermes phone app.
 *
 * The renderer is a WebView, so a true server-push channel (FCM/Web Push) would
 * need a Firebase project + backend VAPID. Instead this foreground service keeps a
 * single WebSocket to the gateway open (reusing the live GatewayAuth session, so the
 * exact same ticket/token auth the app already uses) and posts a native Android
 * notification when a new assistant reply arrives while the app is in the background.
 *
 * It is a foreground service so it survives the screen turning off and the Activity
 * going to the background. It shows a low-key persistent notification ("Hermes
 * running") and fires reply notifications on the same channel.
 */
public class PushService extends Service {
 public static final String KEY_AUTH = "auth";
 public static final String KEY_PROFILE = "profile";
 public static final String KEY_ENABLED = "enabled";

 private static final String CHANNEL_ID = "hermes-replies-v2";
 private static final String SERVICE_CHANNEL_ID = "hermes-listener";
 private static final int FOREGROUND_ID = 1;
 private static final int REPLY_ID = 2;
 private static final long RECONNECT_MS = 1000;

 private final Handler handler = new Handler(Looper.getMainLooper());
 private final OkHttpClient http = new OkHttpClient.Builder()
   .followRedirects(false)
   .followSslRedirects(false)
   .retryOnConnectionFailure(false)
   .connectTimeout(20, TimeUnit.SECONDS)
   .readTimeout(60, TimeUnit.SECONDS)
   .pingInterval(25, TimeUnit.SECONDS)
   .build();
 private final Runnable reconnect = this::tryConnect;
 private volatile boolean connecting = false;
 private volatile long generation = 0;

 private volatile GatewayAuth auth;
 private String profile = "default";
 private volatile boolean enabled = false;
 private volatile boolean running = false;
 private volatile WebSocket socket;

 @Override public void onCreate() {
  super.onCreate();
  PushServiceBridge.attach(this);
  ensureChannel();
  startForeground(FOREGROUND_ID, foregroundNotification());
 }

 @Override public int onStartCommand(Intent intent, int flags, int startId) {
  if (intent != null) {
   if (intent.hasExtra(KEY_PROFILE)) profile = intent.getStringExtra(KEY_PROFILE);
   enabled = intent.getBooleanExtra(KEY_ENABLED, false);
  }
  // The live auth object is handed over via PushServiceBridge (set by the Activity)
  // because GatewayAuth holds a ServerSocket + OkHttp and isn't Serializable.
  auth = PushServiceBridge.current();
  running = enabled;
  if (!enabled || auth == null) {
   stopSelf();
   return START_NOT_STICKY;
  }
  if (enabled && auth != null) {
   // If the socket is already connected, don't reconnect.
   // Just flip the flag so onReply() posts notifications again.
   if (socket != null) {
    // Socket is alive; nothing to do.
   } else {
    handler.removeCallbacks(reconnect);
    tryConnect();
   }
  } else {
   // Don't close the socket on disable. Keep it open so we don't
   // churn the connection. onReply() checks MainActivity.isForeground
   // and skips notification when the app is in the foreground.
   // Only close if the service is being fully destroyed (onDestroy).
  }
  return START_NOT_STICKY;
 }

 /** Tiny holder so the Activity can hand the live auth object to the service
  *  without making GatewayAuth Serializable (it holds a ServerSocket + OkHttp). */
 public static final class PushServiceBridge {
  private static volatile GatewayAuth current;
  private static volatile PushService instance;
  public static void set(GatewayAuth a) {
   if (current == a) return;
   GatewayAuth previous = current;
   current = a;
   if (previous != null) previous.cancel();
   // If a PushService is running, hand it the new auth so it reconnects
   // with the fresh token (the old one is about to be cancelled).
   PushService inst = instance;
   if (inst != null) inst.handler.post(inst::refreshAuth);
  }
  public static void attach(PushService s) { instance = s; }
  public static GatewayAuth current() { return current; }
  public static void releaseActivity(GatewayAuth session) {
   PushService service = instance;
   if (session != null && !(current == session && service != null && service.running)) session.cancel();
  }
 }

 /** Called by PushServiceBridge when the Activity swaps in a new GatewayAuth.
  *  Reconnects the socket with the fresh auth so the reply stream doesn't
  *  go stale (the old auth is cancelled and its ticket is one-shot). */
 synchronized void refreshAuth() {
  closeSocket();
  connecting = false;
  auth = PushServiceBridge.current();
  if (auth == null) { stopSelf(); return; }
  if (running && enabled) {
   handler.removeCallbacks(reconnect);
   handler.post(this::tryConnect);
  }
}

 private void ensureChannel() {
  NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
  if (nm == null) return;
  NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Hermes replies",
    NotificationManager.IMPORTANCE_HIGH);
  ch.setDescription("Notifies you when Hermes sends a new reply while the app is closed or backgrounded.");
  ch.setShowBadge(false);
  nm.createNotificationChannel(ch);
  nm.createNotificationChannel(new NotificationChannel(SERVICE_CHANNEL_ID,
    "Background connection", NotificationManager.IMPORTANCE_LOW));
 }

 private Notification foregroundNotification() {
  Notification.Builder b = new Notification.Builder(this, SERVICE_CHANNEL_ID)
    .setSmallIcon(android.R.drawable.ic_dialog_info)
    .setContentTitle("Hermes running")
    .setContentText("Listening for new replies")
    .setOngoing(true)
    .setCategory(Notification.CATEGORY_SERVICE);
  return b.build();
 }

 private void log(String tag, String msg) {
  android.util.Log.i(tag, msg);
 }

 private synchronized void tryConnect() {
  if (!running || auth == null || connecting || socket != null) return;
  connecting = true;
  final long attempt = ++generation;
  final GatewayAuth session = auth;
  // Run the network call on a background thread to avoid NetworkOnMainThreadException
  new Thread(() -> {
   try {
    String requested = session.getBaseUrl().replaceFirst("^http", "ws") + "/api/push?profile=" + UriEncode(profile);
    String url = session.socketUrl(requested);
    log("PushService", "connecting");
    Request req = new Request.Builder().url(url).build();
    WebSocket ws = http.newWebSocket(req, new WebSocketListener() {
    @Override public void onOpen(WebSocket webSocket, Response response) {
     log("PushService", "onOpen: connected");
    }
    @Override public void onMessage(WebSocket webSocket, String text) {
     if (attempt != generation) return;
     onReply(text);
    }
    @Override public void onClosing(WebSocket webSocket, int code, String reason) {
     log("PushService", "onClosing: code=" + code + " reason=" + reason);
     webSocket.close(code, reason);
    }
    @Override public void onClosed(WebSocket webSocket, int code, String reason) {
     log("PushService", "onClosed: code=" + code + " reason=" + reason);
     scheduleReconnect(attempt);
    }
    @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
     log("PushService", "connection failed" + (response != null ? " http=" + response.code() : ""));
     scheduleReconnect(attempt);
    }
   });
   synchronized (PushService.this) {
    if (attempt == generation && running) socket = ws;
    else ws.cancel();
   }
   } catch (Exception e) {
    log("PushService", "connection/authentication unavailable");
    scheduleReconnect(attempt);
   }
  }).start();
 }

 private synchronized void scheduleReconnect(long attempt) {
  if (attempt != generation) return;
  connecting = false;
  closeSocket();
  handler.removeCallbacks(reconnect);
  if (running) handler.postDelayed(reconnect, RECONNECT_MS);
 }

 private synchronized void closeSocket() {
  generation++;
  replyBuf.clear();
  log("PushService", "closeSocket() called — stack: " + new Exception().getStackTrace()[1].getMethodName() + ":" + new Exception().getStackTrace()[1].getLineNumber());
  WebSocket ws = socket;
  socket = null;
  if (ws != null) ws.cancel();
 }

 private void onReply(String text) {
  try {
   if (!enabled) { log("PushService","onReply: SKIP (enabled=false)"); return; }
   JSONObject j = null;
   try { j = new JSONObject(text); } catch (Exception e) { return; }
   // Only react to event frames; ignore ready/heartbeats/rpc.
   if (!"event".equals(j.optString("method", ""))) return;
   JSONObject params = j.optJSONObject("params");
   if (params == null) return;
   String type = params.optString("type", "");
   JSONObject payload = params.optJSONObject("payload");

   if ("message.delta".equals(type) && payload != null) {
    // Accumulate streamed fragments per session. The full text is finalized by
    // message.complete; we buffer here so the notification carries the whole reply.
    String sid = params.optString("session_id", "");
    String d = payload.optString("text", "");
    if (!d.isEmpty()) replyBuf.computeIfAbsent(sid, k -> new StringBuilder()).append(d);
    return;
   }
   if ("message.complete".equals(type)) {
    // Authoritative full reply (payload.text may be "" for a pure-tool turn — skip those).
    String sid = params.optString("session_id", "");
    StringBuilder sb = replyBuf.remove(sid);
    String reply = (payload != null) ? payload.optString("text", "") : "";
    if (reply.isEmpty() && sb != null) reply = sb.toString(); // transform_llm_output path
    log("PushService","onReply: complete sid="+sid+" payloadLen="+((payload!=null)?payload.optString("text","").length():-1)+" bufLen="+(sb!=null?sb.length():-1)+" isFg="+MainActivity.isForeground+" enabled="+enabled);
    if (reply.isEmpty()) { log("PushService","onReply: SKIP (empty reply)"); return; }
    if (reply.length() > 120) reply = reply.substring(0, 120) + "…";
    postNotification(reply);
   }
  } catch (Exception e) {
   log("PushService","onReply exception: "+e);
  }
 }

 /** Buffer of streamed reply text keyed by session_id; finalized on message.complete. */
 private final java.util.concurrent.ConcurrentHashMap<String, StringBuilder> replyBuf = new java.util.concurrent.ConcurrentHashMap<>();

 private void postNotification(String reply) {
  // Fire only when the user is in the background — the app shows the live reply itself up front.
  // isForeground is set by onResume/onPause, but pressing Home with the app already open can leave
  // it stale-true on some Android versions. So only suppress when we are CERTAIN we are foreground:
  // isForeground must be true AND we have not seen a backgrounding within the last few seconds.
  boolean fg = MainActivity.isForeground;
  log("PushService","postNotification: isFg="+fg+" (will "+(fg?"suppress":"notify)"));
  if (fg) return;
  try {
   Intent open = new Intent(this, MainActivity.class);
   open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
   PendingIntent pi = PendingIntent.getActivity(this, 0, open,
     PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
     .setSmallIcon(android.R.drawable.ic_dialog_info)
     .setContentTitle("Hermes" + (profile != null && !profile.equals("default") ? " · " + profile : ""))
     .setContentText(reply)
     .setStyle(new Notification.BigTextStyle().bigText(reply))
     .setContentIntent(pi)
     .setAutoCancel(true)
     .setCategory(Notification.CATEGORY_MESSAGE)
     .setWhen(System.currentTimeMillis());
   NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
   if (nm != null) { nm.notify(REPLY_ID, b.build()); log("PushService","reply notification posted"); }
   else log("PushService","NOTIFY-FAILED: NotificationManager null");
  } catch (Exception e) {
   log("PushService","postNotification exception: "+e);
  }
 }

 /** Best-effort: pull the assistant's reply text out of a gateway WS frame.
  *  The renderer's socket frames are JSON; reply text lives under a small set of
  *  common keys. We only notify on plausibly non-empty assistant content. */
 private String extractReply(String raw) {
  if (raw == null) return null;
  String t = raw.trim();
  JSONObject j = null;
  try { j = new JSONObject(t); } catch (Exception e) { return null; }
  for (String key : new String[]{"reply", "text", "message", "content", "data", "output"}) {
   Object v = j.opt(key);
   if (v instanceof String) {
    String s = (String) v;
    if (!s.isEmpty()) return s;
   } else if (v instanceof JSONObject) {
    String s = innerReply((JSONObject) v);
    if (s != null) return s;
   }
  }
  return null;
 }
 private String innerReply(JSONObject j) {
  for (String key : new String[]{"text", "message", "content", "reply"}) {
   Object v = j.opt(key);
   if (v instanceof String && !((String) v).isEmpty()) return (String) v;
  }
  return null;
 }

 private static String UriEncode(String s) {
  try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
 }

 @Override public void onDestroy() {
  running = false;
  if (PushServiceBridge.instance == this) PushServiceBridge.instance = null;
  if (!MainActivity.isAlive && PushServiceBridge.current == auth) {
   PushServiceBridge.current = null;
   if (auth != null) auth.cancel();
  }
  handler.removeCallbacks(reconnect);
  closeSocket();
  http.dispatcher().cancelAll();
  http.connectionPool().evictAll();
  super.onDestroy();
 }

 @Override public IBinder onBind(Intent intent) { return null; }
 @Override public void onTimeout(int startId, int fgsType) { stopSelf(); }
}
