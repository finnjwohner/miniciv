import { useState, type FormEvent } from 'react'
import { createRoom, joinRoom } from './api'
import { Icon } from './ui/Icon'
import { Panel } from './ui/Panel'

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
    <div className="screen screen--narrow">
      <Panel title="Found a Nation" icon="flag">
        <form className="stack" onSubmit={join}>
          <label className="field">
            <span className="field__label">Nation name</span>
            <input value={nationName} onChange={(e) => setNationName(e.target.value)} required />
          </label>
          <button
            type="button"
            className="button--primary"
            onClick={() => void create()}
            disabled={busy || !nationName.trim()}
          >
            <Icon name="plus" size={12} />
            Create a Room
          </button>
          <div className="divider">or</div>
          <div className="field">
            <label className="field__label" htmlFor="room-code">
              Room code
            </label>
            <div className="field__row">
              <input id="room-code" value={code} onChange={(e) => setCode(e.target.value)} />
              <button type="submit" disabled={busy || !code.trim()}>
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

export default Home
