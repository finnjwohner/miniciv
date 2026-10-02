import { useEffect, useState } from 'react'
import { otherNations } from './api'
import type { BuildingDamage, BuildingType, MapNation, NationView, UnitType, Warparty } from './api'
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

/** A defensive Building type's damaged and destroyed Buildings, with buttons to repair and rebuild them. */
function Damage({
  damage,
  building,
  nation,
  onRepair,
  onRebuild,
}: {
  damage: BuildingDamage
  building: BuildingType
  nation: NationView
  onRepair: (building: string) => void
  onRebuild: (building: string) => void
}) {
  return (
    <ul>
      {damage.damaged > 0 && (
        <li>
          1 damaged, {damage.damaged} Defense lost{' '}
          <button onClick={() => onRepair(damage.building)} disabled={damage.repairGold > nation.gold}>
            Repair {damage.building}
          </button>{' '}
          {damage.repairGold} Gold, {plural(damage.repairTurns, 'Turn')}
        </li>
      )}
      {damage.destroyed > 0 && (
        <li>
          {damage.destroyed} destroyed{' '}
          <button onClick={() => onRebuild(damage.building)} disabled={building.gold > nation.gold}>
            Rebuild {damage.building}
          </button>{' '}
          {building.gold} Gold, {plural(building.turns, 'Turn')}
        </li>
      )}
    </ul>
  )
}

