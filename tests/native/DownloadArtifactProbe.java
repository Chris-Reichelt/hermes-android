package com.hermes.privateapp;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import com.sun.net.httpserver.*;
import okhttp3.*;
import org.json.JSONObject;

/** Loopback only, exact APK bytes through production native transport AND save pipeline.
 * Not a phone SAF test. Accepts explicit artifact paths; never reads chats or credentials. */
public class DownloadArtifactProbe {
 static byte[] hash(Path p)throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream i=Files.newInputStream(p)){byte[] b=new byte[32768];int n;while((n=i.read(b))!=-1)d.update(b,0,n);}return d.digest();}
 public static void main(String[] args)throws Exception {
  Map<String,Path> artifacts=new HashMap<>();for(String a:args){Path p=Path.of(a);if(!Files.isRegularFile(p))throw new IOException("Missing explicit artifact");artifacts.put(a,p);}
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/status",e->{byte[] b="{\"auth_required\":false}".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/api/fs/download",e->{try {
   Map<String,String> q=new HashMap<>();for(String part:e.getRequestURI().getRawQuery().split("&")){String[] kv=part.split("=",2);q.put(kv[0],URLDecoder.decode(kv[1],"UTF-8"));}
   if(!"Bearer synthetic-token".equals(e.getRequestHeaders().getFirst("Authorization"))||!"synthetic-owner".equals(q.get("profile"))||!"synthetic-session".equals(q.get("session_id"))){e.sendResponseHeaders(403,-1);return;}
   Path p=artifacts.get(q.get("path"));if(p==null){e.sendResponseHeaders(404,-1);return;}
   e.getResponseHeaders().set("Content-Type",p.toString().endsWith(".apk")?"application/vnd.android.package-archive":"text/plain");e.sendResponseHeaders(200,Files.size(p));Files.copy(p,e.getResponseBody());
  }catch(Exception ignored){}finally{e.close();}});
  server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();GatewayAuth auth=new GatewayAuth(base,new OkHttpClient());auth.useLegacy("synthetic-token");
  Path cache=Files.createTempDirectory("artifact-stage-");
  try {
   for(String a:args){Path p=Path.of(a),destination=Files.createTempFile("artifact-destination-",".bin");try {
    JSONObject params=new JSONObject().put("path",a).put("connectionId","android-remote").put("sessionId","synthetic-session");
    GatewaySave.save(cache.toFile(),out->GatewayDownload.file(auth,GatewayDownload.fileUrl(base,params,"synthetic-owner"),out,()->true),()->Files.newOutputStream(destination),()->true);
    if(!Arrays.equals(hash(p),hash(destination)))throw new AssertionError("Hash mismatch");
    System.out.println("PASS "+p.getFileName()+" bytes="+Files.size(destination)+" SHA256 matches; native request/staging/destination copy; MIME="+(a.endsWith(".apk")?"application/vnd.android.package-archive":"text/plain"));
   }finally{Files.deleteIfExists(destination);}}
   try(java.util.stream.Stream<Path> entries=Files.list(cache)){if(entries.count()!=0)throw new AssertionError("Staging residue");}
  }finally{auth.cancel();server.stop(0);Files.delete(cache);}
 }
}
