import { useState, type ReactNode } from 'react'
import type { Dilemma, Encounter, MapNation, NationView, Tribute } from '../api'
import { Panel } from '../ui/Panel'
import { CountInput, Rejection } from './controls'
import { listCounts } from './format'
import { MapName } from './MapName'

/** Such as "20 Gold and 3 Population". */
function describeTribute(tribute: Tribute) {
  if (tribute.population === 0) return `${tribute.gold} Gold`
  if (tribute.gold === 0) return `${tribute.population} Population`
  return `${tribute.gold} Gold and ${tribute.population} Population`
}

/** The side of an Encounter that is this Nation's Warparty, then the other Nation's. */
function sides(encounter: Encounter, nation: string) {
  return encounter.first.nation === nation ? [encounter.first, encounter.second] : [encounter.second, encounter.first]
}

/** One decision in the strip: a titled card, marked while it still awaits the Player. */
function Decision({ kind, open, children }: { kind: string; open: boolean; children: ReactNode }) {
  return (
    <li className={open ? 'decision decision--open' : 'decision'}>
      <h3 className="decision__kind">
        {kind}
        {open && <span className="tag tag--accent">needs you</span>}
      </h3>
      {children}
    </li>
  )
}

/** A Dilemma; the choice made, or else the worst, applies as the Turn ends. */
function DilemmaDecision({
  dilemma,
  onAnswer,
}: {
  dilemma: Dilemma
  onAnswer: (dilemma: string, choice: string) => void
}) {
  return (
    <Decision kind="Dilemma" open={dilemma.chosen === null}>
      <p>
        <strong>{dilemma.text}.</strong>
      </p>
      <p className="muted">
        {dilemma.chosen
          ? `You chose ${dilemma.chosen}; it applies as the Turn ends.`
          : 'Choose before the Turn ends, or the worst choice applies.'}
      </p>
      <ul className="list">
        {dilemma.choices.map((choice) => (
          <li key={choice.name} className="row">
            <button
              className={dilemma.chosen === choice.name ? 'button--chosen' : undefined}
              onClick={() => onAnswer(dilemma.name, choice.name)}
              disabled={dilemma.chosen !== null}
            >
              {choice.name}
            </button>
            {choice.worst && <span className="row__meta">applies if you do not choose</span>}
          </li>
        ))}
      </ul>
    </Decision>
  )
}

/** One of the Nation's Warparties met another Nation's; either Player choosing to fight makes them fight. */
function EncounterDecision({
  encounter,
  nation,
  map,
  onAnswer,
}: {
  encounter: Encounter
  nation: string
  map: MapNation[]
  onAnswer: (warparty: number, fight: boolean) => void
}) {
  const [own, other] = sides(encounter, nation)
  const them = <MapName name={other.nation} map={map} />
  return (
    <Decision kind="Encounter" open={own.choice === 'NONE'}>
      <p>
        Your Warparty of {listCounts(own.units)} met {them}'s Warparty of {listCounts(other.units)}.
      </p>
      {own.choice === 'NONE' ? (
        <div className="button-row">
          <button onClick={() => onAnswer(own.warparty, true)}>Fight</button>
          <button onClick={() => onAnswer(own.warparty, false)}>Let it pass</button>
        </div>
      ) : (
        <p>{own.choice === 'FIGHT' ? 'You chose to fight.' : 'You chose not to fight.'}</p>
      )}
      <p className="muted">
        {them}{' '}
        {other.choice === 'FIGHT'
          ? 'chose to fight, so they will fight as this Turn ends.'
          : other.choice === 'PASS'
            ? 'chose not to fight.'
            : 'has not chosen yet.'}
      </p>
    </Decision>
  )
}

/** The Gold and Population to offer one arrived Warparty as Tribute. */
function TributeOffer({
  nation,
  warparty,
  onOffer,
}: {
  nation: string
  warparty: number
  onOffer: (nation: string, warparty: number, gold: number, population: number) => void
}) {
  const [gold, setGold] = useState(0)
  const [population, setPopulation] = useState(0)
  return (
    <div className="button-row">
      <CountInput value={gold} min={0} label={`Gold to offer ${nation}`} onChange={setGold} />
      Gold
      <CountInput value={population} min={0} label={`Population to offer ${nation}`} onChange={setPopulation} />
      Population
      <button onClick={() => onOffer(nation, warparty, gold, population)} disabled={gold + population <= 0}>
        Offer Tribute
      </button>
    </div>
  )
}

