import { useEffect, useState } from 'react'
import type { NationView } from './api'
import type { ReceivedRoom } from './useRoom'

/** The time now, updated a few times a second. */
function useNow() {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(timer)
  }, [])
  return now
}

function formatCountdown(millis: number) {
  const seconds = Math.ceil(Math.max(0, millis) / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** One Turn of the Game as this Player sees it; they mark themselves done once they have decided. */
function GameScreen({
  room,
  nation,
  playerId,
  onDone,
}: {
  room: ReceivedRoom
  nation: NationView | null
  playerId: string
  onDone: (turn: number) => void
}) {
  const now = useNow()
  const game = room.game!
  const me = room.players.find((p) => p.id === playerId)
  const turnEndsAt = room.receivedAt + game.millisLeft

  return (
    <section>
      <h2>Turn {game.turn}</h2>
      <p>Time left: {formatCountdown(turnEndsAt - now)}</p>
      {nation && (
        <>
          <h3>{nation.nation}</h3>
          <ul>
            <li>Gold: {nation.gold}</li>
            <li>Population: {nation.population}</li>
            <li>Research: {nation.research}</li>
          </ul>
        </>
      )}
      <button onClick={() => onDone(game.turn)} disabled={!me || me.done}>
        {me?.done ? 'Waiting for the other Players…' : 'Done'}
      </button>
      <h3>Turn Report</h3>
      {nation?.turnReport ? (
        <ul>
          {nation.turnReport.lines.map((line, i) => (
            <li key={i}>{line}</li>
          ))}
        </ul>
      ) : (
        <p>Nothing has happened yet.</p>
      )}
      <h3>Nations</h3>
      <ul>
        {room.players.map((p) => (
          <li key={p.id}>
            {p.nationName}
            {p.id === playerId && ' (you)'}
            {p.done && ' — done'}
          </li>
        ))}
      </ul>
    </section>
  )
}

export default GameScreen
