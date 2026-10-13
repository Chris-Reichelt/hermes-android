#!/usr/bin/env python3
"""Verify the attachment-only release, without contacting a real gateway."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile,xml.etree.ElementTree as ET,shutil
root=Path(__file__).resolve().parents[1]
apk=root/'artifacts/hermes-android-0.3.4-debug.apk'
shutil.copy2(root/'artifacts/hermes-android-debug.apk',apk)
tools=root/'toolchain/android-sdk/build-tools/35.0.0'
def run(*args):return subprocess.check_output([str(a) for a in args],text=True,stderr=subprocess.DEVNULL)
def cert(p):return re.search(r'certificate SHA-256 digest: (\w+)',run(tools/'apksigner','verify','--print-certs',p)).group(1)
old=root/'artifacts/hermes-android-0.3.3-debug.apk'
assert cert(old)==cert(apk)
badging=run(tools/'aapt','dump','badging',apk)
assert "package: name='com.hermes.privateapp' versionCode='7' versionName='0.3.4'" in badging
assert 'android.permission.RECORD_AUDIO' in badging and 'android.permission.MODIFY_AUDIO_SETTINGS' in badging
with zipfile.ZipFile(apk) as z:
 files=[p for p in (root/'apps/desktop/dist').rglob('*') if p.is_file()]
 for p in files:assert z.read('assets/'+p.relative_to(root/'apps/desktop/dist').as_posix())==p.read_bytes(),str(p)
 assert set(n for n in z.namelist() if n.startswith('assets/') and not n.endswith('/'))==set('assets/'+p.relative_to(root/'apps/desktop/dist').as_posix() for p in files)
source=(root/'android/app/src/main/java/com/hermes/privateapp/MainActivity.java').read_text()
assert 'setMediaPlaybackRequiresUserGesture(true)' in source
assert 'settings.setAllowFileAccess(false)' in source and 'settings.setAllowContentAccess(false)' in source
assert 'WebSettings.MIXED_CONTENT_NEVER_ALLOW' in source
junit=[]
for p in (root/'android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
 t=ET.parse(p).getroot();entry={k:int(t.attrib[k]) for k in ['tests','failures','errors','skipped']};assert entry['failures']==entry['errors']==entry['skipped']==0
 junit.append({'name':t.attrib['name'],**entry})
report={'apk':str(apk),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'versionName':'0.3.4','versionCode':7,'certificate_sha256':cert(apk),'same_certificate_as_0_3_3':True,'packaged_renderer_exact_match':True,'renderer_asset_count':len(files),'autoplay_policy_unchanged':True,'security_restrictions_preserved':True,'junit':junit,'junit_total':sum(e['tests'] for e in junit),'manual_attachment_baseline':json.loads((root/'artifacts/media-playback-baseline.json').read_text()),'manual_attachment_fixed':json.loads((root/'artifacts/media-playback-fixed.json').read_text()),'phone_recovery_verified':False,'android_runtime':'No device connected; no emulator run in this task. /dev/kvm unavailable to current user; prior software emulator/WebView instability remains a limitation.','production_backend_contacted':False,'auto_speech':'User confirmed working after enabling the device-local toggle; no automatic-speech implementation or policy change.'}
(root/'artifacts/media-playback-validation.json').write_text(json.dumps(report,indent=2)+'\n')
(root/'artifacts/hermes-android-0.3.4-debug.apk.sha256').write_text(report['sha256']+'  '+apk.name+'\n')
print(json.dumps(report,indent=2))
