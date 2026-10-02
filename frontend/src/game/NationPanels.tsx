import { useState } from 'react'
import type { BuildingDamage, BuildingType, NationView, TechTreeEntry, UnitType } from '../api'
import { Icon, type IconName } from '../ui/Icon'
import { Panel } from '../ui/Panel'
import { CountInput, Rejection } from './controls'
import { plural } from './format'

function Stat({ icon, label, value }: { icon: IconName; label: string; value: number }) {
  return (
    <div className="stat">
      <Icon name={icon} />
      <span className="stat__label">{label}</span>
      <span className="stat__value">{value}</span>
    </div>
  )
}

/** The Nation's Gold, Population, Research and Defense, and the Gold its Units cost each Turn. */
export function ResourcesPanel({ nation }: { nation: NationView }) {
  return (
    <Panel title={nation.nation} icon="flag" className="dash__resources">
      <div className="stats">
        <Stat icon="coins" label="Gold" value={nation.gold} />
        <Stat icon="human" label="Population" value={nation.population} />
        <Stat icon="lightbulb" label="Research" value={nation.research} />
        <Stat icon="shield" label="Defense" value={nation.defense} />
      </div>
      <p className="muted">Upkeep, at home and abroad: {nation.upkeep} Gold per Turn</p>
    </Panel>
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
    <>
      {damage.damaged > 0 && (
        <li className="row row--nested">
          <span className="tag tag--warn">Damaged</span>
          <span className="row__meta">
            1 Building, {damage.damaged} Defense lost · Repair {damage.repairGold} Gold, {plural(damage.repairTurns, 'Turn')}
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
            {plural(damage.destroyed, 'Building')} · Rebuild {building.gold} Gold, {plural(building.turns, 'Turn')}
          </span>
          <button
            aria-label={`Rebuild ${damage.building}`}
            onClick={() => onRebuild(damage.building)}
            disabled={building.gold > nation.gold}
          >
            Rebuild
          </button>
        </li>
      )}
    </>
  )
}

/** The Buildings the Nation has and is constructing, Repairing or Rebuilding, and the ones it can construct. */
export function BuildPanel({
  nation,
  rejection,
  onStart,
  onRepair,
  onRebuild,
}: {
  nation: NationView
  rejection: string | null
  onStart: (building: string) => void
  onRepair: (building: string) => void
  onRebuild: (building: string) => void
}) {
  const owned = Object.entries(nation.buildings)
  return (
    <Panel title="Build" icon="building">
      <Rejection reason={rejection} />
      <h3>Your Buildings</h3>
      {owned.length === 0 && nation.construction.length === 0 && nation.repairs.length === 0 ? (
        <p className="muted">None yet.</p>
      ) : (
        <ul className="list">
          {owned.map(([name, count]) => {
            const damage = nation.damage.find((d) => d.building === name)
            const building = nation.availableBuildings.find((b) => b.name === name)
            return [
              <li key={name} className="row">
                <span className="row__name">{name}</span>
                <span className="row__meta">×{count}</span>
              </li>,
              damage && building && (
                <Damage
                  key={`${name}-damage`}
                  damage={damage}
                  building={building}
                  nation={nation}
                  onRepair={onRepair}
                  onRebuild={onRebuild}
                />
              ),
            ]
          })}
          {nation.construction.map((c, i) => (
            <li key={`construction-${i}`} className="row">
              <span className="row__name">{c.building}</span>
              <span className="tag tag--muted">constructing</span>
              <span className="row__meta">{plural(c.turnsLeft, 'Turn')} left</span>
            </li>
          ))}
          {nation.repairs.map((r, i) => (
            <li key={`repair-${i}`} className="row">
              <span className="row__name">{r.building}</span>
              <span className="tag tag--muted">{r.rebuild ? 'being Rebuilt' : 'being Repaired'}</span>
              <span className="row__meta">{plural(r.turnsLeft, 'Turn')} left</span>
            </li>
          ))}
        </ul>
      )}
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
    </Panel>
  )
}

/** What a Unit type does, its upkeep and its counters, in one line. */
function describeUnit(unit: UnitType) {
  const counters = unit.counters.length > 0 ? `, counters ${unit.counters.join(', ')}` : ''
  const role = unit.scout
    ? 'stays home and may discover Warparties abroad'
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

/** The Units in training and the Unit types the Nation can train. */
export function TrainPanel({
  nation,
  rejection,
  onTrain,
}: {
  nation: NationView
  rejection: string | null
  onTrain: (unit: string, count: number) => void
}) {
  return (
    <Panel title="Train" icon="sword">
      <Rejection reason={rejection} />
      <h3>In training</h3>
      {nation.training.length === 0 ? (
        <p className="muted">No Units in training.</p>
      ) : (
        <ul className="list">
          {nation.training.map((t, i) => (
            <li key={i} className="row">
              <span className="row__name">
                {t.unit} ×{t.count}
              </span>
              <span className="row__meta">ready on Turn {t.readyOnTurn}</span>
            </li>
          ))}
        </ul>
      )}
      <h3>Units</h3>
      <ul className="list">
        {nation.availableUnits.map((u) => (
          <TrainUnit key={u.name} unit={u} nation={nation} onTrain={onTrain} />
        ))}
      </ul>
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
    <Panel title="Tech Tree" icon="lightbulb">
      <Rejection reason={rejection} />
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
                  <button aria-label={`Unlock ${t.name}`} onClick={() => onUnlock(t.name)} disabled={t.research > nation.research}>
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
