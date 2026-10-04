import { useState } from 'react'
import { locatableNations, locatedNations, recallTurns, travelTurns } from '../api'
import type { Fact, MapNation, Mission, NationView, Spy, Task, TaskKind, Warparty } from '../api'
import { NationEmblem } from '../ui/NationName'
import { Panel } from '../ui/Panel'
import { TypeCounts, TypeName } from '../ui/TypeIcon'
import { CountSlider, TargetSelect } from './controls'
import { currentTarget, formatAmount, listCounts, mergeAlike, plural } from './format'
import { MapName } from './MapName'
import { rowClass, useFreshRows } from './turnStart'

function describeWarparty(warparty: Warparty, map: MapNation[]) {
  const target = <MapName name={warparty.target} map={map} />
  if (!warparty.homeward && warparty.turnsLeft === 0) {
    return <>at {target}, fighting the Battle as this Turn ends</>
  }
  if (!warparty.homeward) {
    return (
      <>
        travelling to {target}, {plural(warparty.turnsLeft, 'Turn')} left
      </>
    )
  }
  const { gold, research } = warparty.plunder
  const plunder = gold > 0 || research > 0 ? `, carrying ${gold} Gold and ${research} Research` : ''
  return (
    <>
      heading home from {target}
      {plunder}, {plural(warparty.turnsLeft, 'Turn')} left
    </>
  )
}

/**
 * A form to send Units from the Army to another Nation previewing the Warparty's Strength and travel
 * Turns, then the Army at home and its total Strength, and the Nation's Warparties abroad with their
 * Strength, destination and Turns left, each not yet heading home with a button to Recall it. A Unit
 * type new to the Army flashes.
 */
