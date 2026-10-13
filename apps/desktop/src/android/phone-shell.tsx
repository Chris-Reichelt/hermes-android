import { useStore } from '@nanostores/react'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { WiredPane } from '@/app/contrib/context'
import { $gatewayState, $sessionsLoading, $sessionsLoadError } from '@/store/session'
import { $activeGatewayProfile, $newChatProfile, $profileOrder, $profiles, profileLabel, selectProfile } from '@/store/profile'
import { sortByProfileOrder } from '@/lib/profile-order'
import { NEW_CHAT_ROUTE } from '@/app/routes'
import { openSession } from '@/app/open-session'
import { listSidebarSessions } from '@/api/sessions'
import { $autoSpeakReplies, setAutoSpeakReplies } from '@/store/voice-prefs'
import { Ear, EarOff } from '@/lib/icons'

/** Phone chrome only. All history, routing, transcript and composer are Desktop's. */
export function PhoneShell() {
  const [sessions, setSessions] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()
  const gateway = useStore($gatewayState)
  const loading = useStore($sessionsLoading)
  const error = useStore($sessionsLoadError)
  const profile = useStore($activeGatewayProfile)
  const profiles = useStore($profiles)
  const profileOrder = useStore($profileOrder)
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
      <button onClick={() => { setSessions(false); $newChatProfile.set(null); navigate(NEW_CHAT_ROUTE) }}>New chat</button>
      <button onClick={() => { setSessions(false); navigate('/settings') }}>Settings</button>
      <select className="phone-profile" aria-label="Bot profile" value={profile}
        onChange={event => {
          setSessions(false)
          const name = event.target.value
          if (name === profile) return
          selectProfile(name)
          // The desktop switcher ends in a fresh draft; the phone should land on
          // the bot's most recent session instead — the sidebar recents page is
          // served newest-first for exactly the picked profile.
          void listSidebarSessions({ recentsProfile: name, recentsLimit: 1, cronLimit: 1, messagingLimit: 1, recentsExclude: [], messagingExclude: [] })
            .then(response => { const newest = response.recents.sessions[0]; if (newest) openSession(newest.id, navigate) })
            .catch(() => {})
        }}>
        {sortByProfileOrder(profiles, profileOrder, item => item.name).map(item =>
          <option key={item.name} value={item.name}>{profileLabel(item)}</option>)}
      </select>
      <button type="button" className="phone-read-aloud" role="switch" aria-label="Read aloud" aria-checked={readAloud} title="Read new replies aloud on this device" onClick={() => setAutoSpeakReplies(!readAloud)}>
        {readAloud ? <Ear className="phone-read-aloud-icon" aria-hidden="true" /> : <EarOff className="phone-read-aloud-icon" aria-hidden="true" />}
        <span className="phone-read-aloud-state" aria-hidden="true">{readAloud ? 'ON' : 'OFF'}</span>
      </button>
    </nav>
    {gateway !== 'open' || loading || error ? <div className="phone-connection" role="status">{gateway === 'open' ? 'Connected' : 'Reconnecting…'}{loading ? ' · Loading history…' : error ? ' · History unavailable — open Sessions to retry' : ''}</div> : null}
    <main className="phone-chat" hidden={sessions}><WiredPane part="chatRoutes" /></main>
    <section id="phone-sessions" className="phone-sessions" aria-label="Session history" hidden={!sessions}><WiredPane part="sidebar" /></section>
  </div>
}
