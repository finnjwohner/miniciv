import { useState } from 'react'
import { otherNations, recallTurns, travelTurns } from '../api'
import type { Fact, MapNation, NationView, Spy, TravelBracket, Warparty } from '../api'
import { NationEmblem } from '../ui/NationName'
import { Panel } from '../ui/Panel'
import { CountInput, Rejection, TargetSelect } from './controls'
import { currentTarget, formatAmount, listCounts, plural } from './format'
import { MapName } from './MapName'

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

/** The travel brackets, such as "0–9: 3 · 10–24: 4 · 25+: 5". */
function describeBrackets(brackets: TravelBracket[]) {
  return brackets
    .map((bracket, i) => {
      const next = brackets[i + 1]
      const range = next ? `${bracket.strength}–${next.strength - 1}` : `${bracket.strength}+`
      return `${range}: ${bracket.turns}`
    })
    .join(' · ')
}

/**
 * The Army at home and its total Strength, the Nation's Warparties abroad with their Strength,
 * destination and Turns left, each not yet heading home with a button to Recall it, and a form to send
 * Units from the Army to another Nation previewing the Warparty's Strength and travel Turns.
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
  const targets = otherNations(nation)
  const [chosenTarget, setTarget] = useState(targets[0] ?? '')
  const target = currentTarget(targets, chosenTarget)
  const [chosen, setChosen] = useState<Record<string, number>>({})
  const types = new Map(nation.availableUnits.map((u) => [u.name, u]))
  const army = Object.entries(nation.army)
  const strengthOf = (name: string, count: number) => (types.get(name)?.strength ?? 0) * count
  const strength = army.reduce((total, [name, count]) => total + strengthOf(name, count), 0)
  const sendable = army.filter(([name]) => !types.get(name)?.scout && !types.get(name)?.spy)
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
    >
      <Rejection reason={rejection} />
      <h3>At home</h3>
      {army.length === 0 ? (
        <p className="muted">No Units yet.</p>
      ) : (
        <ul className="list">
          {army.map(([name, count]) => (
            <li key={name} className="row">
              <span className="row__name">
                {name} ×{count}
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
              <span className="row__name">{listCounts(w.units)}</span>
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
      <h3>Send a Warparty</h3>
      {sendable.length === 0 ? (
        <p className="muted">Your Army has no Units to send.</p>
      ) : targets.length === 0 ? (
        <p className="muted">There is no other Nation to send them to.</p>
      ) : (
        <div className="stack stack--tight">
          <ul className="list">
            {sendable.map(([name, owned]) => (
              <li key={name} className="row">
                <CountInput
                  value={chosen[name] ?? 0}
                  min={0}
                  max={owned}
                  label={`How many ${name} to send`}
                  onChange={(count) => setChosen({ ...chosen, [name]: count })}
                />
                <span className="row__name">{name}</span>
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
          <p className="muted">
            Turns to arrive by Strength: {describeBrackets(nation.travelBrackets)}. The trip home takes the
            bracket for the Strength that survives, longer when carrying a large haul.
          </p>
        </div>
      )}
    </Panel>
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
  if (spy.turnsLeft === 0) return <>in place at {target}, reporting now and then</>
  return (
    <>
      travelling to {target}, {plural(spy.turnsLeft, 'Turn')} left
    </>
  )
}

/** The Nation's Spies abroad, and a form to send a Spy from the Army to another Nation. */
export function SpiesPanel({
  nation,
  rejection,
  onSend,
}: {
  nation: NationView
  rejection: string | null
  onSend: (target: string) => void
}) {
  const targets = otherNations(nation)
  const [chosenTarget, setTarget] = useState(targets[0] ?? '')
  const target = currentTarget(targets, chosenTarget)
  const spyTypes = new Set(nation.availableUnits.filter((u) => u.spy).map((u) => u.name))
  const atHome = Object.entries(nation.army)
    .filter(([name]) => spyTypes.has(name))
    .reduce((total, [, count]) => total + count, 0)
  return (
    <Panel title="Spies" icon="eye" actions={<span className="muted">{atHome} at home</span>}>
      <Rejection reason={rejection} />
      {nation.spies.length === 0 ? (
        <p className="muted">No Spies abroad.</p>
      ) : (
        <ul className="list">
          {nation.spies.map((s) => (
            <li key={s.id} className="row row--wrap">
              <span className="row__name">{s.unit}</span>
              <span className="row__meta">{describeSpy(s, nation.map)}</span>
            </li>
          ))}
        </ul>
      )}
      {atHome === 0 ? (
        <p className="muted">Train a Spy to send one abroad. Spies kept at home can catch enemy Spies.</p>
      ) : targets.length === 0 ? (
        <p className="muted">There is no other Nation to send a Spy to.</p>
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
    </Panel>
  )
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
