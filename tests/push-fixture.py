import argparse
import asyncio

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
import uvicorn


app = FastAPI()
sockets = set()
connections = 0
closing = set()


@app.get('/api/status')
async def status():
    return {'auth_required': False}


@app.get('/state')
async def state():
    return {'active': len(sockets), 'connections': connections}


@app.websocket('/api/push')
async def push(socket: WebSocket):
    global connections
    if socket.query_params.get('token') != 'synthetic-push-test':
        await socket.close(code=1008)
        return
    await socket.accept()
    sockets.add(socket)
    connections += 1
    try:
        while True:
            await socket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        sockets.discard(socket)


@app.post('/emit/{scenario}')
async def emit(scenario: str, run: str = 'fixture'):
    if scenario not in {'foreground', 'background', 'delta', 'reconnected', 'returned', 'destroyed'}:
        raise HTTPException(400, 'Unknown synthetic scenario')
    if len(sockets) != 1:
        raise HTTPException(409, 'Expected exactly one listener')
    socket = next(iter(sockets))
    fragments = [('message.complete', 'synthetic ' + scenario)]
    if scenario == 'delta':
        fragments = [('message.delta', 'synthetic '), ('message.delta', 'delta'),
                     ('message.complete', '')]
    for event_type, text in fragments:
        await socket.send_json({'method': 'event', 'params': {
            'type': event_type, 'session_id': 'push-test-' + run + '-' + scenario,
            'payload': {'text': text}}})
    return {'sent': len(fragments)}


@app.post('/close')
async def close():
    for socket in list(sockets):
        sockets.discard(socket)
        task = asyncio.create_task(socket.close(code=1012, reason='synthetic reconnect test'))
        closing.add(task)
        task.add_done_callback(closing.discard)
    return {'closed': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=18765)
    args = parser.parse_args()
    uvicorn.run(app, host='127.0.0.1', port=args.port)
