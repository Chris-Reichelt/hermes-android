import { installAndroidBridge } from './bridge'
import './mobile.css'

const port = (window as any).HermesNative
if (!port) {
  document.getElementById('root')!.textContent = 'Android secure native bridge is unavailable. Open this build in the Android app.'
} else {
  document.documentElement.dataset.android = 'true'
  const resize = () => document.documentElement.style.setProperty('--phone-height', `${window.visualViewport?.height ?? window.innerHeight}px`)
  window.visualViewport?.addEventListener('resize', resize)
  window.addEventListener('resize', resize)
  resize()
  installAndroidBridge(port)
  void import('../main')
}
