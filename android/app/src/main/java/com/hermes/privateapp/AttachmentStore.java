package com.hermes.privateapp;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Capability-only attachment staging. Never accepts a renderer path/URI to open.
 * 8 MiB fits the existing base64 JSON-RPC and OkHttp's 16 MiB socket queue.
 * Copies are bounded on disk; data URL reads are serialized and bounded too. */
public final class AttachmentStore implements Closeable {
 public static final int MAX_BYTES=8*1024*1024, MAX_TOTAL=32*1024*1024, MAX_FILES=32;
 private final File directory;
 private final Map<String,Entry> entries=new HashMap<>();
 private long total;
 private static final class Entry {
  final File file; final String profile,mime; final Object owner;
  Entry(File f,String p,String m,Object o){file=f;profile=p;mime=m;owner=o;}
 }
 public AttachmentStore(File cache)throws IOException {
  directory=new File(cache,"picked-attachments");
  if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Attachment staging unavailable");
  // No handles survive process/Activity recreation; remove orphan copies.
  File[] old=directory.listFiles();if(old!=null)for(File f:old)if(f.isFile())f.delete();
 }
 public static String name(String raw) {
  String s=raw==null?"attachment":raw.replaceAll("[\\\\/\\p{Cntrl}]","_").trim();
  if(s.isEmpty()||s.equals(".")||s.equals(".."))s="attachment";
  return s.length()>160?s.substring(s.length()-160):s;
 }
 public synchronized String stage(InputStream source,String filename,String mime,String profile,Object owner,BooleanSupplier active)throws IOException {
  if(source==null)throw new IOException("Cannot read selected document");
  File staged=null;boolean kept=false;
  try(InputStream in=source){
   if(owner==null||!active.getAsBoolean())throw new IOException("Attachment selection expired");
   if(entries.size()>=MAX_FILES)throw new IOException("Attachment staging full; reconnect to clear unused selections");
   staged=File.createTempFile("picked-",".part",directory);
   long count=0;byte[] buffer=new byte[32768];
   try(OutputStream out=new FileOutputStream(staged)){
    for(int n;(n=in.read(buffer))!=-1;){
     if(!active.getAsBoolean())throw new IOException("Attachment selection expired");
     count+=n;
     if(count>MAX_BYTES)throw new IOException("Attachment too large (limit 8388608 bytes)");
     if(total+count>MAX_TOTAL)throw new IOException("Attachment staging full; reconnect to clear unused selections");
     out.write(buffer,0,n);
    }
   }
   if(!active.getAsBoolean())throw new IOException("Attachment selection expired");
   String handle="android-attachment:"+UUID.randomUUID()+"/"+name(filename);
   String type=mime!=null&&mime.matches("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+")?mime:"application/octet-stream";
   entries.put(handle,new Entry(staged,profile,type,owner));total+=count;kept=true;return handle;
  } finally {if(!kept&&staged!=null)staged.delete();}
 }
 public synchronized String read(String handle,String profile,Object owner,BooleanSupplier active)throws IOException {
  Entry e=entries.get(handle);
  if(e==null||e.owner!=owner||!e.profile.equals(profile)||!active.getAsBoolean())throw new IOException("Attachment unavailable for this owner; choose the file again");
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(InputStream in=new FileInputStream(e.file)){
   byte[] b=new byte[32768];int count=0;
   for(int n;(n=in.read(b))!=-1;){count+=n;if(count>MAX_BYTES||!active.getAsBoolean())throw new IOException("Attachment expired or too large");out.write(b,0,n);}
  }
  if(!active.getAsBoolean())throw new IOException("Attachment selection expired");
  return "data:"+e.mime+";base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
 }
 public synchronized void discard(String handle){Entry e=entries.remove(handle);if(e!=null){total-=e.file.length();e.file.delete();}}
 @Override public synchronized void close(){for(Entry e:entries.values())e.file.delete();entries.clear();total=0;}
}
