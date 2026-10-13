package com.hermes.privateapp;

import java.io.*;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import okhttp3.*;
import org.json.JSONObject;

/** Bounded attachment bytes, not URLs/tokens. Uses the working download contract. */
public final class GatewayMedia {
 public static final int MAX_BYTES=8*1024*1024;
 private static final Semaphore READS=new Semaphore(2);
 public static JSONObject read(GatewayAuth auth,String base,JSONObject params,String profile,BooleanSupplier active)throws Exception {
  if(!READS.tryAcquire())throw new IOException("Too many media reads");
  try {
   HttpUrl url=GatewayDownload.fileUrl(base,params,profile);
   if(!active.getAsBoolean())throw new IOException("Media canceled");
   try(Response r=auth.request(new Request.Builder().url(url).build())) {
    if(r.body()==null||r.body().contentLength()>MAX_BYTES)throw new IOException("Media exceeds 8 MiB");
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    long count=GatewayDownload.copy(r.body().byteStream(),out,MAX_BYTES,active);
    if(count==0||(r.body().contentLength()>=0&&count!=r.body().contentLength()))throw new IOException("Incomplete media");
    return new JSONObject().put("base64",Base64.getEncoder().encodeToString(out.toByteArray()));
   }
  }finally{READS.release();}
 }
}
