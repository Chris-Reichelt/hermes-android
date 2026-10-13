package com.hermes.privateapp;

import android.content.SharedPreferences;

/** Only the two nonsecret, user-selected connection fields may reach disk. */
final class ConnectionPreferences {
 private final SharedPreferences preferences;
 ConnectionPreferences(SharedPreferences preferences) {this.preferences=preferences;}
 static String profile(String input) {
  String value=input.trim();if(value.isEmpty())return "default";
  if(value.length()>128||value.equals(".")||value.equals("..")||value.chars().anyMatch(c->Character.isISOControl(c)||c=='/'||c=='\\'))throw new IllegalArgumentException("Invalid profile");
  return value;
 }
 boolean save(String endpoint,String selectedProfile) {
  String valid=EndpointPolicy.validateBase(endpoint), owner=profile(selectedProfile);
  return preferences.edit().putString("endpoint",valid).putString("profile",owner).commit();
 }
 String[] restore() {
  try {
   String endpoint=preferences.getString("endpoint","");
   if(endpoint.isEmpty())return new String[]{"","default"};
   return new String[]{EndpointPolicy.validateBase(endpoint),profile(preferences.getString("profile","default"))};
  }catch(RuntimeException invalid){return new String[]{"","default"};}
 }
}
