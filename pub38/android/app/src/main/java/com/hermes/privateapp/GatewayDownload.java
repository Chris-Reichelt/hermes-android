package com.hermes.privateapp;

import java.io.*;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.*;
import org.json.JSONObject;

/** Byte transport only. Credentials never leave GatewayAuth; redirects are refused. */
public final class GatewayDownload {
 public static final long MAX_BYTES=256L*1024*1024, MAX_IMAGE_BYTES=16L*1024*1024;
 private static final OkHttpClient PUBLIC_HTTP=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(60,TimeUnit.SECONDS).build();
 /** Do not coerce JSON objects/numbers into private paths or scope identifiers. */
 public static String text(JSONObject p,String key,boolean required) {
  Object value=p.opt(key);
  if(value==null||value==JSONObject.NULL){if(required)throw new IllegalArgumentException("Missing field");return "";}
  if(!(value instanceof String))throw new IllegalArgumentException("Invalid field type");
  String result=(String)value;
  long maxLength=key.equals("url")&&result.startsWith("data:image/")?MAX_IMAGE_BYTES*4/3+104:16384;
  if(result.length()>maxLength||(required&&result.trim().isEmpty())||result.chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("Invalid field");
  return result;
 }
 public static HttpUrl fileUrl(String base,JSONObject p,String profile) {
  String owner=text(p,"connectionId",false);
  if(!owner.isEmpty()&&!owner.equals("null")&&!owner.equals("android-remote"))throw new IllegalArgumentException("Unknown owner");
  String path=text(p,"path",true);
  HttpUrl.Builder u=HttpUrl.get(base+"/api/fs/download").newBuilder().addQueryParameter("path",path).addQueryParameter("profile",profile);
  String session=text(p,"sessionId",false);if(!session.isEmpty())u.addQueryParameter("session_id",session);
  return u.build();
 }
 public static String imageName(String raw) {
  if(raw.startsWith("data:image/")) {
   String subtype=raw.substring(11,Math.min(raw.length(),40)).split("[;,]",2)[0];
   return "image."+(subtype.equals("jpeg")?"jpg":subtype.equals("svg+xml")?"svg":subtype.matches("[a-z0-9]{2,8}")?subtype:"png");
  }
  try {String path=HttpUrl.get(raw).encodedPath();String name=filename("",path);return name.contains(".")?name:"image";}catch(Exception e){return "image";}
 }
 public static String filename(String suggestion,String path) {
  String name=suggestion==null||suggestion.trim().isEmpty()?path:suggestion;
  name=name.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_").trim();
  if(name.isEmpty()||name.equals(".")||name.equals(".."))name="download";
  return name.length()>160?name.substring(name.length()-160):name;
 }
 public static long copy(InputStream in,OutputStream out,long limit,BooleanSupplier active)throws IOException {
  byte[] buffer=new byte[32768];long count=0;int n;
  while((n=in.read(buffer))!=-1){if(!active.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("Download canceled");count+=n;if(count>limit)throw new IOException("Download exceeds size limit");out.write(buffer,0,n);}
  if(!active.getAsBoolean())throw new IOException("Download canceled");return count;
 }
 public static void file(GatewayAuth auth,HttpUrl url,OutputStream out,BooleanSupplier active)throws Exception {
  try(Response r=auth.request(new Request.Builder().url(url).build())) {writeResponse(r,out,MAX_BYTES,active,false);}
 }
 private static void writeResponse(Response r,OutputStream out,long limit,BooleanSupplier active,boolean image)throws Exception {
  try {
   if(!r.isSuccessful()||r.body()==null)throw new IOException("Invalid response");
   if(r.body().contentLength()>limit)throw new DownloadFailure(DownloadFailure.Stage.RECEIVE,DownloadFailure.Reason.SIZE_LIMIT);
   if(image&&!r.header("Content-Type","").toLowerCase().startsWith("image/"))throw new IOException("Not an image");
   long bytes=copy(r.body().byteStream(),out,limit,active);
   if(r.body().contentLength()>=0&&bytes!=r.body().contentLength())throw new DownloadFailure(DownloadFailure.Stage.RECEIVE,DownloadFailure.Reason.INCOMPLETE);
  }catch(DownloadFailure e){throw e;}
  catch(Exception e){throw new DownloadFailure(DownloadFailure.Stage.RECEIVE,e);}
 }
 public static void image(GatewayAuth auth,String base,String raw,String profile,OutputStream out,BooleanSupplier active)throws Exception {
  if(raw.startsWith("data:image/")) {
   int comma=raw.indexOf(',');
   if(comma<0||comma>100||!raw.substring(0,comma).endsWith(";base64")||raw.length()>MAX_IMAGE_BYTES*4/3+104)throw new IllegalArgumentException("Invalid image data");
   try(InputStream in=Base64.getDecoder().wrap(new ByteArrayInputStream(raw.substring(comma+1).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))){copy(in,out,MAX_IMAGE_BYTES,active);}return;
  }
  HttpUrl url=HttpUrl.get(raw), gateway=HttpUrl.get(base);
  if(!url.username().isEmpty()||!url.password().isEmpty()||url.fragment()!=null)throw new IllegalArgumentException("Invalid image URL");
  boolean same=url.host().equals(gateway.host())&&url.port()==gateway.port()&&url.scheme().equals(gateway.scheme());
  if(same){
   EndpointPolicy.requireSameEndpoint(base,raw);
   String relative=url.encodedPath().substring(gateway.encodedPath().replaceAll("/+$", "").length());
   if(!java.util.Arrays.asList("/api/media","/api/files/download","/api/fs/download").contains(relative))throw new IllegalArgumentException("Invalid image route");
   // An explicit owner embedded in a media URL must not be silently retargeted.
   if(url.queryParameter("profile")!=null)profile=url.queryParameter("profile");
   url=url.newBuilder().removeAllQueryParameters("token").removeAllQueryParameters("ticket").removeAllQueryParameters("internal").setQueryParameter("profile",profile).build();
   try(Response r=auth.request(new Request.Builder().url(url).build())){writeResponse(r,out,MAX_IMAGE_BYTES,active,true);}
  }else{
   if(!url.isHttps())throw new IllegalArgumentException("External images require HTTPS");
   // Separate credential/cookie-free client; never follow even same-site redirects.
   try(Response r=PUBLIC_HTTP.newCall(new Request.Builder().url(url).build()).execute()){writeResponse(r,out,MAX_IMAGE_BYTES,active,true);}
  }
 }
}
