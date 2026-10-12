import { useState, type FormEvent } from 'react'
import { joinRoom, type Membership } from './api'
import { Icon } from './ui/Icon'
import { Panel } from './ui/Panel'

/** Joining a Room from an invite link: the Player only needs to pick a Nation name. */
function JoinRoom({ code, onJoined }: { code: string; onJoined: (membership: Membership) => void }) {
  const [nationName, setNationName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const join = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onJoined((await joinRoom(code, nationName)).membership)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setBusy(false)
    }
  }

  return (
    <div className="screen screen--narrow">
      <Panel title={`Join Room ${code}`} icon="login">
        <form className="stack" onSubmit={(e) => void join(e)}>
          <p className="muted">You've been invited to Room {code}. Name your Nation to join.</p>
          <div className="field">
            <label className="field__label" htmlFor="nation-name">
              Nation name
            </label>
            <div className="field__row">
              <input id="nation-name" value={nationName} onChange={(e) => setNationName(e.target.value)} required />
              <button type="submit" className="button--primary" disabled={busy || !nationName.trim()}>
                <Icon name="login" size={12} />
                Join
              </button>
            </div>
          </div>
          {error && (
            <p className="alert" role="alert">
              {error}
            </p>
          )}
        </form>
      </Panel>
    </div>
  )
}

export default JoinRoom
