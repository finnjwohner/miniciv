import { useEffect, useState } from 'react'
import type { BuildingType, NationView } from './api'
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

function describeEffect(building: BuildingType) {
  const effects = []
  if (building.populationPerTurn > 0) effects.push(`+${building.populationPerTurn} Population per Turn`)
  if (building.goldPerPopulation > 0) effects.push(`+${building.goldPerPopulation} Gold per Population per Turn`)
  return effects.join(', ')
}

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

/** The Buildings a Nation has and is constructing, and the ones it can start. */
function Buildings({
  nation,
  rejection,
  onStart,
}: {
  nation: NationView
  rejection: string | null
  onStart: (building: string) => void
}) {
  const owned = Object.entries(nation.buildings)
  return (
    <>
      <h3>Buildings</h3>
      {owned.length === 0 && nation.construction.length === 0 ? (
        <p>None yet.</p>
      ) : (
        <ul>
          {owned.map(([name, count]) => (
            <li key={name}>
              {name}
              {count > 1 && ` ×${count}`}
            </li>
          ))}
          {nation.construction.map((c, i) => (
            <li key={i}>
              {c.building} — under construction, {plural(c.turnsLeft, 'Turn')} left
            </li>
          ))}
        </ul>
      )}
      <h4>Build</h4>
      {rejection && <p role="alert">{rejection}</p>}
      <ul>
        {nation.availableBuildings.map((b) => (
          <li key={b.name}>
            <button onClick={() => onStart(b.name)} disabled={b.gold > nation.gold}>
              Build {b.name}
            </button>{' '}
            {b.gold} Gold, {plural(b.turns, 'Turn')} — {describeEffect(b)}
          </li>
        ))}
      </ul>
    </>
  )
}

/** One Turn of the Game as this Player sees it; they mark themselves done once they have decided. */
function GameScreen({
  room,
  nation,
  playerId,
  rejection,
  onStartBuilding,
  onDone,
}: {
  room: ReceivedRoom
  nation: NationView | null
  playerId: string
  rejection: string | null
  onStartBuilding: (building: string) => void
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
          <Buildings nation={nation} rejection={rejection} onStart={onStartBuilding} />
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
