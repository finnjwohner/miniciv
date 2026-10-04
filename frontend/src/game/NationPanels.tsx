import { useState } from 'react'
import type { BuildingDamage, BuildingType, GoldForecast, NationView, TechTreeEntry, UnitType } from '../api'
import { Icon, type IconName } from '../ui/Icon'
import { Panel } from '../ui/Panel'
import { CountInput } from './controls'
import { formatChange, mergeAlike, plural } from './format'
import { rowClass, useCountUp, useFreshRows } from './turnStart'

/**
 * One resource, counting to its `value` from before it changed by `change` as it mounts, with the change
 * floating up from it in green or red, unless motion is reduced.
 */
function Stat({ icon, label, value, change = 0 }: { icon: IconName; label: string; value: number; change?: number }) {
  const { shown, counting } = useCountUp(value, change)
  return (
    <div className="stat">
      <Icon name={icon} />
      <span className="stat__label">{label}</span>
      <span className="stat__value">{shown}</span>
      {counting && (
        <span className={`stat__float stat__float--${change > 0 ? 'good' : 'bad'}`} aria-hidden="true">
          {formatChange(change)}
        </span>
      )}
    </div>
  )
}

/**
 * The Nation's Gold, Population, Research and Defense, the Gold its Units cost each Turn, and the Gold it will
 * gain or lose as this Turn resolves. As each new Turn arrives, Gold, Population and Research count from before
 * the Turn resolved to their new values.
 */
export function ResourcesPanel({ nation }: { nation: NationView }) {
  const [firstTurn] = useState(nation.turn)
  const netChange = nation.turn !== firstTurn ? nation.turnReport?.netChange : undefined
  return (
    <Panel title={nation.nation} icon="flag" className="dash__resources">
      <div className="stats" key={nation.turn}>
        <Stat icon="coins" label="Gold" value={nation.gold} change={netChange?.gold} />
        <Stat icon="human" label="Population" value={nation.population} change={netChange?.population} />
        <Stat icon="lightbulb" label="Research" value={nation.research} change={netChange?.research} />
        <Stat icon="shield" label="Defense" value={nation.defense} />
      </div>
      <p className="muted">Upkeep, at home and abroad: {nation.upkeep} Gold per Turn</p>
      <GoldForecastLine forecast={nation.goldForecast} />
    </Panel>
  )
}

/** The Gold the Nation will gain or lose as this Turn resolves, with its income and upkeep, in red if a loss. */
function GoldForecastLine({ forecast }: { forecast: GoldForecast }) {
  const change = forecast.income - forecast.upkeep
  return (
    <p className={change < 0 ? 'report__loss' : 'muted'}>
      Next Turn: {formatChange(change)} Gold (income {forecast.income} − upkeep {forecast.upkeep})
    </p>
  )
}

function describeEffect(building: BuildingType) {
  const effects = []
  if (building.populationPerTurn > 0) effects.push(`+${building.populationPerTurn} Population per Turn`)
  if (building.goldPerTurn > 0) effects.push(`+${building.goldPerTurn} Gold per Turn`)
  if (building.goldPerPopulation > 0) effects.push(`+${building.goldPerPopulation} Gold per Population per Turn`)
  if (building.researchPerTurn > 0) effects.push(`+${building.researchPerTurn} Research per Turn`)
  if (building.defense > 0) effects.push(`+${building.defense} Defense`)
  return effects.join(', ')
}

/** A defensive Building type's Damaged and Destroyed Buildings, with buttons to Repair and Rebuild them. */
function Damage({
  damage,
  nation,
  onRepair,
  onRebuild,
}: {
  damage: BuildingDamage
  nation: NationView
  onRepair: (building: string) => void
  onRebuild: (building: string) => void
}) {
  return (
    <>
      {damage.damaged > 0 && (
        <li className="row row--nested">
          <span className="tag tag--warn">Damaged</span>
          <span className="row__meta">
            1 Building, {damage.damaged} Defense lost · Repair {damage.repairGold} Gold,{' '}
            {plural(damage.repairTurns, 'Turn')}
          </span>
          <button
            aria-label={`Repair ${damage.building}`}
            onClick={() => onRepair(damage.building)}
            disabled={damage.repairGold > nation.gold}
          >
            Repair
          </button>
        </li>
      )}
      {damage.destroyed > 0 && (
        <li className="row row--nested">
          <span className="tag tag--bad">Destroyed</span>
          <span className="row__meta">
            {plural(damage.destroyed, 'Building')} · Rebuild {damage.rebuildGold} Gold,{' '}
            {plural(damage.rebuildTurns, 'Turn')}
          </span>
          <button
            aria-label={`Rebuild ${damage.building}`}
            onClick={() => onRebuild(damage.building)}
            disabled={damage.rebuildGold > nation.gold}
          >
            Rebuild
          </button>
        </li>
      )}
    </>
  )
}

