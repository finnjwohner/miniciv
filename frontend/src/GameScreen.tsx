import { useEffect, useState } from 'react'
import type { BuildingType, NationView, UnitType } from './api'
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
function Buildings({ nation, onStart }: { nation: NationView; onStart: (building: string) => void }) {
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

/** A Unit type's costs, training time, Strength, upkeep and counters, in one line. */
function describeUnit(unit: UnitType) {
  const counters = unit.counters.length > 0 ? `, counters ${unit.counters.join(', ')}` : ''
  return (
    `${unit.gold} Gold and ${unit.population} Population, ${plural(unit.turns, 'Turn')} — ` +
    `Strength ${unit.strength}, upkeep ${unit.upkeep} Gold per Turn${counters}`
  )
}

/** One Unit type the Player can train, as many at once as they choose. */
function TrainUnit({
  unit,
  nation,
  onTrain,
}: {
  unit: UnitType
  nation: NationView
  onTrain: (unit: string, count: number) => void
}) {
  const [count, setCount] = useState(1)
  const affordable = count >= 1 && unit.gold * count <= nation.gold && unit.population * count <= nation.population
  return (
    <li>
      <input
        type="number"
        min={1}
        value={count}
        onChange={(e) => setCount(Math.floor(Number(e.target.value)))}
        aria-label={`How many ${unit.name} to train`}
        style={{ width: '4em' }}
      />{' '}
      <button onClick={() => onTrain(unit.name, count)} disabled={!affordable}>
        Train {unit.name}
      </button>{' '}
      {describeUnit(unit)}
    </li>
  )
}

/** The Nation's Army, the Units in training, and the Unit types it can train. */
function Army({ nation, onTrain }: { nation: NationView; onTrain: (unit: string, count: number) => void }) {
  const army = Object.entries(nation.army)
  return (
    <>
      <h3>Army</h3>
      {army.length === 0 && nation.training.length === 0 ? (
        <p>No Units yet.</p>
      ) : (
        <ul>
          {army.map(([name, count]) => (
            <li key={name}>
              {name} ×{count}
            </li>
          ))}
          {nation.training.map((t, i) => (
            <li key={i}>
              {t.unit} ×{t.count} — in training, ready on Turn {t.readyOnTurn}
            </li>
          ))}
        </ul>
      )}
      <p>Upkeep: {nation.upkeep} Gold per Turn</p>
      <h4>Train</h4>
      <ul>
        {nation.availableUnits.map((u) => (
          <TrainUnit key={u.name} unit={u} nation={nation} onTrain={onTrain} />
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
  onTrainUnits,
  onDone,
}: {
  room: ReceivedRoom
  nation: NationView | null
  playerId: string
  rejection: string | null
  onStartBuilding: (building: string) => void
  onTrainUnits: (unit: string, count: number) => void
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
          {rejection && <p role="alert">{rejection}</p>}
          <Buildings nation={nation} onStart={onStartBuilding} />
          <Army nation={nation} onTrain={onTrainUnits} />
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
