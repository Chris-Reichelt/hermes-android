package com.hermes.privateapp;

import java.io.*;
import java.util.function.BooleanSupplier;

/** Shared native save pipeline. The destination is opened only after a complete staged response. */
public final class GatewaySave {
 @FunctionalInterface public interface Transfer {void receive(OutputStream out)throws Exception;}
 @FunctionalInterface public interface Destination {OutputStream open()throws Exception;}
 public static void save(File cache,Transfer transfer,Destination destination,BooleanSupplier active)throws DownloadFailure {
  File staged=null;
  DownloadFailure.Stage stage=DownloadFailure.Stage.STAGING;
  try {
   if(!active.getAsBoolean())throw new DownloadFailure(DownloadFailure.Stage.SESSION,DownloadFailure.Reason.CANCELED);
   staged=File.createTempFile("hermes-download-",".part",cache);
   try(OutputStream out=new FileOutputStream(staged)) {
    stage=DownloadFailure.Stage.REQUEST;
    transfer.receive(out);
    stage=DownloadFailure.Stage.STAGING;
   }
   if(!active.getAsBoolean())throw new DownloadFailure(DownloadFailure.Stage.SESSION,DownloadFailure.Reason.CANCELED);
   stage=DownloadFailure.Stage.STAGED_READ;
   try(InputStream in=new FileInputStream(staged)) {
    stage=DownloadFailure.Stage.DESTINATION_OPEN;
    try(OutputStream out=destination.open()) {
     if(out==null)throw new IOException();
     stage=DownloadFailure.Stage.DESTINATION_COPY;
     GatewayDownload.copy(in,out,GatewayDownload.MAX_BYTES,active);
    }
   }
  }catch(DownloadFailure e){throw e;}
  catch(Exception e){throw new DownloadFailure(stage,e);}
  finally {if(staged!=null)staged.delete();}
 }
}