/** Whether the Nation has any Dilemma, Encounter or Tribute to show this Turn. */
function hasDecisions(nation: NationView) {
  return (
    nation.dilemmas.length > 0 ||
    nation.encounters.length > 0 ||
    nation.arrivals.length > 0 ||
    nation.tributes.some((t) => t.to === nation.nation || t.answer === 'ACCEPTED')
  )
}

/**
 * The highlighted strip of this Turn's decisions: Dilemmas, Encounters, enemy Warparties that arrived
 * with a form to offer Tribute, and Tribute offered to this Nation with buttons to accept or refuse it;
 * nothing if there are none this Turn.
 */
export function DecisionsPanel({
  nation,
  rejection,
  onAnswerDilemma,
  onAnswerEncounter,
  onOfferTribute,
  onAnswerTribute,
}: {
  nation: NationView
  rejection: string | null
  onAnswerDilemma: (dilemma: string, choice: string) => void
  onAnswerEncounter: (warparty: number, fight: boolean) => void
  onOfferTribute: (nation: string, warparty: number, gold: number, population: number) => void
  onAnswerTribute: (warparty: number, accept: boolean) => void
}) {
  if (!hasDecisions(nation)) return null
  const received = nation.tributes.filter((t) => t.to === nation.nation)
  const offeredFor = (attacker: string, warparty: number) =>
    nation.tributes.find((t) => t.from === nation.nation && t.to === attacker && t.warparty === warparty)
  const accepted = nation.tributes.filter((t) => t.from === nation.nation && t.answer === 'ACCEPTED')
  const open =
    nation.dilemmas.filter((d) => d.chosen === null).length +
    nation.encounters.filter((e) => sides(e, nation.nation)[0].choice === 'NONE').length +
    nation.arrivals.filter((a) => !offeredFor(a.nation, a.warparty)).length +
    received.filter((t) => t.answer === 'NONE').length

  return (
    <Panel
      title="Decisions"
      icon="warning"
      className="dash__decisions panel--highlight"
      actions={open > 0 ? <span className="tag tag--accent">{open} open</span> : undefined}
    >
      <Rejection reason={rejection} />
      <ul className="decisions">
        {nation.dilemmas.map((d) => (
          <DilemmaDecision key={d.name} dilemma={d} onAnswer={onAnswerDilemma} />
        ))}
        {nation.encounters.map((e) => (
          <EncounterDecision
            key={sides(e, nation.nation)[0].warparty}
            encounter={e}
            nation={nation.nation}
            map={nation.map}
            onAnswer={onAnswerEncounter}
          />
        ))}
        {nation.arrivals.map((a) => {
          const offered = offeredFor(a.nation, a.warparty)
          const attacker = <MapName name={a.nation} map={nation.map} />
          return (
            <Decision key={`${a.nation}-${a.warparty}`} kind="Warparty arrived" open={!offered}>
              <p>A Warparty from {attacker} has arrived; the Battle will be fought as this Turn ends.</p>
              {!offered ? (
                <TributeOffer nation={a.nation} warparty={a.warparty} onOffer={onOfferTribute} />
              ) : offered.answer === 'REFUSED' ? (
                <p>
                  {attacker} refused your Tribute of {describeTribute(offered)}.
                </p>
              ) : (
                <p className="muted">
                  You offered {attacker} {describeTribute(offered)}; waiting for their answer.
                </p>
              )}
            </Decision>
          )
        })}
        {accepted.map((t) => (
          <Decision key={`${t.to}-${t.warparty}`} kind="Tribute" open={false}>
            <p>
              <MapName name={t.to} map={nation.map} /> accepted your Tribute of {describeTribute(t)}, and its Warparty turned back home.
            </p>
          </Decision>
        ))}
        {received.map((t) => (
          <Decision key={`${t.from}-${t.warparty}`} kind="Tribute offered" open={t.answer === 'NONE'}>
            <p>
              <MapName name={t.from} map={nation.map} /> offers you {describeTribute(t)} to Recall your Warparty.
            </p>
            {t.answer === 'NONE' ? (
              <>
                <div className="button-row">
                  <button onClick={() => onAnswerTribute(t.warparty, true)}>Accept</button>
                  <button onClick={() => onAnswerTribute(t.warparty, false)}>Refuse</button>
                </div>
                <p className="muted">Unanswered as the Turn ends, the Battle goes ahead.</p>
              </>
            ) : (
              <p>{t.answer === 'ACCEPTED' ? 'You accepted it.' : 'You refused it.'}</p>
            )}
          </Decision>
        ))}
      </ul>
    </Panel>
  )
}
