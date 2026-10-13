package com.hermes.privateapp;
import java.net.URI;
public final class EndpointPolicy {
 public static String validateBase(String input) {
  URI u = URI.create(input.trim());
  String host = u.getHost();
  if (host == null || u.getUserInfo() != null || u.getQuery() != null || u.getFragment() != null) throw new IllegalArgumentException("Enter a gateway URL without credentials, query or fragment");
  if (!u.getScheme().equals("https") && !u.getScheme().equals("http")) throw new IllegalArgumentException("Use HTTP or HTTPS");
  boolean privateHost = host.endsWith(".ts.net");
  String[] octets = host.split("\\.");
  if (octets.length == 4) {
   try { privateHost |= Integer.parseInt(octets[0]) == 100 && Integer.parseInt(octets[1]) >= 64 && Integer.parseInt(octets[1]) <= 127 && Integer.parseInt(octets[2]) >= 0 && Integer.parseInt(octets[2]) <= 255 && Integer.parseInt(octets[3]) >= 0 && Integer.parseInt(octets[3]) <= 255; } catch (NumberFormatException ignored) { }
  }
  if (!privateHost) throw new IllegalArgumentException("Initial build accepts only Tailscale 100.64.0.0/10 or .ts.net endpoints");
  return input.trim().replaceAll("/+$", "");
 }
 public static void requireSameEndpoint(String base, String target) {
  URI b=URI.create(base), t=URI.create(target);
  String scheme=t.getScheme().replace("wss", "https").replace("ws", "http");
  String path=t.getPath();
  if (t.getFragment()!=null || path.contains("\\\\") || java.util.Arrays.asList(path.split("/")).contains("..")) throw new IllegalArgumentException("Invalid request path");
  if (!b.getScheme().equals(scheme) || !b.getHost().equals(t.getHost()) || b.getPort()!=t.getPort() || t.getUserInfo()!=null || !t.getPath().startsWith(b.getPath()+"/api/")) throw new IllegalArgumentException("Cross-endpoint request blocked");
 }
}
