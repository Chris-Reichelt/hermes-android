"""Verify final link-editing APK against its built renderer and previous track."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
apk = root / 'artifacts/hermes-android-0.3.38-apk50-public-preview.apk'
previous = root / 'hermes-android-0.3.37-apk49.apk'
source = root / 'android/app/build/outputs/apk/debug/app-debug.apk'
shutil.copy2(source, apk)
tools = root / 'toolchain/android-sdk/build-tools/35.0.0'
def run(*args):
    return subprocess.check_output([str(a) for a in args], text=True, stderr=subprocess.PIPE)
def cert(path):
    text = run(tools / 'apksigner', 'verify', '--verbose', '--print-certs', path)
    match = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', text)
    assert match is not None, 'No certificate SHA-256 in apksigner output'
    return match.group(1)
current_cert, old_cert = cert(apk), cert(previous)
assert current_cert == old_cert, 'Signing identity changed'
badging = run(tools / 'aapt', 'dump', 'badging', apk)
assert "name='com.hermes.privateapp.publicpreview' versionCode='50' versionName='0.3.38-apk50-public-preview'" in badging
built = root / 'apps/desktop/dist'
extract = Path(os.environ['TMPDIR']) / 'link-editing-apk50-renderer'
extract.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(apk) as z:
    assets = {n[7:]: z.read(n) for n in z.namelist() if n.startswith('assets/') and not n.endswith('/')}
expected = {str(p.relative_to(built)): p.read_bytes() for p in built.rglob('*') if p.is_file()}
assert assets.keys() == expected.keys(), 'Packaged asset names differ'
assert all(assets[n] == expected[n] for n in expected), 'Packaged asset bytes differ'
for name, data in assets.items():
    path = extract / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
reports = [ET.parse(p).getroot() for p in (root / 'android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
counts = {k: sum(int(r.attrib.get(k, 0)) for r in reports) for k in ['tests','failures','errors','skipped']}
assert counts['tests'] > 0 and counts['failures'] == counts['errors'] == 0
receipt = {'apk':str(apk), 'package':'com.hermes.privateapp.publicpreview', 'versionCode':50,
           'versionName':'0.3.38-apk50-public-preview', 'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
           'bytes':apk.stat().st_size, 'signingCertificateSha256':current_cert, 'matchesApk49SigningIdentity':True,
           'allPackagedRendererAssetsByteEqual':True, 'packagedRendererFiles':len(assets),
           'extractedRenderer':str(extract), 'junit':counts, 'physicalDeviceVerified':False}
(root/'artifacts/link-editing-apk50-verification.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps(receipt,indent=2))
