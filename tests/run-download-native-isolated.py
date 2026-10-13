#!/usr/bin/env python3
"""Compile native download sources in scratch, not the shared Gradle build.
Pass exact APK/TXT paths to also run the loopback artifact probe. No device/production access.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
scratch = Path(os.environ['TMPDIR'])
cache = root / 'toolchain/gradle-home/caches'

def dependency(pattern):
    matches = sorted(cache.rglob(pattern))
    if not matches:
        raise RuntimeError(f'Missing cached dependency: {pattern}')
    return matches[0]

with tempfile.TemporaryDirectory(prefix='native-download-tests-', dir=scratch) as folder:
    work = Path(folder)
    jars = [dependency(p) for p in ('okhttp-4.12.0.jar', 'okio-jvm-3.6.0.jar', 'kotlin-stdlib-1.9.10.jar', 'json-20240303.jar', 'junit-4.13.2.jar', 'hamcrest-core-1.3.jar')]
    cp = os.pathsep.join(map(str, jars))
    main = root / 'android/app/src/main/java/com/hermes/privateapp'
    tests = root / 'android/app/src/test/java/com/hermes/privateapp'
    sources = [main / (n + '.java') for n in ('EndpointPolicy', 'GatewayAuth', 'GatewayDownload', 'GatewaySave', 'DownloadFailure')]
    sources += [tests / (n + '.java') for n in ('GatewayDownloadTest', 'DownloadFailureTest')]
    sources += [root / 'tests/native/DownloadArtifactProbe.java']
    subprocess.run(['javac', '-cp', cp, '-d', str(work), *map(str, sources)], check=True)
    runtime = str(work) + os.pathsep + cp
    subprocess.run(['java', '-Djava.io.tmpdir=' + str(work), '-cp', runtime, 'org.junit.runner.JUnitCore', 'com.hermes.privateapp.GatewayDownloadTest', 'com.hermes.privateapp.DownloadFailureTest'], check=True)
    if len(sys.argv) > 1:
        subprocess.run(['java', '-Djava.io.tmpdir=' + str(work), '-cp', runtime, 'com.hermes.privateapp.DownloadArtifactProbe', *sys.argv[1:]], check=True)
    # Compile Activity wiring against the real SDK without touching app/build.
    with zipfile.ZipFile(dependency('webkit-1.12.1.aar')) as aar:
        (work / 'webkit.jar').write_bytes(aar.read('classes.jar'))
    activity_cp = runtime + os.pathsep + str(work / 'webkit.jar') + os.pathsep + str(dependency('annotation-jvm-1.8.1.jar')) + os.pathsep + str(root / 'toolchain/android-sdk/platforms/android-35/android.jar')
    subprocess.run(['javac', '-cp', activity_cp, '-d', str(work), *map(str, main.glob('*.java'))], check=True)
    print('PASS MainActivity and all native sources compile against Android 35; isolated output only.', flush=True)
