package com.hermes.privateapp;

import java.io.IOException;
import java.net.SocketTimeoutException;

/** Bounded diagnostics only: never render exception messages, causes, URIs or payloads. */
public final class DownloadFailure extends IOException {
 public enum Stage {
  INPUT("DL_INPUT", "Invalid file request. Reopen the file from its original conversation."),
  BUSY("DL_BUSY", "Finish or cancel the existing Save dialog first."),
  PICKER_LAUNCH("DL_PICKER_LAUNCH", "Android could not open the Save dialog."),
  PICKER_RESULT("DL_PICKER_RESULT", "Android did not return a usable document destination."),
  WORKER("DL_WORKER", "Android could not start the save task."),
  STAGING("DL_STAGING", "Could not create or finalize temporary storage. Check free space."),
  REQUEST("DL_REQUEST", "Could not request the file from the gateway."),
  RECEIVE("DL_RECEIVE", "Could not receive the file into temporary storage."),
  SESSION("DL_SESSION", "The connection changed before the save completed."),
  STAGED_READ("DL_STAGED_READ", "Could not reopen the temporary download."),
  DESTINATION_OPEN("DL_DESTINATION_OPEN", "Could not open the selected document for writing. Choose local Downloads."),
  DESTINATION_COPY("DL_DESTINATION_COPY", "Could not write or finalize the selected document. Check destination storage.");
  public final String code, hint;
  Stage(String code,String hint){this.code=code;this.hint=hint;}
 }
 public enum Reason { FAILED, SIZE_LIMIT, INCOMPLETE, CANCELED }
 public final Stage stage;
 public final Reason reason;
 public DownloadFailure(Stage stage,Reason reason){super(stage.code);this.stage=stage;this.reason=reason;}
 public DownloadFailure(Stage stage,Exception cause){super(stage.code,cause);this.stage=stage;this.reason=Reason.FAILED;}
 public static String safe(Stage fallback,Exception error) {
  Stage stage=fallback;Reason reason=Reason.FAILED;
  if(error instanceof DownloadFailure){DownloadFailure failure=(DownloadFailure)error;stage=failure.stage;reason=failure.reason;}
  // Only these known exception types contribute a bounded qualifier. Never getMessage().
  Throwable detail=error instanceof DownloadFailure&&error.getCause()!=null?error.getCause():error;
  String qualifier="";
  if(detail instanceof GatewayAuth.AuthException){
   int status=((GatewayAuth.AuthException)detail).status;
   qualifier=status>=100&&status<=599?" HTTP "+status+".":" Gateway rejected the request.";
   if(status==401)qualifier+=" Sign in again.";
  }else if(detail instanceof SecurityException)qualifier=" Permission denied.";
  else if(detail instanceof SocketTimeoutException)qualifier=" Network timeout.";
  else if(reason==Reason.SIZE_LIMIT)qualifier=" Size limit exceeded (files 256 MiB; images 16 MiB).";
  else if(reason==Reason.INCOMPLETE)qualifier=" The response ended before all bytes arrived.";
  else if(reason==Reason.CANCELED)qualifier=" Download canceled.";
  String code=stage.code;
  if(stage==Stage.REQUEST&&reason==Reason.FAILED&&!(detail instanceof GatewayAuth.AuthException))code+="_"+requestCategory(detail);
  return "Download failed ["+code+"]. "+stage.hint+qualifier;
 }
 /** Fixed allowlist, not exception names/messages, even for unknown subclasses. */
 private static String requestCategory(Throwable e) {
  if(e.getClass().getName().equals("android.os.NetworkOnMainThreadException"))return "THREAD";
  if(e instanceof javax.net.ssl.SSLException)return "TLS";
  if(e instanceof java.net.UnknownHostException)return "DNS";
  if(e instanceof java.net.ConnectException)return "CONNECT";
  if(e instanceof java.net.SocketTimeoutException)return "TIMEOUT";
  if(e instanceof java.net.SocketException)return "SOCKET";
  if(e instanceof SecurityException)return "PERMISSION";
  if(e instanceof IllegalArgumentException)return "ARGUMENT";
  if(e instanceof IllegalStateException)return "STATE";
  if(e instanceof org.json.JSONException)return "JSON";
  if(e instanceof NullPointerException)return "NULL";
  if(e instanceof IOException)return "IO";
  return "OTHER";
 }
}
