import { useState } from 'react'
import {
  locatableCamps,
  locatableNations,
  locatedCamps,
  locatedNations,
  named,
  recallTurns,
  searchingTheWilds,
  travelTurns,
} from '../api'
import type { CampFacts, Fact, MapCamp, MapNation, Mission, Named, NationView, Spy, Task, TaskKind, Warparty } from '../api'
import { NationEmblem } from '../ui/NationName'
import { Panel } from '../ui/Panel'
import { TypeCounts, TypeName } from '../ui/TypeIcon'
import { CountSlider, TargetSelect } from './controls'
import { currentTarget, formatAmount, listCounts, mergeAlike, plural } from './format'
import { MapName } from './MapName'
import { rowClass, useFreshRows } from './turnStart'

function describeWarparty(warparty: Warparty, map: Named[]) {
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
 * A form to send Units from the Army to another Nation or a Barbarian Camp previewing the Warparty's Strength and travel
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
  const targets = [...locatedNations(nation), ...locatedCamps(nation)]
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
              <span className="row__meta">{describeWarparty(w, named(nation))}</span>
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

/** Whether a Mission harms or steals from the Nation its Spy is at, rather than reporting a Fact. */
function hostile(mission: Mission) {
  return mission.harm !== null || mission.steals !== null
}

/** Such as "Report Gold (2 Turns)" or, for a hostile Mission, "Poison Army (4 Turns, 50% chance)". */
function missionLabel(mission: Mission) {
  const turns = plural(mission.turns, 'Turn')
  if (!hostile(mission)) return `${mission.name} (${turns})`
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
        { label: 'Intel', group: missions.filter((m) => !hostile(m)) },
        { label: 'Hostile', group: missions.filter(hostile) },
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
    const haul = spy.haul && ` carrying ${spy.haul.amount} ${spy.haul.resource === 'GOLD' ? 'Gold' : 'Research'}`
    return (
      <>
        heading home from {target}
        {haul}, {plural(spy.turnsLeft, 'Turn')} left
      </>
    )
  }
  if (spy.escaping) {
    return (
      <>
        in place at {target}, Escaping after {spy.mission}; it takes its Haul as this Turn ends
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

/** Such as "Locating Carthage", "Searching the wilds" or "Watching for Warparties". */
function describeTask(task: Task) {
  switch (task.kind) {
    case 'LOCATE':
      return task.target === null ? 'Searching the wilds' : `Locating ${task.target}`
    case 'WATCH':
      return 'Watching for Warparties'
    case 'HUNT':
      return 'Hunting Travelling Spies'
  }
}

/** What each kind of Task is called when choosing one, and what it does. */
const TASK_KINDS: { kind: TaskKind; name: string; about: string }[] = [
  {
    kind: 'LOCATE',
    name: 'Locate',
    about:
      'Each Scout gives a 15% chance each Turn of Locating what it seeks. Searching the wilds finds any Barbarian Camp you have not Located.',
  },
  {
    kind: 'WATCH',
    name: 'Watch for Warparties',
    about: 'Takes 1 Scout. Each Watch Task gives a chance each Turn of discovering each Warparty abroad.',
  },
  {
    kind: 'HUNT',
    name: 'Hunt Travelling Spies',
    about: "Each Scout gives a chance each Turn of catching other Nations' Spies still travelling.",
  },
]

/** The Locate option that searches the wilds for any Barbarian Camp, rather than one Nation or Camp. */
const WILDS = ''

/**
 * The Nation's Idle Scouts, its Tasks each with a button to cancel it, and a form to set Idle Scouts to a
 * new Task: Locate offers the Nations still in the Game and the Barbarian Camps still standing that the Nation
 * has heard of, that are neither Located nor being Located, and the wilds while no Scouts are searching them;
 * Watch for Warparties takes exactly 1 Scout; Hunt Travelling Spies any number.
 */
function ScoutTasks({
  nation,
  onSetTask,
  onCancelTask,
}: {
  nation: NationView
  onSetTask: (kind: TaskKind, target: string | null, scouts: number) => void
  onCancelTask: (task: number) => void
}) {
  const targets = [...locatableNations(nation), ...locatableCamps(nation), ...(searchingTheWilds(nation) ? [] : [WILDS])]
  const kinds = TASK_KINDS.filter((k) => k.kind !== 'LOCATE' || targets.length > 0)
  const [chosenKind, setKind] = useState<TaskKind>(kinds[0].kind)
  const kind = kinds.find((k) => k.kind === chosenKind) ?? kinds[0]
  const [chosenTarget, setTarget] = useState(targets[0] ?? WILDS)
  const target = targets.includes(chosenTarget) ? chosenTarget : (targets[0] ?? WILDS)
  const [chosenCount, setCount] = useState(1)
  const most = kind.kind === 'WATCH' ? 1 : nation.idleScouts
  const count = Math.max(1, Math.min(chosenCount, most))
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
      {nation.idleScouts > 0 && (
        <div className="stack stack--tight">
          <div className="field__row field__row--end">
            <label className="field">
              <span className="field__label">Task</span>
              <select value={kind.kind} onChange={(e) => setKind(e.target.value as TaskKind)}>
                {kinds.map((k) => (
                  <option key={k.kind} value={k.kind}>
                    {k.name}
                  </option>
                ))}
              </select>
            </label>
            {kind.kind === 'LOCATE' && (
              <label className="field">
                <span className="field__label">Locate</span>
                <select value={target} onChange={(e) => setTarget(e.target.value)}>
                  {targets.map((n) => (
                    <option key={n} value={n}>
                      {n === WILDS ? 'The wilds' : n}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {most > 1 && (
              <CountSlider
                value={count}
                min={1}
                max={most}
                label="How many Scouts to set to the Task"
                onChange={setCount}
              />
            )}
            <button
              onClick={() => onSetTask(kind.kind, kind.kind === 'LOCATE' && target !== WILDS ? target : null, count)}
            >
              Set Task
            </button>
          </div>
          <p className="muted">{kind.about}</p>
        </div>
      )}
      <p className="muted">Idle Scouts do nothing; set them to Tasks to find, watch and hunt.</p>
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
  onSetTask: (kind: TaskKind, target: string | null, scouts: number) => void
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
          {mergeAlike(nation.spies, (s) => [
            s.unit,
            s.target,
            s.homeward,
            s.turnsLeft,
            inPlace(s) ? s.id : 0,
            s.haul?.resource ?? '',
            s.haul?.amount ?? 0,
          ]).map(
            ({ key, item, count }) => (
              <li key={key} className="row row--wrap">
                <span className="row__name">
                  <TypeName name={item.unit} kind="unit" size={24} /> ×{count}
                </span>
                <span className="row__meta">{describeSpy(item, nation.map)}</span>
                {inPlace(item) && !item.escaping && (
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

/** What Locating a Barbarian Camp showed, each with the Turn it was Located, or that it is not Located yet. */
function CampFactList({ located }: { located: CampFacts | null }) {
  if (located === null) return <p className="muted">Not Located yet.</p>
  const learned = (value: string) => `${value} · Turn ${located.turn}`
  return (
    <FactList
      entries={[
        ['Defense', learned(String(located.defense))],
        ['Army', learned(listCounts(located.army) || 'none')],
        ['Buildings', learned(listCounts(located.buildings) || 'none')],
        ['Targets', learned(located.targets.join(', ') || 'none')],
      ]}
    />
  )
}

/** The Barbarian Camps the Player has heard of, each in its colour with its emblem and Ruler. */
function CampCards({ camps }: { camps: MapCamp[] }) {
  return (
    <ul className="nations" aria-label="Barbarian Camps">
      {camps.map((camp) => (
        <li key={camp.name} className="nation-card" style={{ borderLeftColor: camp.colour }}>
          <h4 className="nation-card__name">
            <NationEmblem colour={camp.colour} emblem={camp.emblem} />
            <span className="nation-card__title">
              {camp.name}
              <span className="nation-card__ruler">Ruled by {camp.ruler}</span>
            </span>
            {camp.located && <span className="tag">located</span>}
          </h4>
          <CampFactList located={camp.located} />
        </li>
      ))}
    </ul>
  )
}

/**
 * A read-only placeholder Map of every Nation in the Game, drawn from this Player's view alone: every
 * Nation in its colour with its emblem and Ruler, their own Nation in full, every other Nation with the
 * Facts known of it, and which have been knocked out or Removed. Then the Barbarian Camps the Player has heard
 * of, with what Locating each showed.
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
      {nation.camps.length > 0 && (
        <>
          <h3>Barbarian Camps</h3>
          <CampCards camps={nation.camps} />
        </>
      )}
    </Panel>
  )
}
