import { useState, type FormEvent } from 'react'
import { joinRoom, type Membership } from './api'

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
      onJoined(await joinRoom(code, nationName))
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={(e) => void join(e)}>
      <p>Join Room {code}</p>
      <label>
        Nation name <input value={nationName} onChange={(e) => setNationName(e.target.value)} required />
      </label>{' '}
      <button type="submit" disabled={busy || !nationName.trim()}>
        Join
      </button>
      {error && <p role="alert">{error}</p>}
    </form>
  )
}

export default JoinRoom
