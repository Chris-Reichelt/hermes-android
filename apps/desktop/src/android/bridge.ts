/** Android shell adapter. Session credentials are in native memory only. */
export interface NativePort { postMessage(value: string): void; onmessage: ((event: {data: string}) => void) | null }
export function createNativeRpc(port: NativePort) {
  let sequence = 0
  const pending = new Map<number, {resolve: (value: any) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout>}>()
  const events = new Set<(event: any) => void>()
  port.onmessage = ({data}) => {
    const message = JSON.parse(data)
    if (message.event) { for (const listener of events) listener(message); return }
    const call = pending.get(message.id)
    if (!call) return
    clearTimeout(call.timer); pending.delete(message.id)
    if (message.error) call.reject(new Error(message.error)); else call.resolve(message.result)
  }
  return {
    request(method: string, params: any = {}): Promise<any> {
      return new Promise((resolve, reject) => {
        const id = ++sequence
        // Audio transcription/speak block server-side far past a normal API
        // call; the native client now allows 300s, so api must outlast it.
        const timer = setTimeout(() => { pending.delete(id); reject(new Error(`Android bridge timed out: ${method}`)) }, method.startsWith('download.') ? 600000 : method === 'api' ? 310000 : 65000)
        pending.set(id, {resolve, reject, timer})
        port.postMessage(JSON.stringify({id, method, params}))
      })
    },
    subscribe(listener: (event: any) => void) { events.add(listener); return () => events.delete(listener) }
  }
}

