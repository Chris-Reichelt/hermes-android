"""Validate final download APK without touching any production data."""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
artifacts = ROOT / 'artifacts'
source = ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk'
apk = artifacts / 'hermes-android-0.3.3-debug.apk'
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
if apk.exists():
    assert digest(apk) == digest(source), 'Refusing to overwrite different versioned artifact'
else:
    shutil.copy2(source, apk)
old = artifacts / 'hermes-android-0.3.2-debug.apk'
assert digest(old) == '6f718d50fa58523e730d4c52224ca37ff6f1814033fac649f1e9c89ba9ada97d'
tools = ROOT / 'toolchain/android-sdk/build-tools/35.0.0'
def certificate(path):
    output = subprocess.check_output([str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(path)], text=True)
    return re.search(r'Signer #1 certificate SHA-256 digest: (.+)', output).group(1), output
cert, signature = certificate(apk)
assert cert == certificate(old)[0]
badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
assert "name='com.hermes.privateapp'" in badging and "versionCode='6'" in badging and "versionName='0.3.3'" in badging
assert 'android.permission.RECORD_AUDIO' in badging and 'android.permission.MODIFY_AUDIO_SETTINGS' in badging
assert 'android.permission.MANAGE_EXTERNAL_STORAGE' not in badging
with zipfile.ZipFile(apk) as z:
    dist = ROOT/'apps/desktop/dist'
    expected = {str(p.relative_to(dist)): p for p in dist.rglob('*') if p.is_file()}
    actual = {n[7:] for n in z.namelist() if n.startswith('assets/') and not n.endswith('/')}
    assert actual == set(expected)
    for name,p in expected.items(): assert z.read('assets/'+name) == p.read_bytes(), name
    dex = b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
    assert b'GatewayDownload' in dex and b'android.intent.action.CREATE_DOCUMENT' in dex
    assert b'DownloadInstrumentation' not in dex
counts = {key:0 for key in ['tests','failures','errors','skipped']}
for p in (ROOT/'android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    r = ET.parse(p).getroot()
    for key in counts: counts[key] += int(r.attrib[key])
assert counts['tests'] >= 21 and not any(counts[k] for k in ['failures','errors','skipped'])
receipt = dict(apk=str(apk), sha256=digest(apk), applicationId='com.hermes.privateapp', versionCode=6, versionName='0.3.3', signingCertificateSha256=cert, sameSignatureAs032=True, previousVersionPreserved=True, matchingRendererAssets=len(expected), javaTests=counts, productionBackendContacted=False)
(artifacts/'download-artifact-validation.json').write_text(json.dumps(receipt,indent=2)+'\n')
(artifacts/'download-apk-signature.txt').write_text(signature)
(artifacts/'download-apk-badging.txt').write_text(badging)
(apk.with_suffix('.apk.sha256')).write_text(digest(apk)+'  '+apk.name+'\n')
print(json.dumps(receipt,indent=2))
