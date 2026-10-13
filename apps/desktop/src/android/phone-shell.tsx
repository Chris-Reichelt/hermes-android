import { useStore } from '@nanostores/react'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { WiredPane } from '@/app/contrib/context'
import { $gatewayState, $sessionsLoading, $sessionsLoadError } from '@/store/session'
import { $activeGatewayProfile } from '@/store/profile'
import { $autoSpeakReplies, setAutoSpeakReplies } from '@/store/voice-prefs'

/** Phone chrome only. All history, routing, transcript and composer are Desktop's. */
export function PhoneShell() {
  const [sessions, setSessions] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const gateway = useStore($gatewayState)
  const loading = useStore($sessionsLoading)
  const error = useStore($sessionsLoadError)
  const profile = useStore($activeGatewayProfile)
  const readAloud = useStore($autoSpeakReplies)
  useEffect(() => { setSessions(false) }, [location.pathname])
  useEffect(() => {
    const close = () => setSessions(false)
    window.addEventListener('hermes:phone-close-sessions', close)
    return () => window.removeEventListener('hermes:phone-close-sessions', close)
  }, [])
  useEffect(() => {
    const back = () => { if (sessions) setSessions(false); else if (location.pathname !== '/') navigate(-1) }
    window.addEventListener('hermes:phone-back', back)
    return () => window.removeEventListener('hermes:phone-back', back)
  }, [sessions, location.pathname, navigate])
  useEffect(() => {
    document.documentElement.dataset.phoneBack = sessions || location.pathname !== '/' ? 'true' : 'false'
  }, [sessions, location.pathname])
  return <div className="phone-shell">
    <nav className="phone-nav" aria-label="Phone navigation">
      <button aria-expanded={sessions} aria-controls="phone-sessions" onClick={() => setSessions(!sessions)}>{sessions ? 'Back to chat' : 'Sessions'}</button>
      <button onClick={() => { setSessions(false); window.dispatchEvent(new Event('hermes:phone-new')) }}>New chat</button>
      <button onClick={() => { setSessions(false); navigate('/settings') }}>Settings</button>
    </nav>
    {gateway !== 'open' || loading || error ? <div className="phone-connection" role="status">{gateway === 'open' ? 'Connected' : 'Reconnecting…'}{loading ? ' · Loading history…' : error ? ' · History unavailable — open Sessions to retry' : ''}</div> : null}
    <button type="button" className="phone-read-aloud" role="switch" aria-label="Read aloud" aria-checked={readAloud} title="Read new replies aloud on this device" onClick={() => setAutoSpeakReplies(!readAloud)}>
      <span>Read aloud</span>
      <span className="phone-read-aloud-state" aria-hidden="true">{readAloud ? 'ON' : 'OFF'}</span>
    </button>
    <main className="phone-chat" hidden={sessions}><WiredPane part="chatRoutes" /></main>
    <section id="phone-sessions" className="phone-sessions" aria-label="Session history" hidden={!sessions}><WiredPane part="sidebar" /></section>
  </div>
}
