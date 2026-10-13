"""v0.3.1 artifact verification; no production service or credentials."""
from pathlib import Path
import hashlib, json, re, subprocess, zipfile, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
apk=root/'artifacts/hermes-android-0.3.1-debug.apk'
installed=root/'artifacts/emulator-v4-installed.apk'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(apk)==sha(installed)==sha(root/'artifacts/hermes-android-debug.apk')
tools=root/'toolchain/android-sdk/build-tools/35.0.0'
def cert(p):
    text=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(p)],text=True,stderr=subprocess.DEVNULL)
    return next(x for x in text.splitlines() if 'certificate SHA-256 digest:' in x)
certificate=cert(apk)
assert certificate==cert(root/'artifacts/hermes-android-0.3.0-debug.apk')
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
assert "name='com.hermes.privateapp' versionCode='4' versionName='0.3.1'" in badging
assert "uses-permission: name='android.permission.MODIFY_AUDIO_SETTINGS'" in badging
with zipfile.ZipFile(apk) as z:
    assets={n:z.read(n) for n in z.namelist() if n.startswith('assets/') and not n.endswith('/')}
    built={'assets/'+str(p.relative_to(root/'apps/desktop/dist')):p.read_bytes() for p in (root/'apps/desktop/dist').rglob('*') if p.is_file()}
    assert assets==built
    assert not any('MicrophoneProbe' in n for n in z.namelist())
    matched=[v.decode() for k,v in assets.items() if k.endswith('.js') and b'TrackStartError' in v]
    assert len(matched)==1
    text=matched[0];pos=text.index('TrackStartError');mapping=text[pos:pos+180]
    assert 'microphoneStartFailed' in mapping and 'microphoneInUse' not in mapping
junit={k:0 for k in ('tests','failures','errors','skipped')}
for p in (root/'android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    tree=ET.parse(p).getroot()
    for k in junit:junit[k]+=int(tree.get(k,'0'))
assert junit['tests'] and not any(junit[k] for k in ('failures','errors','skipped'))
vitest=int(re.search(r'Tests\s+(\d+) passed', (root/'tests-js.log').read_text()).group(1))
result={'apk':str(apk),'sha256':sha(apk),'bytes':apk.stat().st_size,'package':'com.hermes.privateapp','versionName':'0.3.1','versionCode':4,'same_certificate_as_0_3_0':certificate,'installed_apk_hash_matches':True,'renderer_assets_identical':len(assets),'bundled_error_mapping':mapping,'vitest_passed':vitest,'junit':junit,'phone_ui_fixtures':json.loads((root/'artifacts/phone-ui-results.json').read_text()),'browser_capture_control':json.loads((root/'artifacts/microphone-browser-control.json').read_text()),'android_capture':'BLOCKED: WebView 124 renderer SIGTRAP/process crashes in old and updated APK instrumentation; no successful acquisition or phone recovery claim','auth':'isolated real-component integration passed; no production requests or changes'}
(root/'artifacts/microphone-validation.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ('phone_ui_fixtures','browser_capture_control')},indent=2))
