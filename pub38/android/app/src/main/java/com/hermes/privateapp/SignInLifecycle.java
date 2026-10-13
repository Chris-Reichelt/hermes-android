package com.hermes.privateapp;

/** UI-thread-only admission gate. No credentials, timers, or automatic retries. */
final class SignInLifecycle {
 private int generation=-1;
 private boolean resumed, launched, ready, entered;
 void begin(int id) {generation=id;launched=false;ready=false;entered=false;}
 void invalidate() {generation=-1;ready=false;}
 void resume() {resumed=true;}
 void pause() {resumed=false;}
 boolean active(int id) {return generation==id;}
 boolean launch(int id) {
  if(!active(id)||!resumed||launched||ready)return false;
  launched=true;return true;
 }
 void complete(int id) {if(active(id))ready=true;}
 boolean enter(int id) {
  if(!active(id)||!resumed||!ready||entered)return false;
  entered=true;return true;
 }
}
