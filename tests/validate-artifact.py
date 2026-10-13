"""Recompute artifact evidence without reading credentials or mutating live source."""
from pathlib import Path
import hashlib,json,subprocess,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
LIVE=Path('/home/will/.hermes/hermes-agent')
hashfile=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
apk=ROOT/'artifacts/hermes-android-debug.apk'
comparison={}
for part in ['apps/desktop/src','apps/desktop/public','apps/shared/src','apps/desktop/electron']:
    same=0;modified=[];missing=[]
    for p in (LIVE/part).rglob('*'):
        if not p.is_file():continue
        rel=p.relative_to(LIVE);copy=ROOT/rel
        if not copy.is_file():missing.append(str(rel))
        elif hashfile(p)==hashfile(copy):same+=1
        else:modified.append(str(rel))
    comparison[part]={'identical_files':same,'modified_existing_files':modified,'missing_files':missing}
with zipfile.ZipFile(apk) as z:
    mismatches=[str(p.relative_to(ROOT/'apps/desktop/dist')) for p in (ROOT/'apps/desktop/dist').rglob('*') if p.is_file() and z.read('assets/'+str(p.relative_to(ROOT/'apps/desktop/dist')))!=p.read_bytes()]
    count=sum(x.startswith('assets/') and not x.endswith('/') for x in z.namelist())
signer=ROOT/'toolchain/android-sdk/build-tools/35.0.0/apksigner'
def certificate(p):
    output=subprocess.check_output([str(signer),'verify','--print-certs',str(p)],text=True,stderr=subprocess.DEVNULL)
    return next(line for line in output.splitlines() if 'certificate SHA-256 digest:' in line)
original=ROOT/'artifacts/hermes-android-0.1.0-debug.apk'
cert=certificate(apk);assert cert==certificate(original)
(ROOT/'artifacts/v3-signing-certificate.txt').write_text(cert+'\nMatches original 0.1.0 certificate.\n')
junit={k:0 for k in ['tests','skipped','errors','failures']}
for p in (ROOT/'android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    suite=ET.parse(p).getroot()
    for key in junit:junit[key]+=int(suite.get(key,'0'))
assert not mismatches
installed=ROOT/'artifacts/emulator-v3-installed.apk'
assert hashfile(apk)==hashfile(installed)
validation={'source_comparison':comparison,'apk':{'path':str(apk),'package':'com.hermes.privateapp','versionCode':3,'versionName':'0.3.0','bytes':apk.stat().st_size,'sha256':hashfile(apk),'renderer_asset_files':count,'asset_mismatches':mismatches,'same_signing_certificate_as_original':True,'installed_emulator_apk_sha256':hashfile(installed)},'validation':{'renderer_build':'passed','adapter_typecheck':'passed; not full upstream desktop typecheck','vitest_passed':32,'junit':junit,'real_auth_integration':'passed against isolated real production auth components and actual Java GatewayAuth; no live-account credentials','phone_renderer':json.loads((ROOT/'artifacts/phone-ui-results.json').read_text()),'lint':'passed with warnings','android_runtime':'Android 15 software emulator booted; final v0.3.0 APK upgraded, launched; native browser sign-in setup UI inspected; installed base.apk pulled and SHA256 matches final. Launcher/System UI ANRs recovered using Wait. Native authenticated WebView and native WebView IME not exercised. Setup keyboard attempt reported mInputShown=false.','live_backend':'No auth attempt, prompt, session or config mutation; no credentials accessed. Selective redacted gui.log scan found no sidebar HTTP-status evidence, so live history availability remains unverified.'},'diagnosis':{'confirmed':'Desktop pane layout removes collapsible sessions at narrow widths, with desktop titlebar/tiny tab chrome not providing usable phone navigation. Screenshot cannot establish backend history failure. New shell mounts actual wired sidebar/chat routes plus existing settings overlay; error/loading states now visible.','fixture_data_path':'Desktop listSidebarSessions -> native api /api/profiles/sessions/sidebar (codex) -> original sidebar; session.resume -> original transcript; session.create/prompt.submit -> shared JSON-RPC via native adapter. No replacement chat engine.','unverified':'Actual account history and live model response; no production requests were authorized for tests.'},'auth_preserved':['GatewayAuth.java unchanged','system browser native PKCE','native bearer/serialized refresh','fresh single-use WS ticket per socket/reconnect','memory-only auth; process death still requires sign-in']}
(ROOT/'artifacts/validation.json').write_text(json.dumps(validation,indent=2)+'\n')
print(json.dumps({'apk':validation['apk'],'junit':junit,'modified_renderer_files':comparison['apps/desktop/src']['modified_existing_files']},indent=2))
