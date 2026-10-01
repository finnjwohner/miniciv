import { useState, type FormEvent } from 'react'
import { createRoom, joinRoom } from './api'

/** Create a Room, or join one by its room code; either way the Player picks a Nation name. */
function Home({ onJoined }: { onJoined: (code: string) => void }) {
  const [nationName, setNationName] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const run = async (joinSomeRoom: () => Promise<string>) => {
    setBusy(true)
    setError(null)
    try {
      onJoined(await joinSomeRoom())
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
    }
  }

  const join = (e: FormEvent) => {
    e.preventDefault()
    const roomCode = code.trim().toUpperCase()
    void run(async () => {
      await joinRoom(roomCode, nationName)
      return roomCode
    })
  }

  const create = () =>
    run(async () => {
      const roomCode = await createRoom()
      await joinRoom(roomCode, nationName)
      return roomCode
    })

  return (
    <form onSubmit={join}>
      <label>
        Nation name <input value={nationName} onChange={(e) => setNationName(e.target.value)} required />
      </label>
      <p>
        <button type="button" onClick={() => void create()} disabled={busy || !nationName.trim()}>
          Create a Room
        </button>
      </p>
      <p>
        <label>
          Room code <input value={code} onChange={(e) => setCode(e.target.value)} />
        </label>{' '}
        <button type="submit" disabled={busy || !code.trim()}>
          Join
        </button>
      </p>
      {error && <p role="alert">{error}</p>}
    </form>
  )
}

export default Home