export function installAndroidBridge(port: NativePort) {
  const native = createNativeRpc(port)
  const unsupported = async () => { throw new Error('Not available in the initial Android port') }
  const noEvent = () => () => {}
  const noop = () => {}
  const claims = new Set<string>()
  let selectedProfile: string | null = null
  let socketId = 0
  // Native sockets keep the actual shared JSON-RPC channel unchanged and avoid
  // weakening the gateway's Origin check for a packaged WebView asset origin.
  class AndroidSocket extends EventTarget {
    static CONNECTING = 0; static OPEN = 1; static CLOSING = 2; static CLOSED = 3
    CONNECTING = 0; OPEN = 1; CLOSING = 2; CLOSED = 3
    readyState = 0; bufferedAmount = 0; binaryType = 'arraybuffer'; protocol = ''; extensions = ''
    onopen: any; onmessage: any; onerror: any; onclose: any
    private id = ++socketId
    private unsubscribe: () => void
    private deliver(event: Event) {
      this.dispatchEvent(event)
      const handler = (this as any)[`on${event.type}`]
      if (typeof handler === 'function') handler.call(this, event)
    }
    constructor(public url: string, protocols?: string | string[]) {
      super()
      this.unsubscribe = native.subscribe(message => {
        if (message.socket !== this.id) return
        if (message.event === 'open') this.readyState = 1
        if (message.event === 'close') this.readyState = 3
        let data: any = message.data
        if (message.binary) {
          const bytes = Uint8Array.from(atob(message.data), character => character.charCodeAt(0))
          data = this.binaryType === 'arraybuffer' ? bytes.buffer : new Blob([bytes])
        }
        const event = message.event === 'message' ? new MessageEvent('message', {data}) :
          message.event === 'close' ? new CloseEvent('close', {code: message.code || 1006, reason: message.reason || '', wasClean: message.code === 1000}) : new Event(message.event)
        this.deliver(event)
        if (message.event === 'close') this.unsubscribe()
      })
      native.request('socket.open', {id: this.id, url, protocols}).catch(() => {
        this.readyState = 3; this.deliver(new Event('error')); this.deliver(new CloseEvent('close', {code: 1006})); this.unsubscribe()
      })
    }
    send(data: string) {
      if (this.readyState !== 1) throw new DOMException('Socket is not open', 'InvalidStateError')
      if (typeof data !== 'string') throw new Error('Binary native sockets are not implemented')
      void native.request('socket.send', {id: this.id, data}).catch(() => this.deliver(new Event('error')))
    }
    close(code = 1000, reason = '') { this.readyState = 2; void native.request('socket.close', {id: this.id, code, reason}) }
  }
  window.WebSocket = AndroidSocket as any
  const connection = (profile?: string | null) => native.request('connection', {profile: profile || selectedProfile})
  const scoped = (params: any = {}) => {
    if (params.connectionId && params.connectionId !== 'android-remote') return Promise.reject(new Error('Unknown Android connection; refusing to retarget'))
    return connection(params.profile)
  }
  const api = (params: any) => native.request('api', params)
  const profileState = async () => ({profile: selectedProfile || (await connection()).profile})
  const registry = async () => {
    const c = await connection()
    return {version: 2, primary: 'android-remote', secureTokenStorage: false, connections: [{id: 'android-remote', kind: 'remote', label: 'Private gateway', url: c.baseUrl, authMode: c.authMode, tokenSet: c.authMode === 'token', tokenPreview: null}]}
  }
  const bridge: any = {
    platform: 'android', glassSupported: false, translucencySupported: false,
    localModelsEnabled: false, guestOnboardingEnabled: false, localSkin: null,
    getConnection: connection, getConnectionFor: scoped,
    getGatewayWsUrl: async (profile: string) => ({ok: true, wsUrl: (await connection(profile)).wsUrl}),
    getGatewayWsUrlFor: async (params: any) => ({ok: true, wsUrl: (await scoped(params)).wsUrl}),
    getProfileRoutes: async (profiles: string[]) => profiles.map(profile => ({connectionId: 'android-remote', mode: 'remote', profile, targetProfile: profile})),
    api,
    getAgentRoster: async () => {
      const data = await api({path: '/api/profiles'})
      return {agents: data.profiles.map((p: any) => ({...p, connectionId: 'android-remote', connectionKind: 'remote', connectionLabel: 'Private gateway', profile: p.name, targetProfile: p.name, handle: p.name})), sources: [{connectionId: 'android-remote', label: 'Private gateway', kind: 'remote', reachable: true}]}
    },
    revalidateConnection: async () => { await api({path: '/api/status'}); return {ok: true, rebuilt: false} },
    touchBackend: async () => ({ok: true}), // no local process pool exists on Android
    getPoolLimits: async () => ({maxBackends: 1, idleMs: 0}), setPoolLimits: unsupported,
    getBootProgress: async () => ({error: null, fakeMode: false, message: 'Connecting to private gateway', phase: 'connecting', progress: 0, running: false, timestamp: Date.now()}),
    profile: {
      get: profileState, getDefault: async () => ({connectionId: 'android-remote', profile: (await profileState()).profile}),
      remember: async (name: string) => { selectedProfile = name; return profileState() },
      setDefault: async (route: any) => { await scoped(route); selectedProfile = route.profile; return route },
      onChanged: noEvent, onDefaultChanged: noEvent
    },
    connections: {list: registry, onChanged: noEvent, setLastUsed: async () => ({ok: true, registry: await registry()}), save: unsupported, remove: unsupported, setPrimary: unsupported, test: unsupported},
    getConnectionConfig: async () => { const c = await connection(); return {mode: 'remote', remote: {url: c.baseUrl, authMode: c.authMode, tokenSet: c.authMode === 'token'}, source: 'settings'} },
    getVersion: async () => ({version: '0.3.5-android', packaged: true}),
    getMachineProfile: async () => null,
    getRemoteDisplayReason: async () => null,
    desktopPluginsRoot: async () => '', // external Electron plugin directories are unavailable
    getSecretStorageEncryption: async () => ({on: false}),
    claimAmbientCue: async (key: string) => { if (claims.has(key)) return false; claims.add(key); return true },
    requestMicrophoneAccess: () => native.request('microphone'),
    readClipboard: () => native.request('clipboard.read'),
    writeClipboard: (text: string) => native.request('clipboard.write', {text}),
    openExternal: (url: string) => native.request('external', {url}),
    saveGatewayFile: (params: any) => native.request('download.file', {...params, profile: params.profile || selectedProfile}),
    // The phone's file picker is the native attachment picker: one
    // ACTION_OPEN_DOCUMENT per call; the staged-handle array is exactly the
    // path list `selectDesktopPaths` returns back (Android has no window
    // chrome, so title/defaultPath/filters are advisory and dropped).
    selectPaths: async (options?: any) => {
      const filters = Array.isArray(options?.filters) ? options.filters : []
      const images = filters.some((filter: any) => (filter?.extensions ?? []).some((extension: string) => ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'tiff'].includes(String(extension).toLowerCase())))
      if (options?.directories) throw new Error('Choose individual files on Android')
      const handles = await native.request('attachment.pick', { profile: selectedProfile, images })
      return Array.isArray(handles) ? handles : []
    },
    // Staged picks live in native cache; `attachment.read` answers with the
    // same `data:<mime>;base64,` URL the desktop's readFileDataUrl returns.
    readFileDataUrl: async (path: string) => {
      if (typeof path !== 'string' || !path.startsWith('android-attachment:')) throw new Error('Not an Android attachment handle')
      return await native.request('attachment.read', { path, profile: selectedProfile })
    },
    readGatewayMedia: async (params: any) => {
      // Snapshot ownership before the native request; do not re-resolve after await.
      const result = await native.request('media.read', {...params, profile: params.profile || selectedProfile})
      if (typeof result?.base64 !== 'string' || result.base64.length > Math.ceil(8 * 1024 * 1024 / 3) * 4) throw new Error('Media exceeds 8 MiB')
      const bytes = Uint8Array.from(atob(result.base64), character => character.charCodeAt(0))
      return URL.createObjectURL(new Blob([bytes], {type: params.mime}))
    },
    saveImageFromUrl: async (url: string) => {
      // Blob URLs exist only inside the renderer. Transfer bounded image data,
      // never a gateway token or an authenticated fetch to WebView.
      if (url.startsWith('blob:')) {
        const blob = await (await fetch(url)).blob()
        if (!blob.type.startsWith('image/') || blob.size > 16 * 1024 * 1024) throw new Error('Image exceeds 16 MiB or is not an image')
        url = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(String(reader.result))
          reader.onerror = () => reject(new Error('Cannot read image'))
          reader.readAsDataURL(blob)
        })
      }
      return native.request('download.image', {url, profile: selectedProfile})
    },
    notify: unsupported,
    windowControls: {custom: false, minimize: noop, toggleMaximize: noop, close: noop},
    setActiveWork: noop, setTitleBarTheme: noop, setNativeTheme: noop, setTranslucency: async () => ({supported: false}),
    setKeepAwake: (enabled: boolean) => native.request('keepAwake', {enabled}),
    setPreviewShortcutActive: noop, setF12ShortcutActive: noop, signalDeepLinkReady: noop,
    logLine: noop, reportRendererError: noop,
    quickEntry: {onSubmit: noEvent, onState: noEvent, onShown: noEvent, pushState: noop, getSettings: async () => ({enabled: false, registered: false, shortcut: ''}), setSettings: unsupported},
    petOverlay: {onControl: noEvent, onState: noEvent, pushState: noop, open: unsupported, close: unsupported},
  }
  for (const name of ['onBootProgress','onBackendExit','onPowerResume','onConnectionApplied','onWindowStateChanged','onBrowserPopoutClosed','onExternalOpenFailed','onFoundInPage','onOpenFindBarRequested','onContextMenuSpellcheck','onOpenUpdatesRequested','onFocusSession','onNotificationAction','onNotificationActivate','onDeepLink','onClosePreviewRequested','onPreviewNav','onOpenFolderRequested','onPreviewFileChanged']) bridge[name] = noEvent
  // `selectPaths` and `readFileDataUrl` are implemented above (native
  // attachment picker/read) — keep them out of the unsupported list.
  for (const name of ['selectSavePath','openSessionWindow','openSessionInTerminal','openWindow','openBrowserWindow','normalizePreviewTarget','reachPreviewUrl','revealLogs','resetBootstrap','repairBootstrap','applyConnectionConfig','saveConnectionConfig','testConnectionConfig','probeConnectionConfig','recycleBackend','relaunchApp','revealPath','openDir','saveImageBuffer','saveClipboardImage','reconcileDesktopPlugins','oauthLoginConnectionConfig','oauthLogoutConnectionConfig','setSecretStorageEncryption']) bridge[name] = unsupported
  ;(window as any).hermesDesktop = bridge
  return bridge
}