export function ArmyPanel({
  nation,
  rejection,
  onSend,
  onRecall,
}: {
  nation: NationView
  rejection: string | null
  onSend: (target: string, units: Record<string, number>) => void
  onRecall: (warparty: number) => void
}) {
  const targets = locatedNations(nation)
  const [chosenTarget, setTarget] = useState(targets[0] ?? '')
  const target = currentTarget(targets, chosenTarget)
  const [chosen, setChosen] = useState<Record<string, number>>({})
  const types = new Map(nation.availableUnits.map((u) => [u.name, u]))
  const army = Object.entries(nation.army)
  const strengthOf = (name: string, count: number) => (types.get(name)?.strength ?? 0) * count
  const strength = army.reduce((total, [name, count]) => total + strengthOf(name, count), 0)
  const isFresh = useFreshRows(
    nation.version,
    army.map(([name]) => name),
  )
  const sendable = army.filter(([name]) => !types.get(name)?.spy)
  const units: Record<string, number> = {}
  for (const [name, owned] of sendable) {
    const count = Math.min(chosen[name] ?? 0, owned)
    if (count > 0) units[name] = count
  }
  const sending = Object.entries(units).reduce((total, [name, count]) => total + strengthOf(name, count), 0)
  const send = () => {
    onSend(target, units)
    setChosen({})
  }

  return (
    <Panel
      title="Army & Warparties"
      icon="castle"
      actions={<span className="tag tag--accent">Strength {formatAmount(strength)}</span>}
      rejection={rejection}
    >
      <h3>Send a Warparty</h3>
      {sendable.length === 0 ? (
        <p className="muted">Your Army has no Units to send.</p>
      ) : targets.length === 0 ? (
        <NoneLocated />
      ) : (
        <div className="stack stack--tight">
          <ul className="list">
            {sendable.map(([name, owned]) => (
              <li key={name} className="row">
                <CountSlider
                  value={Math.min(chosen[name] ?? 0, owned)}
                  min={0}
                  max={owned}
                  label={`How many ${name} to send`}
                  onChange={(count) => setChosen({ ...chosen, [name]: count })}
                />
                <span className="row__name">
                  <TypeName name={name} kind="unit" size={24} />
                </span>
                <span className="row__meta">of {owned}</span>
              </li>
            ))}
          </ul>
          <div className="field__row field__row--end">
            <TargetSelect targets={targets} value={target} label="To" onChange={setTarget} />
            <button onClick={send} disabled={!target || Object.keys(units).length === 0}>
              Send Warparty
            </button>
          </div>
          <p>
            {Object.keys(units).length === 0
              ? 'Choose Units to send.'
              : `Strength ${formatAmount(sending)} · arrives in ${plural(travelTurns(nation.travelBrackets, sending), 'Turn')}`}
          </p>
        </div>
      )}
      <h3>At home</h3>
      {army.length === 0 ? (
        <p className="muted">No Units yet.</p>
      ) : (
        <ul className="list">
          {army.map(([name, count]) => (
            <li key={name} className={rowClass(isFresh(name))}>
              <span className="row__name">
                <TypeName name={name} kind="unit" size={24} /> ×{count}
              </span>
              <span className="row__meta">Strength {formatAmount(strengthOf(name, count))}</span>
            </li>
          ))}
          <li className="row row--total">
            <span className="row__name">Total Strength</span>
            <span className="row__meta">{formatAmount(strength)}</span>
          </li>
        </ul>
      )}
      <h3>Warparties abroad</h3>
      {nation.warparties.length === 0 ? (
        <p className="muted">No Warparties abroad.</p>
      ) : (
        <ul className="list">
          {nation.warparties.map((w) => (
            <li key={w.id} className="row row--wrap">
              <span className="row__name">
                <TypeCounts counts={w.units} kind="unit" size={24} />
              </span>
              <span className="tag">Strength {formatAmount(w.strength)}</span>
              <span className="row__meta">{describeWarparty(w, nation.map)}</span>
              {!w.homeward && (
                <button onClick={() => onRecall(w.id)}>
                  Recall (home in {plural(recallTurns(nation.travelBrackets, w), 'Turn')})
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

/** Whether a Spy has arrived at its target and stays there. */
function inPlace(spy: Spy) {
  return !spy.homeward && spy.turnsLeft === 0
}

/** Such as "Report Gold (2 Turns)" or, for a hostile Mission, "Poison Army (4 Turns, 50% chance)". */
function missionLabel(mission: Mission) {
  const turns = plural(mission.turns, 'Turn')
  if (mission.harm === null) return `${mission.name} (${turns})`
  return `${mission.name} (${turns}, ${Math.round(mission.chance * 100)}% chance)`
}

/**
 * Picks the Mission a Spy in place is set to, starting it from scratch, Intel Missions apart from hostile
 * ones; an Idle Spy shows a prompt to choose one.
 */
function MissionSelect({
  spy,
  missions,
  onChange,
}: {
  spy: Spy
  missions: Mission[]
  onChange: (mission: string) => void
}) {
  return (
    <select
      value={spy.mission ?? ''}
      aria-label={`Mission for Spy ${spy.id} in ${spy.target}`}
      onChange={(e) => onChange(e.target.value)}
    >
      {spy.mission === null && (
        <option value="" disabled>
          Choose a Mission
        </option>
      )}
      {[
        { label: 'Intel', group: missions.filter((m) => m.harm === null) },
        { label: 'Hostile', group: missions.filter((m) => m.harm !== null) },
      ]
        .filter(({ group }) => group.length > 0)
        .map(({ label, group }) => (
          <optgroup key={label} label={label}>
            {group.map((m) => (
              <option key={m.name} value={m.name}>
                {missionLabel(m)}
              </option>
            ))}
          </optgroup>
        ))}
    </select>
  )
}

function describeSpy(spy: Spy, map: MapNation[]) {
  const target = <MapName name={spy.target} map={map} />
  if (spy.homeward) {
    return (
      <>
        heading home from {target}, {plural(spy.turnsLeft, 'Turn')} left
      </>
    )
  }
  if (inPlace(spy)) {
    return spy.mission === null ? (
      <>in place at {target}, Idle</>
    ) : (
      <>
        in place at {target}, on {spy.mission}, {plural(spy.missionTurnsLeft, 'Turn')} left
      </>
    )
  }
  return (
    <>
      travelling to {target}, {plural(spy.turnsLeft, 'Turn')} left
    </>
  )
}

/** Says no Nation is Located yet, so there is nowhere to send Spies or Warparties. */
function NoneLocated() {
  return (
    <p className="muted">
      You have not Located any Nation yet. Set your Scouts to Locate one under Spies &amp; Scouts.
    </p>
  )
}

/** Such as "Locating Carthage". */
function describeTask(task: Task) {
  switch (task.kind) {
    case 'LOCATE':
      return `Locating ${task.target}`
  }
}

/**
 * The Nation's Idle Scouts, its Tasks each with a button to cancel it, and a form to set Idle Scouts to a
 * new Task: Locate offers only the Nations still in the Game neither Located nor being Located.
 */
function ScoutTasks({
  nation,
  onSetTask,
  onCancelTask,
}: {
  nation: NationView
  onSetTask: (kind: TaskKind, target: string, scouts: number) => void
  onCancelTask: (task: number) => void
}) {
  const targets = locatableNations(nation)
  const [chosenTarget, setTarget] = useState(targets[0] ?? '')
  const target = currentTarget(targets, chosenTarget)
  const [chosenCount, setCount] = useState(1)
  const count = Math.max(1, Math.min(chosenCount, nation.idleScouts))
  if (nation.scouts === 0) return <p className="muted">No Scouts yet. Train some to roam the wilds.</p>
  return (
    <>
      <ul className="list">
        <li className="row">
          <span className="row__name">Idle Scouts ×{nation.idleScouts}</span>
        </li>
        {nation.tasks.map((task) => (
          <li key={task.id} className="row">
            <span className="row__name">{describeTask(task)}</span>
            <span className="row__meta">{plural(task.scouts, 'Scout')}</span>
            <button onClick={() => onCancelTask(task.id)} aria-label={`Cancel ${describeTask(task)}`}>
              Cancel
            </button>
          </li>
        ))}
      </ul>
      {nation.idleScouts > 0 && targets.length > 0 && (
        <div className="stack stack--tight">
          <div className="field__row field__row--end">
            <TargetSelect targets={targets} value={target} label="Locate" onChange={setTarget} />
            <CountSlider
              value={count}
              min={1}
              max={nation.idleScouts}
              label="How many Scouts to set to the Task"
              onChange={setCount}
            />
            <button onClick={() => onSetTask('LOCATE', target, count)} disabled={!target}>
              Set Task
            </button>
          </div>
          <p className="muted">Each Scout gives a 15% chance each Turn of Locating the Nation.</p>
        </div>
      )}
      <p className="muted">
        Your Scouts give your Nation a chance each Turn to discover Warparties abroad and catch other Nations'
        Spies still travelling, better the more Scouts you have.
      </p>
    </>
  )
}

/**
 * A form to send a Spy from the Army to a Located Nation, what the Spies at home are doing, then the
 * Nation's Spies abroad: each in place on its own row with its Mission and a picker to set one, those
 * travelling with the same target and state merged into one row. Then its Scouts roaming the wilds and
 * their Tasks.
 */
export function SpiesPanel({
  nation,
  rejection,
  onSend,
  onSetMission,
  onSetTask,
  onCancelTask,
}: {
  nation: NationView
  rejection: string | null
  onSend: (target: string) => void
  onSetMission: (spy: number, mission: string) => void
  onSetTask: (kind: TaskKind, target: string, scouts: number) => void
  onCancelTask: (task: number) => void
}) {
  const targets = locatedNations(nation)
  const [chosenTarget, setTarget] = useState(targets[0] ?? '')
  const target = currentTarget(targets, chosenTarget)
  const spyTypes = new Set(nation.availableUnits.filter((u) => u.spy).map((u) => u.name))
  const atHome = Object.entries(nation.army)
    .filter(([name]) => spyTypes.has(name))
    .reduce((total, [, count]) => total + count, 0)
  return (
    <Panel
      title="Spies & Scouts"
      icon="eye"
      actions={<span className="muted">{atHome} on counter-intelligence</span>}
      rejection={rejection}
    >
      {atHome === 0 ? (
        <p className="muted">Train a Spy to send one abroad. Spies kept at home run counter-intelligence.</p>
      ) : targets.length === 0 ? (
        <NoneLocated />
      ) : (
        <div className="stack stack--tight">
          <div className="field__row field__row--end">
            <TargetSelect targets={targets} value={target} label="Send a Spy to" onChange={setTarget} />
            <button onClick={() => onSend(target)} disabled={!target}>
              Send Spy
            </button>
          </div>
          <p className="muted">Takes {plural(nation.spyTravelTurns, 'Turn')} to arrive, then stays there.</p>
        </div>
      )}
      {atHome > 0 && (
        <p className="muted">
          Your Spies at home are running counter-intelligence, giving your Nation a chance each Turn to catch enemy
          Spies in its land.
        </p>
      )}
      {nation.spies.length === 0 ? (
        <p className="muted">No Spies abroad.</p>
      ) : (
        <ul className="list">
          {mergeAlike(nation.spies, (s) => [s.unit, s.target, s.homeward, s.turnsLeft, inPlace(s) ? s.id : 0]).map(
            ({ key, item, count }) => (
              <li key={key} className="row row--wrap">
                <span className="row__name">
                  <TypeName name={item.unit} kind="unit" size={24} /> ×{count}
                </span>
                <span className="row__meta">{describeSpy(item, nation.map)}</span>
                {inPlace(item) && (
                  <MissionSelect
                    spy={item}
                    missions={nation.missions}
                    onChange={(mission) => onSetMission(item.id, mission)}
                  />
                )}
              </li>
            ),
          )}
        </ul>
      )}
      <h3>Scouts</h3>
      <ScoutTasks nation={nation} onSetTask={onSetTask} onCancelTask={onCancelTask} />
    </Panel>
  )
}

/** How many of each non-defensive Building the Nation has Damaged, counting those being Repaired. */
function damagedBuildings(nation: NationView) {
  const damaged: Record<string, number> = {}
  for (const d of nation.damage) {
    if (d.defenseLost === null) damaged[d.building] = d.damaged
  }
  for (const r of nation.repairs) {
    if (r.defense === 0) damaged[r.building] = (damaged[r.building] ?? 0) + 1
  }
  return damaged
}

/** Such as "120 · Turn 4", or "unknown" if nothing has been learned. */
function describeFact<T>(fact: Fact<T> | null, describe: (value: T) => string) {
  return fact ? `${describe(fact.value)} · Turn ${fact.turn}` : 'unknown'
}

function FactList({ entries }: { entries: [string, string][] }) {
  return (
    <dl className="facts">
      {entries.map(([label, value]) => (
        <div key={label} className={value === 'unknown' ? 'facts__entry facts__entry--unknown' : 'facts__entry'}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}

/** What this Player's Spies have reported of another Nation, each Fact with the Turn it was learned. */
function KnownFacts({ facts }: { facts: MapNation['facts'] }) {
  if (!Object.values(facts).some((fact) => fact !== null)) return <p className="muted">Nothing known yet.</p>
  const number = (value: number) => String(value)
  const counts = (counted: Record<string, number>) => listCounts(counted) || 'none'
  return (
    <FactList
      entries={[
        ['Gold', describeFact(facts.gold, number)],
        ['Population', describeFact(facts.population, number)],
        ['Research', describeFact(facts.research, number)],
        ['Defense', describeFact(facts.defense, number)],
        ['Army', describeFact(facts.army, counts)],
        ['Buildings', describeFact(facts.buildings, counts)],
        ['Damaged', describeFact(facts.damaged, counts)],
      ]}
    />
  )
}

/**
 * A read-only placeholder Map of every Nation in the Game, drawn from this Player's view alone: every
 * Nation in its colour with its emblem and Ruler, their own Nation in full, every other Nation with the
 * Facts known of it, and which have been knocked out or Removed.
 */
export function MapPanel({ nation }: { nation: NationView }) {
  return (
    <Panel title="Map" icon="map" className="dash__map">
      <ul className="nations">
        {nation.map.map((other) => {
          const you = other.name === nation.nation
          return (
            <li
              key={other.name}
              className={you ? 'nation-card nation-card--you' : 'nation-card'}
              style={{ borderLeftColor: other.colour }}
            >
              <h3 className="nation-card__name">
                <NationEmblem colour={other.colour} emblem={other.emblem} />
                <span className="nation-card__title">
                  {other.name}
                  <span className="nation-card__ruler">Ruled by {other.ruler}</span>
                </span>
                {you && <span className="tag tag--accent">you</span>}
                {other.standing === 'CONQUERED' && <span className="tag tag--bad">knocked out</span>}
                {other.standing === 'REMOVED' && <span className="tag tag--muted">removed</span>}
                {nation.located.includes(other.name) && <span className="tag">located</span>}
              </h3>
              {you ? (
                <FactList
                  entries={[
                    ['Gold', String(nation.gold)],
                    ['Population', String(nation.population)],
                    ['Research', String(nation.research)],
                    ['Defense', String(nation.defense)],
                    ['Army', listCounts(nation.army) || 'none'],
                    ['Buildings', listCounts(nation.buildings) || 'none'],
                    ['Damaged', listCounts(damagedBuildings(nation)) || 'none'],
                  ]}
                />
              ) : (
                <KnownFacts facts={other.facts} />
              )}
            </li>
          )
        })}
      </ul>
    </Panel>
  )
}
