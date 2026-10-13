// Generate a test-APK-only document-start fixture. Never copy to main APK assets.
import {mkdir,writeFile} from 'node:fs/promises'
import {installPhoneFixture} from './phone-fixture.mjs'
const script=`(()=>{
 const realPort=window.HermesNative;
 (${installPhoneFixture.toString()})();
 const fixture=window.HermesNative;
 const original=fixture.postMessage;
 window.fixtureNativeWrites=0;window.fixtureNativeReads=0;
 realPort.onmessage=event=>fixture.onmessage?.(event);
 fixture.postMessage=raw=>{
  const call=JSON.parse(raw);
  if(call.method==='clipboard.write'){window.fixtureNativeWrites++;realPort.postMessage(raw);return;}
  if(call.method==='clipboard.read'){window.fixtureNativeReads++;throw Error('Passive clipboard read forbidden');}
  original(raw);
 };
})();`
await mkdir('android/app/src/androidTest/assets',{recursive:true})
await writeFile('android/app/src/androidTest/assets/clipboard-fixture.js',script)
console.log('Generated isolated clipboard fixture for instrumentation APK only')