/**
 * The Buildings the Nation can construct, then the ones it has, with a button to Sell each type it may
 * Sell one of, and the ones it is constructing, Repairing or Rebuilding, the same ones merged into one row.
 * The buttons to construct come first so they stay put as construction starts. Rows new in this Nation
 * view flash.
 */
export function BuildPanel({
  nation,
  rejection,
  onStart,
  onRepair,
  onRebuild,
  onSell,
}: {
  nation: NationView
  rejection: string | null
  onStart: (building: string) => void
  onRepair: (building: string) => void
  onRebuild: (building: string) => void
  onSell: (building: string) => void
}) {
  const owned = Object.entries(nation.buildings)
  // Each row in progress is known by the Turn it finishes on, so it stays the same row as its Turns left count down.
  const construction = mergeAlike(nation.construction, (c) => ['construction', c.building, nation.turn + c.turnsLeft])
  const repairs = mergeAlike(nation.repairs, (r) => ['repair', r.building, r.rebuild, nation.turn + r.turnsLeft])
  const isFresh = useFreshRows(nation.version, [
    ...owned.map(([name]) => name),
    ...construction.map(({ key }) => key),
    ...repairs.map(({ key }) => key),
  ])
  return (
    <Panel title="Build" icon="building" rejection={rejection}>
      <h3>Construct</h3>
      <ul className="list">
        {nation.availableBuildings.map((b) => (
          <li key={b.name} className="row row--wrap">
            <span className="row__name">{b.name}</span>
            <span className="row__meta">
              {b.gold} Gold, {plural(b.turns, 'Turn')} — {describeEffect(b)}
            </span>
            <button aria-label={`Build ${b.name}`} onClick={() => onStart(b.name)} disabled={b.gold > nation.gold}>
              Build
            </button>
          </li>
        ))}
      </ul>
      <h3>Your Buildings</h3>
      {owned.length === 0 && nation.construction.length === 0 && nation.repairs.length === 0 ? (
        <p className="muted">None yet.</p>
      ) : (
        <ul className="list">
          {owned.map(([name, count]) => {
            const damage = nation.damage.find((d) => d.building === name)
            const sellValue = nation.sellValues[name]
            return [
              <li key={name} className={rowClass(isFresh(name))}>
                <span className="row__name">{name}</span>
                <span className="row__meta">×{count}</span>
                {sellValue !== undefined && (
                  <button aria-label={`Sell ${name}`} onClick={() => onSell(name)}>
                    Sell for {sellValue} Gold
                  </button>
                )}
              </li>,
              damage && (
                <Damage
                  key={`${name}-damage`}
                  damage={damage}
                  nation={nation}
                  onRepair={onRepair}
                  onRebuild={onRebuild}
                />
              ),
            ]
          })}
          {construction.map(({ key, item: c, count }) => (
            <li key={key} className={rowClass(isFresh(key))}>
              <span className="row__name">
                {c.building} ×{count}
              </span>
              <span className="tag tag--muted">constructing</span>
              <span className="row__meta">{plural(c.turnsLeft, 'Turn')} left</span>
            </li>
          ))}
          {repairs.map(({ key, item: r, count }) => (
            <li key={key} className={rowClass(isFresh(key))}>
              <span className="row__name">
                {r.building} ×{count}
              </span>
              <span className="tag tag--muted">{r.rebuild ? 'being Rebuilt' : 'being Repaired'}</span>
              <span className="row__meta">{plural(r.turnsLeft, 'Turn')} left</span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

/** What a Unit type does, its upkeep and its counters, in one line. */
function describeUnit(unit: UnitType) {
  const counters = unit.counters.length > 0 ? `, counters ${unit.counters.join(', ')}` : ''
  const role = unit.scout
    ? "roams the wilds, where it may discover Warparties abroad and catch other Nations' travelling Spies"
    : unit.spy
      ? "sent on its own to report another Nation's hidden state"
      : `Strength ${unit.strength}`
  return `${role}, upkeep ${unit.upkeep} Gold per Turn${counters}`
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
    <li className="row row--wrap">
      <span className="row__name">{unit.name}</span>
      <span className="row__meta">
        {unit.gold} Gold and {unit.population} Population, {plural(unit.turns, 'Turn')} — {describeUnit(unit)}
      </span>
      <span className="button-row">
        <CountInput value={count} min={1} label={`How many ${unit.name} to train`} onChange={setCount} />
        <button aria-label={`Train ${unit.name}`} onClick={() => onTrain(unit.name, count)} disabled={!affordable}>
          Train
        </button>
      </span>
    </li>
  )
}

/**
 * The Unit types the Nation can train, then the Units in training, merged by Unit type and Turns left, so
 * the Train buttons stay put; rows new in this Nation view flash.
 */
export function TrainPanel({
  nation,
  rejection,
  onTrain,
}: {
  nation: NationView
  rejection: string | null
  onTrain: (unit: string, count: number) => void
}) {
  const training = mergeAlike(
    nation.training,
    (t) => [t.unit, t.readyOnTurn],
    (t) => t.count,
  )
  const isFresh = useFreshRows(
    nation.version,
    training.map(({ key }) => key),
  )
  return (
    <Panel title="Train" icon="sword" rejection={rejection}>
      <h3>Units</h3>
      <ul className="list">
        {nation.availableUnits.map((u) => (
          <TrainUnit key={u.name} unit={u} nation={nation} onTrain={onTrain} />
        ))}
      </ul>
      <h3>In training</h3>
      {nation.training.length === 0 ? (
        <p className="muted">No Units in training.</p>
      ) : (
        <ul className="list">
          {training.map(({ key, item: t, count }) => (
            <li key={key} className={rowClass(isFresh(key))}>
              <span className="row__name">
                {t.unit} ×{count}
              </span>
              <span className="row__meta">{plural(t.readyOnTurn - nation.turn, 'Turn')} left</span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

const standingTags: Record<TechTreeEntry['standing'], string> = {
  UNLOCKED: 'tag tag--good',
  AVAILABLE: 'tag tag--accent',
  LOCKED: 'tag tag--muted',
}

/**
 * The Tech Tree: every Technology, whether it is unlocked, available or locked, what it costs and what it
 * unlocks, with a button to unlock each available one.
 */
export function TechTreePanel({
  nation,
  rejection,
  onUnlock,
}: {
  nation: NationView
  rejection: string | null
  onUnlock: (technology: string) => void
}) {
  const unlocked = new Set(nation.techTree.filter((t) => t.standing === 'UNLOCKED').map((t) => t.name))
  return (
    <Panel title="Tech Tree" icon="lightbulb" rejection={rejection}>
      {nation.techTree.length === 0 ? (
        <p className="muted">There are no Technologies in this Game.</p>
      ) : (
        <ul className="list">
          {nation.techTree.map((t) => {
            const missing = t.prerequisites.filter((p) => !unlocked.has(p))
            return (
              <li key={t.name} className={t.standing === 'LOCKED' ? 'row row--wrap row--locked' : 'row row--wrap'}>
                <span className="row__name">{t.name}</span>
                <span className={standingTags[t.standing]}>{t.standing.toLowerCase()}</span>
                <span className="row__meta">
                  {t.research} Research
                  {t.unlocks.length > 0 && ` — unlocks ${t.unlocks.join(', ')}`}
                  {t.standing === 'LOCKED'
                    ? `; requires ${missing.join(', ')}`
                    : t.prerequisites.length > 0 && `; requires ${t.prerequisites.join(', ')}`}
                </span>
                {t.standing === 'AVAILABLE' && (
                  <button
                    aria-label={`Unlock ${t.name}`}
                    onClick={() => onUnlock(t.name)}
                    disabled={t.research > nation.research}
                  >
                    Unlock
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </Panel>
  )
}