/** The Buildings a Nation has and is constructing or repairing, and the ones it can start, repair or rebuild. */
function Buildings({
  nation,
  onStart,
  onRepair,
  onRebuild,
}: {
  nation: NationView
  onStart: (building: string) => void
  onRepair: (building: string) => void
  onRebuild: (building: string) => void
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
            const building = nation.availableBuildings.find((b) => b.name === name)
            return (
              <li key={name}>
                {name}
                {count > 1 && ` ×${count}`}
                {damage && building && (
                  <Damage
                    damage={damage}
                    building={building}
                    nation={nation}
                    onRepair={onRepair}
                    onRebuild={onRebuild}
                  />
                )}
              </li>
            )
          })}
          {nation.construction.map((c, i) => (
            <li key={i}>
              {c.building} — under construction, {plural(c.turnsLeft, 'Turn')} left
            </li>
          ))}
          {nation.repairs.map((r, i) => (
            <li key={i}>
              {r.building} — {r.rebuild ? 'being rebuilt' : 'being repaired'}, {plural(r.turnsLeft, 'Turn')} left
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

/** Units or Buildings counted by type, such as "Warrior ×2, Archer ×1". */
function listCounts(units: Record<string, number>) {
  return Object.entries(units)
    .map(([name, count]) => `${name} ×${count}`)
    .join(', ')
}

function describeWarparty(warparty: Warparty) {
  const units = listCounts(warparty.units)
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
  const targets = otherNations(nation)
  const [target, setTarget] = useState(targets[0] ?? '')
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
              {targets.map((n) => (
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

/** How the Map marks a Nation no longer in the Game. */
function describeStanding(standing: MapNation['standing']) {
  if (standing === 'CONQUERED') return ' — knocked out'
  if (standing === 'REMOVED') return ' — removed'
  return ''
}

/**
 * A read-only placeholder Map of every Nation in the Game, drawn from this Player's view alone: their
 * own Nation in full, every other Nation by name, and which have been knocked out or removed.
 */
function WorldMap({ nation }: { nation: NationView }) {
  return (
    <>
      <h3>Map</h3>
      <ul>
        {nation.map.map((other) =>
          other.name === nation.nation ? (
            <li key={other.name}>
              <strong>{other.name} (you)</strong>
              {describeStanding(other.standing)}
              <ul>
                <li>
                  Gold {nation.gold}, Population {nation.population}, Research {nation.research}, Defense{' '}
                  {nation.defense}, upkeep {nation.upkeep} Gold per Turn
                </li>
                <li>Buildings: {listCounts(nation.buildings) || 'none'}</li>
                {nation.construction.map((c, i) => (
                  <li key={`construction-${i}`}>
                    {c.building} under construction, {plural(c.turnsLeft, 'Turn')} left
                  </li>
                ))}
                {nation.damage.map((d) => (
                  <li key={`damage-${d.building}`}>
                    {d.building}: {d.destroyed} destroyed{d.damaged > 0 && `, 1 damaged with ${d.damaged} Defense lost`}
                  </li>
                ))}
                {nation.repairs.map((r, i) => (
                  <li key={`repair-${i}`}>
                    {r.building} {r.rebuild ? 'being rebuilt' : 'being repaired'}, {plural(r.turnsLeft, 'Turn')} left
                  </li>
                ))}
                <li>Army: {listCounts(nation.army) || 'none'}</li>
                {nation.training.map((t, i) => (
                  <li key={`training-${i}`}>
                    {t.unit} ×{t.count} in training, ready on Turn {t.readyOnTurn}
                  </li>
                ))}
                {nation.warparties.map((w, i) => (
                  <li key={`warparty-${i}`}>Warparty: {describeWarparty(w)}</li>
                ))}
              </ul>
            </li>
          ) : (
            <li key={other.name}>
              {other.name}
              {describeStanding(other.standing)}
            </li>
          ),
        )}
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
  onRepairBuilding,
  onRebuildBuilding,
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
  onRebuildBuilding: (building: string) => void
  onTrainUnits: (unit: string, count: number) => void
  onSendWarparty: (target: string, units: Record<string, number>) => void
  onDone: (turn: number) => void
}) {
  const now = useNow()
  const game = room.game!
  const me = room.players.find((p) => p.id === playerId)
  const turnEndsAt = room.receivedAt + game.millisLeft
  const knockedOut = me !== undefined && game.conquered.includes(me.nationName)
  const removed = me !== undefined && game.removed.includes(me.nationName)
  const playing = !game.winner && !knockedOut && !removed
  const paused = game.waitingFor.length > 0

  return (
    <section>
      <h2>Turn {game.turn}</h2>
      {game.winner && (
        <p role="status">
          <strong>{game.winner === me?.nationName ? 'You have won the Game!' : `${game.winner} has won the Game.`}</strong>
        </p>
      )}
      {knockedOut && (
        <p role="status">
          <strong>
            Your Nation was conquered{nation?.conqueredBy && ` by ${nation.conqueredBy}`}. You are knocked out of the
            Game.
          </strong>
        </p>
      )}
      {removed && (
        <p role="status">
          <strong>Your Nation was removed from the Game, as you were disconnected for too long.</strong>
        </p>
      )}
      {game.removed.some((n) => n !== me?.nationName) && (
        <p role="status">
          Removed from the Game, as their Players were disconnected for too long:{' '}
          {game.removed.filter((n) => n !== me?.nationName).join(', ')}.
        </p>
      )}
      {!game.winner && paused && (
        <div role="status">
          <p>
            <strong>The Game is paused.</strong>
          </p>
          <ul>
            {game.waitingFor.map((w) => (
              <li key={w.nation}>
                Waiting for {w.nation} to reconnect; removed from the Game in{' '}
                {formatCountdown(room.receivedAt + w.millisLeft - now)}.
              </li>
            ))}
          </ul>
        </div>
      )}
      {playing && <p>Time left: {formatCountdown(paused ? game.millisLeft : turnEndsAt - now)}</p>}
      {nation && playing && (
        <>
          <h3>{nation.nation}</h3>
          <ul>
            <li>Gold: {nation.gold}</li>
            <li>Population: {nation.population}</li>
            <li>Research: {nation.research}</li>
            <li>Defense: {nation.defense}</li>
          </ul>
          {rejection && <p role="alert">{rejection}</p>}
          <Buildings
            nation={nation}
            onStart={onStartBuilding}
            onRepair={onRepairBuilding}
            onRebuild={onRebuildBuilding}
          />
          <Army nation={nation} onTrain={onTrainUnits} />
          <Warparties nation={nation} onSend={onSendWarparty} />
        </>
      )}
      {playing && (
        <button onClick={() => onDone(game.turn)} disabled={!me || me.done}>
          {me?.done ? 'Waiting for the other Players…' : 'Done'}
        </button>
      )}
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
      {nation && <WorldMap nation={nation} />}
      <h3>Nations</h3>
      <ul>
        {room.players.map((p) => (
          <li key={p.id}>
            {p.nationName}
            {p.id === playerId && ' (you)'}
            {game.conquered.includes(p.nationName)
              ? ' — knocked out'
              : game.removed.includes(p.nationName)
                ? ' — removed'
                : p.nationName === game.winner
                  ? ' — winner'
                  : game.waitingFor.some((w) => w.nation === p.nationName)
                    ? ' — disconnected'
                    : p.done && ' — done'}
          </li>
        ))}
      </ul>
    </section>
  )
}

export default GameScreen
