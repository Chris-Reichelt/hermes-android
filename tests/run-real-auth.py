"""Exercise APK's actual Java auth against isolated production auth components.
No production server/config/plugin discovery or credential files are loaded.
"""
import os, pathlib, secrets, socket, subprocess, sys, tempfile, threading, time
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, '/home/will/.hermes/hermes-agent')
os.environ['HERMES_HOME'] = tempfile.mkdtemp(prefix='android-auth-', dir=os.environ['TMPDIR'])
os.environ['PYTHONDONTWRITEBYTECODE'] = '1'
from fastapi import FastAPI, WebSocket
from hermes_cli.dashboard_auth.routes import router
from hermes_cli.dashboard_auth.middleware import gated_auth_middleware
from hermes_cli.dashboard_auth.registry import register_provider
from hermes_cli.dashboard_auth.ws_tickets import consume_ticket, TicketInvalid
from plugins.dashboard_auth.basic import BasicAuthProvider, hash_password
import uvicorn
password = secrets.token_urlsafe(32)
provider = BasicAuthProvider(username='android-disposable', password_hash=hash_password(password), secret=secrets.token_bytes(32))
register_provider(provider)
app = FastAPI()
app.state.auth_required = True
app.middleware('http')(gated_auth_middleware)
app.include_router(router)
@app.get('/api/status')
def status():
    return {'auth_required': True, 'auth_flows': ['cookie', 'native_pkce']}
@app.get('/api/fs/download')
def fixture_download(path: str, profile: str, session_id: str):
    # Synthetic artifact only; protected by the production auth middleware above.
    from fastapi.responses import Response
    assert path == '/synthetic/test artifact.apk'
    assert profile == 'fixture-owner' and session_id == 'fixture-session'
    return Response(b'ANDROID SYNTHETIC ARTIFACT\x00\x01\xff', media_type='application/octet-stream')
@app.websocket('/api/ws')
async def ws(socket: WebSocket):
    # Real ticket verifier; no agent, messages, bots, or LLM are started.
    try:
        identity = consume_ticket(socket.query_params.get('ticket', ''))
    except TicketInvalid:
        await socket.close(code=1008)
        return
    await socket.accept()
    await socket.send_json({'provider': identity['provider'], 'profile': socket.query_params.get('profile')})
    await socket.close()
listener = socket.socket()
listener.bind(('127.0.0.1', 0))
server = uvicorn.Server(uvicorn.Config(app, access_log=False, log_level='critical'))
thread = threading.Thread(target=lambda: server.run(sockets=[listener]), daemon=True)
thread.start()
for _ in range(100):
    if server.started: break
    time.sleep(.05)
assert server.started
print('Isolated real basic-provider/auth-router/middleware/ticket server ready', flush=True)
env = dict(os.environ, ANDROID_AUTH_TEST_URL=f'http://127.0.0.1:{listener.getsockname()[1]}', ANDROID_AUTH_TEST_PASSWORD=password,
           JAVA_HOME='/usr/lib/jvm/java-21-openjdk-amd64', GRADLE_USER_HOME=str(ROOT/'toolchain/gradle-home'), ANDROID_HOME=str(ROOT/'toolchain/android-sdk'), ANDROID_USER_HOME=str(ROOT/'toolchain/android-user'))
try:
    result = subprocess.run([str(ROOT/'toolchain/gradle-8.11.1/bin/gradle'), '--no-daemon', ':app:testDebugUnitTest', '--rerun-tasks'], cwd=ROOT/'android', env=env)
    if result.returncode == 0:
        import httpx
        from hermes_cli.dashboard_auth.routes import _reset_password_rate_limit
        from hermes_cli.dashboard_auth import ws_tickets
        from unittest.mock import patch
        with httpx.Client(base_url=env['ANDROID_AUTH_TEST_URL']) as client:
            assert client.post('/api/auth/ws-ticket').status_code == 401
            assert client.post('/auth/native/refresh', json={'provider': 'basic', 'refresh_token': 'invalid-disposable'}).status_code == 401
            _reset_password_rate_limit()
            statuses = [client.post('/auth/password-login', json={'provider': 'basic', 'username': 'android-disposable', 'password': 'wrong'}).status_code for _ in range(11)]
            assert statuses == [401] * 10 + [429]
        ticket = ws_tickets.mint_ticket(user_id='android-disposable', provider='basic')
        with patch.object(ws_tickets.time, 'time', return_value=time.time() + 31):
            try:
                ws_tickets.consume_ticket(ticket)
                raise AssertionError('Expired ticket accepted')
            except TicketInvalid:
                pass
        for report in (ROOT/'android/app/build/test-results/testDebugUnitTest').glob('*.xml'):
            assert password not in report.read_text(), 'Disposable password leaked to test report'
        print('PASS: unauthenticated ticket denied; invalid refresh denied; password throttle; 30-second ticket expiry; disposable-password report redaction', flush=True)
finally:
    server.should_exit = True
    thread.join(10)
    import shutil
    shutil.rmtree(os.environ['HERMES_HOME'])
sys.exit(result.returncode)
