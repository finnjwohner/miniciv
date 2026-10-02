import { useEffect, useState } from 'react'
import type { BuildingDamage, BuildingType, NationView, UnitType, Warparty } from './api'
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
  if (building.defense > 0) effects.push(`+${building.defense} Defense`)
  return effects.join(', ')
}

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

function describeDamage(damage: BuildingDamage) {
  const harmed = []
  if (damage.destroyed > 0) harmed.push(`${damage.destroyed} destroyed`)
  if (damage.damaged) harmed.push('1 damaged')
  return `${harmed.join(', ')}, ${damage.defenseLost} Defense lost`
}

/** The Buildings a Nation has and is constructing, and the ones it can start or repair. */
function Buildings({
  nation,
  onStart,
  onRepair,
}: {
  nation: NationView
  onStart: (building: string) => void
  onRepair: (building: string) => void
}) {
  const owned = Object.entries(nation.buildings)
  return (
    <>
      <h3>Buildings</h3>
      {owned.length === 0 && nation.construction.length === 0 ? (
        <p>None yet.</p>
      ) : (
        <ul>
          {owned.map(([name, count]) => {
            const damage = nation.damage.find((d) => d.building === name)
            return (
              <li key={name}>
                {name}
                {count > 1 && ` ×${count}`}
                {damage && (
                  <>
                    {' '}
                    — {describeDamage(damage)}{' '}
                    <button onClick={() => onRepair(name)} disabled={damage.repairGold > nation.gold}>
                      Repair {name}
                    </button>{' '}
                    {damage.repairGold} Gold
                  </>
                )}
              </li>
            )
          })}
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
      <p>Upkeep, at home and abroad: {nation.upkeep} Gold per Turn</p>
      <h4>Train</h4>
      <ul>
        {nation.availableUnits.map((u) => (
          <TrainUnit key={u.name} unit={u} nation={nation} onTrain={onTrain} />
        ))}
      </ul>
    </>
  )
}

/** Units counted by type, such as "Warrior ×2, Archer ×1". */
function listUnits(units: Record<string, number>) {
  return Object.entries(units)
    .map(([name, count]) => `${name} ×${count}`)
    .join(', ')
}

function describeWarparty(warparty: Warparty) {
  const units = listUnits(warparty.units)
  if (!warparty.homeward && warparty.turnsLeft === 0) {
    return `${units} — at ${warparty.target}, fighting the Battle as this Turn ends`
  }
  if (!warparty.homeward) {
    return `${units} — travelling to ${warparty.target}, ${plural(warparty.turnsLeft, 'Turn')} left`
  }
  const { gold, research } = warparty.plunder
  const plunder = gold > 0 || research > 0 ? `, carrying ${gold} Gold and ${research} Research` : ''
  return `${units} — heading home from ${warparty.target}${plunder}, ${plural(warparty.turnsLeft, 'Turn')} left`
}

/** The Nation's Warparties abroad, and a form to send Units from the Army to another Nation. */
function Warparties({
  nation,
  onSend,
}: {
  nation: NationView
  onSend: (target: string, units: Record<string, number>) => void
}) {
  const [target, setTarget] = useState(nation.otherNations[0] ?? '')
  const [chosen, setChosen] = useState<Record<string, number>>({})
  const army = Object.entries(nation.army)
  const units: Record<string, number> = {}
  for (const [name, owned] of army) {
    const count = Math.min(chosen[name] ?? 0, owned)
    if (count > 0) units[name] = count
  }
  const send = () => {
    onSend(target, units)
    setChosen({})
  }
  return (
    <>
      <h3>Warparties</h3>
      {nation.warparties.length === 0 ? (
        <p>No Warparties abroad.</p>
      ) : (
        <ul>
          {nation.warparties.map((w, i) => (
            <li key={i}>{describeWarparty(w)}</li>
          ))}
        </ul>
      )}
      <h4>Send a Warparty</h4>
      {army.length === 0 ? (
        <p>Your Army has no Units to send.</p>
      ) : (
        <>
          <ul>
            {army.map(([name, owned]) => (
              <li key={name}>
                <input
                  type="number"
                  min={0}
                  max={owned}
                  value={chosen[name] ?? 0}
                  onChange={(e) => setChosen({ ...chosen, [name]: Math.floor(Number(e.target.value)) })}
                  aria-label={`How many ${name} to send`}
                  style={{ width: '4em' }}
                />{' '}
                {name} (of {owned})
              </li>
            ))}
          </ul>
          <label>
            To{' '}
            <select value={target} onChange={(e) => setTarget(e.target.value)}>
              {nation.otherNations.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>{' '}
          <button onClick={send} disabled={!target || Object.keys(units).length === 0}>
            Send Warparty
          </button>{' '}
          Takes {plural(nation.travelTurns, 'Turn')} to arrive, and as long to come home.
        </>
      )}
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
  onRepairBuilding,
  onTrainUnits,
  onSendWarparty,
  onDone,
}: {
  room: ReceivedRoom
  nation: NationView | null
  playerId: string
  rejection: string | null
  onStartBuilding: (building: string) => void
  onRepairBuilding: (building: string) => void
  onTrainUnits: (unit: string, count: number) => void
  onSendWarparty: (target: string, units: Record<string, number>) => void
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
            <li>Defense: {nation.defense}</li>
          </ul>
          {rejection && <p role="alert">{rejection}</p>}
          <Buildings nation={nation} onStart={onStartBuilding} onRepair={onRepairBuilding} />
          <Army nation={nation} onTrain={onTrainUnits} />
          <Warparties nation={nation} onSend={onSendWarparty} />
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
