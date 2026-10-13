package com.hermes.privateapp;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndpointPolicyTest {
 @Test public void acceptsPrivateEndpoints() {assertEquals("http://100.64.0.1:8765",EndpointPolicy.validateBase("http://100.64.0.1:8765/"));assertEquals("https://phone.example.ts.net",EndpointPolicy.validateBase("https://phone.example.ts.net"));}
 @Test public void refusesPublicAndCredentialUrls() {for(String u:new String[]{"http://8.8.8.8","https://example.org","https://person:password@phone.example.ts.net","https://phone.example.ts.net?token=not-a-secret","file:///tmp/test","http://100.63.1.1","http://100.128.1.1"})assertThrows(IllegalArgumentException.class,()->EndpointPolicy.validateBase(u));}
 @Test public void scopesWebsocketToSameGateway() {EndpointPolicy.requireSameEndpoint("https://phone.example.ts.net","wss://phone.example.ts.net/api/ws?profile=ari");}
 @Test public void refusesRetargetingAndDowngrade() {for(String u:new String[]{"wss://other.example.ts.net/api/ws","ws://phone.example.ts.net/api/ws","https://phone.example.ts.net:1234/api/status","https://phone.example.ts.net/auth/login","https://phone.example.ts.net/api/%2e%2e/other"})assertThrows(IllegalArgumentException.class,()->EndpointPolicy.requireSameEndpoint("https://phone.example.ts.net",u));}
 @Test public void preservesGatewaySubpath() {EndpointPolicy.requireSameEndpoint("https://phone.example.ts.net/hermes","wss://phone.example.ts.net/hermes/api/ws");assertThrows(IllegalArgumentException.class,()->EndpointPolicy.requireSameEndpoint("https://phone.example.ts.net/hermes","https://phone.example.ts.net/api/status"));}
}
