package com.hermes.privateapp;
import org.junit.Test;
import static org.junit.Assert.*;

public class SignInLifecycleTest {
 @Test public void firstReturnAfterCompletionEntersOnceWithoutSecondBrowserLaunch(){
  SignInLifecycle s=new SignInLifecycle();s.resume();s.begin(1);assertTrue(s.launch(1));s.pause();
  s.complete(1);assertFalse(s.enter(1));s.resume();assertTrue(s.enter(1));
  assertFalse(s.launch(1));assertFalse(s.enter(1));s.pause();s.resume();assertFalse(s.enter(1));
 }
 @Test public void firstReturnBeforeExchangeCompletesEntersWhenReady(){
  SignInLifecycle s=new SignInLifecycle();s.resume();s.begin(1);assertTrue(s.launch(1));s.pause();s.resume();
  assertFalse(s.enter(1));assertFalse(s.launch(1));s.complete(1);assertTrue(s.enter(1));
 }
 @Test public void authorizeReadyWhilePausedWaitsForResumeAndOnlyLaunchesOnce(){
  SignInLifecycle s=new SignInLifecycle();s.begin(1);assertFalse(s.launch(1));s.resume();assertTrue(s.launch(1));
  s.pause();s.resume();assertFalse(s.launch(1));
 }
 @Test public void cancelErrorDestroyAndRetryRejectOldCallbacks(){
  SignInLifecycle s=new SignInLifecycle();s.resume();s.begin(1);s.launch(1);s.invalidate();
  s.complete(1);assertFalse(s.enter(1));assertFalse(s.launch(1));
  s.begin(2);s.complete(1);assertFalse(s.active(1));assertFalse(s.enter(2));
  assertTrue(s.launch(2));s.complete(2);assertTrue(s.enter(2));s.invalidate();assertFalse(s.enter(2));
 }
 @Test public void processRecreationDoesNotInheritAuthorizedState(){
  SignInLifecycle old=new SignInLifecycle();old.resume();old.begin(1);old.complete(1);
  SignInLifecycle recreated=new SignInLifecycle();recreated.resume();assertFalse(recreated.enter(1));assertFalse(recreated.launch(1));
 }
 @Test public void repeatedResumeDoesNotAuthorizeOrLaunchArbitraryEndpoint(){
  SignInLifecycle s=new SignInLifecycle();s.resume();s.resume();assertFalse(s.launch(0));assertFalse(s.enter(0));
 }
}
