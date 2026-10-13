package com.hermes.privateapp;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class AttachmentStoreTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 AttachmentStore store; Object owner=new Object();
 @Before public void setup()throws Exception{store=new AttachmentStore(temp.getRoot());}
 @After public void cleanup(){store.close();}
 String stage(byte[] bytes)throws Exception{return store.stage(new ByteArrayInputStream(bytes),"test.txt","text/plain","owner-a",owner,()->true);}
 @Test public void successIsOpaqueAndBytesMatch()throws Exception{String h=stage("synthetic".getBytes());assertTrue(h.startsWith("android-attachment:"));assertTrue(h.endsWith("/test.txt"));assertEquals("data:text/plain;base64,c3ludGhldGlj",store.read(h,"owner-a",owner,()->true));}
 @Test public void wrongProfileAuthAndPathsCannotRead()throws Exception{String h=stage(new byte[]{1});for(String p:new String[]{"owner-b"})assertThrows(IOException.class,()->store.read(h,p,owner,()->true));assertThrows(IOException.class,()->store.read(h,"owner-a",new Object(),()->true));for(String h2:new String[]{"/etc/passwd","content://anything",h+"/../test.txt"})assertThrows(IOException.class,()->store.read(h2,"owner-a",owner,()->true));}
 @Test public void oversizeUnknownLengthCleansPartialCopy()throws Exception{InputStream large=new InputStream(){int count;public int read(){return count++<=AttachmentStore.MAX_BYTES?0:-1;}};assertThrows(IOException.class,()->store.stage(large,"huge","text/plain","owner-a",owner,()->true));assertEquals(0,new File(temp.getRoot(),"picked-attachments").list().length);}
 @Test public void cancellationClosesInputAndLeavesNoCopy()throws Exception{AtomicBoolean closed=new AtomicBoolean();InputStream input=new ByteArrayInputStream(new byte[100]){public void close()throws IOException{closed.set(true);super.close();}};assertThrows(IOException.class,()->store.stage(input,"x","text/plain","owner-a",owner,()->false));assertTrue(closed.get());assertEquals(0,new File(temp.getRoot(),"picked-attachments").list().length);}
 @Test public void discardAndCloseInvalidateHandles()throws Exception{String h=stage(new byte[]{1});store.discard(h);assertThrows(IOException.class,()->store.read(h,"owner-a",owner,()->true));final String other=stage(new byte[]{2});store.close();assertThrows(IOException.class,()->store.read(other,"owner-a",owner,()->true));assertEquals(0,new File(temp.getRoot(),"picked-attachments").list().length);}
 @Test public void nameAndMimeCannotInjectDataUrl()throws Exception{String h=store.stage(new ByteArrayInputStream(new byte[]{0}),"../secret\n.txt","text/html;base64,evil","owner-a",owner,()->true);assertTrue(h.endsWith("/.._secret_.txt"));assertTrue(store.read(h,"owner-a",owner,()->true).startsWith("data:application/octet-stream;base64,"));}
}
