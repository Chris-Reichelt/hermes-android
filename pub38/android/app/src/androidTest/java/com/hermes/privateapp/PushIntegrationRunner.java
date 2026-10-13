package com.hermes.privateapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

public class PushIntegrationRunner extends Instrumentation {
 private final OkHttpClient client = new OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build();
 private String base;
 private Context target;
 private NotificationManager notifications;
 private Activity activity;
 private GatewayAuth auth;
 private int channelFailures;
 private boolean skipInitialReconnect;
 private long holdBackgroundMs;
 private final StringBuilder results = new StringBuilder();
 private final String runId = java.util.UUID.randomUUID().toString();

 @Override public void onCreate(Bundle arguments) {
  super.onCreate(arguments);
  base = arguments == null ? "http://127.0.0.1:18765" : arguments.getString("fixture", "http://127.0.0.1:18765");
  skipInitialReconnect = arguments != null && "true".equals(arguments.getString("skipInitialReconnect"));
  holdBackgroundMs = arguments == null ? 0 : Math.min(15000, Math.max(0, Long.parseLong(arguments.getString("holdBackgroundMs", "0"))));
  start();
 }

 @Override public void onStart() {
  Bundle result = new Bundle();
  int code = Activity.RESULT_CANCELED;
  try {
   if (!base.matches("http://(127\\.0\\.0\\.1|10\\.0\\.2\\.2):[0-9]+")) throw new AssertionError("Fixture must be local emulator transport");
   target = getTargetContext();
   notifications = target.getSystemService(NotificationManager.class);
   if (!notifications.areNotificationsEnabled()) throw new AssertionError("Grant POST_NOTIFICATIONS before running");
   activity = startActivitySync(new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   await(() -> MainActivity.isForeground, "MainActivity foreground");
   notifications.cancel(2);
   auth = new GatewayAuth(base, client);
   auth.useLegacy("synthetic-push-test");
   java.lang.reflect.Field activityAuth = MainActivity.class.getDeclaredField("auth");
   activityAuth.setAccessible(true);
   runOnMainSync(() -> {
    try { activityAuth.set(activity, auth); }
    catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    PushService.PushServiceBridge.set(auth);
    target.startForegroundService(new Intent(target, PushService.class)
      .putExtra(PushService.KEY_ENABLED, true).putExtra(PushService.KEY_PROFILE, "default"));
   });
   await(() -> request("/state", false).getInt("active") == 1, "real PushService websocket connected");
   suppressed("foreground");
   target.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   await(() -> !MainActivity.isForeground, "Home backgrounds MainActivity");
   posted("background");
   SystemClock.sleep(holdBackgroundMs);
   posted("delta");
   if (!skipInitialReconnect) {
   int connections = request("/state", false).getInt("connections");
   request("/close", true);
   await(() -> {
    JSONObject state = request("/state", false);
    return state.getInt("connections") > connections && state.getInt("active") == 1;
   }, "server-close reconnect");
   posted("reconnected");
   } else results.append("SKIP initial reconnect (isolated Activity-destroy probe)\n");
   target.startActivity(new Intent(target, MainActivity.class)
     .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
   await(() -> MainActivity.isForeground, "return foreground");
   suppressed("returned");
   int originalPid = android.os.Process.myPid();
   runOnMainSync(() -> activity.finish());
   await(() -> activity.isDestroyed() && !MainActivity.isForeground, "Activity destroyed in same process");
   int beforeDestroyReconnect = request("/state", false).getInt("connections");
   request("/close", true);
   await(() -> {
    JSONObject state = request("/state", false);
    return state.getInt("connections") > beforeDestroyReconnect && state.getInt("active") == 1;
   }, "reconnect after Activity onDestroy (shared auth must remain usable)");
   if (android.os.Process.myPid() != originalPid) throw new AssertionError("Target process changed");
   posted("destroyed");
   if (channelFailures != 0) throw new AssertionError(channelFailures + " HIGH reply channel checks failed; other scenarios completed");
   code = Activity.RESULT_OK;
   results.append(skipInitialReconnect ? "PASS isolated Activity-destroy probe\n" : "PASS all six native push scenarios\n");
  } catch (Throwable failure) {
   results.append("FAIL ").append(android.util.Log.getStackTraceString(failure));
  } finally {
   try {
    if (target != null) runOnMainSync(() -> {
     target.stopService(new Intent(target, PushService.class));
     PushService.PushServiceBridge.set(null);
     if (activity != null) activity.finish();
    });
    if (auth != null) auth.cancel();
    if (notifications != null) notifications.cancel(2);
   } catch (Throwable failure) {
    code = Activity.RESULT_CANCELED;
    results.append("FAIL cleanup: ").append(failure);
   }
   client.dispatcher().executorService().shutdown();
   client.connectionPool().evictAll();
   result.putString("stream", results.toString());
   finish(code, result);
  }
 }

 private JSONObject request(String path, boolean post) throws Exception {
  Request.Builder request = new Request.Builder().url(base + path);
  if (post) request.post(RequestBody.create(new byte[0], null));
  try (Response response = client.newCall(request.build()).execute()) {
   if (!response.isSuccessful()) throw new AssertionError("Fixture HTTP " + response.code());
   return new JSONObject(response.body().string());
  }
 }

 private StatusBarNotification reply() {
  for (StatusBarNotification notification : notifications.getActiveNotifications()) {
   if (notification.getUid() != android.os.Process.myUid()) throw new AssertionError("Notification not target UID");
   if (notification.getId() == 2) return notification;
  }
  return null;
 }

 private void posted(String scenario) throws Exception {
  notifications.cancel(2);
  await(() -> reply() == null, "previous reply cleared");
  request("/emit/" + scenario + "?run=" + runId, true);
  await(() -> reply() != null, scenario + " notification");
  Notification notification = reply().getNotification();
  String expected = "synthetic " + scenario;
  if (!expected.contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT, "")) ||
      !expected.contentEquals(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT, ""))) {
   throw new AssertionError(scenario + " notification text mismatch");
  }
  if (!"hermes-replies-v2".equals(notification.getChannelId()) ||
      notifications.getNotificationChannel(notification.getChannelId()).getImportance() != NotificationManager.IMPORTANCE_HIGH) {
   channelFailures++;
   results.append("FAIL ").append(scenario).append(" reply channel=").append(notification.getChannelId())
     .append(" importance=").append(notifications.getNotificationChannel(notification.getChannelId()).getImportance())
     .append("; expected hermes-replies-v2 HIGH\n");
  }
  results.append("PASS ").append(scenario).append(" native notification text and target UID\n");
 }

 private void suppressed(String scenario) throws Exception {
  notifications.cancel(2);
  await(() -> reply() == null, "previous reply cleared");
  request("/emit/" + scenario + "?run=" + runId, true);
  await(() -> {
   try (ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand("logcat -d -t 2000 -s PushService:I *:S");
        java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
    String log = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    return log.contains("complete sid=push-test-" + runId + "-" + scenario);
   }
  }, scenario + " completion actually received");
  long deadline = SystemClock.elapsedRealtime() + 2000;
  while (SystemClock.elapsedRealtime() < deadline) {
   if (!MainActivity.isForeground || reply() != null) throw new AssertionError(scenario + " foreground suppression failed");
   SystemClock.sleep(100);
  }
  results.append("PASS ").append(scenario).append(" received, suppressed\n");
 }

 private interface Check { boolean ready() throws Exception; }

 private void await(Check check, String label) throws Exception {
  long deadline = SystemClock.elapsedRealtime() + 30000;
  while (SystemClock.elapsedRealtime() < deadline) {
   if (check.ready()) return;
   SystemClock.sleep(100);
  }
  throw new AssertionError("Timed out: " + label);
 }
}
