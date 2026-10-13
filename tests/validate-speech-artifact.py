"""Verify the exact speech build without touching the production backend."""
from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile, xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
a=R/'artifacts/hermes-android-0.3.2-debug.apk'
shutil.copyfile(R/'artifacts/hermes-android-debug.apk',a)
tools=R/'toolchain/android-sdk/build-tools/35.0.0'
def cert(p):
    s=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(p)],text=True,stderr=subprocess.DEVNULL)
    return next(l.split(': ',1)[1] for l in s.splitlines() if 'certificate SHA-256 digest:' in l)
assert cert(a)==cert(R/'artifacts/hermes-android-0.3.1-debug.apk')
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(a)],text=True)
assert "versionCode='5'" in badging and "versionName='0.3.2'" in badging
for permission in ['android.permission.RECORD_AUDIO','android.permission.MODIFY_AUDIO_SETTINGS']:
    assert permission in badging
with zipfile.ZipFile(a) as z:
    dist=R/'apps/desktop/dist'
    files=[p for p in dist.rglob('*') if p.is_file()]
    expected={'assets/'+str(p.relative_to(dist)) for p in files}
    packaged={n for n in z.namelist() if n.startswith('assets/') and not n.endswith('/')}
    assert expected==packaged
    assert all(z.read('assets/'+str(p.relative_to(dist)))==p.read_bytes() for p in files)
suites=[ET.parse(p).getroot() for p in (R/'android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
junit={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']}
assert junit['tests'] and not junit['errors'] and not junit['failures']
sha=hashlib.sha256(a.read_bytes()).hexdigest()
report={'apk':str(a),'sha256':sha,'bytes':a.stat().st_size,'versionName':'0.3.2','versionCode':5,'same_certificate_as_0_3_1':True,'certificate_sha256':cert(a),'packaged_renderer_exact_match':True,'renderer_asset_count':len(files),'microphone_permissions_preserved':True,'junit':junit,'phone_runtime':'Not exercised: adb devices returned no connected device; previous emulator WebView SIGTRAP remains a limitation.','production_backend_contacted':False,'phone_recovery_verified':False}
(R/'artifacts/auto-speech-artifact-validation.json').write_text(json.dumps(report,indent=2)+'\n')
(R/'artifacts/hermes-android-0.3.2-debug.apk.sha256').write_text(f'{sha}  {a.name}\n')
print(json.dumps(report,indent=2))
