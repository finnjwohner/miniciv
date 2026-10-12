import { useState, type ReactNode } from 'react'
import { named } from '../api'
import type { Dilemma, Encounter, HaulDecision, MapNation, Named, NationView, Tribute } from '../api'
import { Panel } from '../ui/Panel'
import { CountSlider } from './controls'
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

/**
 * One decision in the strip: a titled card, tagged while it still awaits the Player and, once they
 * have answered, tagged with `answeredTag` instead, so its header keeps its height.
 */
function Decision({
  kind,
  open,
  answeredTag,
  children,
}: {
  kind: string
  open: boolean
  answeredTag?: string
  children: ReactNode
}) {
  return (
    <li className={open ? 'decision decision--open' : 'decision'}>
      <h3 className="decision__kind">
        {kind}
        {open ? (
          <span className="tag tag--accent">needs you</span>
        ) : (
          answeredTag && <span className="tag tag--muted">{answeredTag}</span>
        )}
      </h3>
      {children}
    </li>
  )
}

/** A Dilemma; the Player may change their choice until the Turn ends, when it, or else the worst, applies. */
function DilemmaDecision({
  dilemma,
  onAnswer,
}: {
  dilemma: Dilemma
  onAnswer: (dilemma: string, choice: string) => void
}) {
  return (
    <Decision kind="Dilemma" open={dilemma.chosen === null} answeredTag="chosen">
      <p>
        <strong>{dilemma.text}.</strong>
      </p>
      <p className="muted">
        {dilemma.chosen
          ? `You chose ${dilemma.chosen}; you can change it until the Turn ends.`
          : 'Choose before the Turn ends, or the worst choice applies.'}
      </p>
      <ul className="list" aria-label={`Choices for ${dilemma.name}`}>
        {dilemma.choices.map((choice) => (
          <li key={choice.name}>
            <button
              className={dilemma.chosen === choice.name ? 'decision__choice button--chosen' : 'decision__choice'}
              onClick={() => onAnswer(dilemma.name, choice.name)}
            >
              {choice.name}
              {choice.worst && <span className="tag tag--warn">if you don't choose</span>}
            </button>
          </li>
        ))}
      </ul>
    </Decision>
  )
}

/** Such as "12 Gold" or "5 Research". */
function describeHaul(decision: HaulDecision) {
  const resource = { GOLD: 'Gold', RESEARCH: 'Research', POPULATION: 'Population' }[decision.haul.resource]
  return `${decision.haul.amount} ${resource}`
}

/**
 * A Haul seized from a caught Spy, or Plunder taken from a Warparty wiped out in an Encounter, to keep or send
 * back to the Nation it was stolen from; the Player may change their choice until the Turn ends, and unanswered
 * it is kept.
 */
function HaulChoice({
  decision,
  map,
  onAnswer,
}: {
  decision: HaulDecision
  map: MapNation[]
  onAnswer: (haul: number, keep: boolean) => void
}) {
  const victim = <MapName name={decision.stolenFrom} map={map} />
  const chosen = decision.choice === 'KEEP' ? 'keep it' : 'send it back'
  return (
    <Decision kind="Haul" open={decision.choice === 'NONE'} answeredTag="chosen">
      <p>
        {decision.plunder ? (
          <>
            Your Warparty took {describeHaul(decision)} from the Warparty it wiped out, plundered from {victim}.
          </>
        ) : (
          <>
            The Spy you caught carried {describeHaul(decision)} stolen from {victim}.
          </>
        )}
      </p>
      <div className="button-row">
        <button
          className={decision.choice === 'KEEP' ? 'button--chosen' : undefined}
          onClick={() => onAnswer(decision.id, true)}
        >
          Keep it
        </button>
        <button
          className={decision.choice === 'RETURN' ? 'button--chosen' : undefined}
          onClick={() => onAnswer(decision.id, false)}
        >
          Send it back
        </button>
      </div>
      <p className="muted">
        {decision.choice === 'NONE'
          ? 'Unanswered as the Turn ends, you keep it.'
          : `You chose to ${chosen}; you can change it until the Turn ends.`}{' '}
        It arrives 3 Turns after this one, and no one can intercept it.
      </p>
    </Decision>
  )
}

/**
 * One of the Nation's Warparties met another Nation's, or a Barbarian Camp's, which has chosen already; either
 * side choosing to fight makes them fight. The Player may change their choice until the Turn ends.
 */
function EncounterDecision({
  encounter,
  nation,
  map,
  onAnswer,
}: {
  encounter: Encounter
  nation: string
  map: Named[]
  onAnswer: (warparty: number, fight: boolean) => void
}) {
  const [own, other] = sides(encounter, nation)
  const them = <MapName name={other.nation} map={map} />
  return (
    <Decision kind="Encounter" open={own.choice === 'NONE'} answeredTag="chosen">
      <p>
        Your Warparty of {listCounts(own.units)} met {them}'s Warparty of {listCounts(other.units)}.
      </p>
      <div className="button-row">
        <button
          className={own.choice === 'FIGHT' ? 'button--chosen' : undefined}
          onClick={() => onAnswer(own.warparty, true)}
        >
          Fight
        </button>
        <button
          className={own.choice === 'PASS' ? 'button--chosen' : undefined}
          onClick={() => onAnswer(own.warparty, false)}
        >
          Let it pass
        </button>
      </div>
      {own.choice !== 'NONE' && (
        <p>
          {own.choice === 'FIGHT' ? 'You chose to fight' : 'You chose not to fight'}; you can change it until the Turn
          ends.
        </p>
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

/** The Gold and Population, each up to what the Nation holds, to offer one arrived Warparty as Tribute. */
function TributeOffer({
  attacker,
  warparty,
  held,
  onOffer,
}: {
  attacker: string
  warparty: number
  held: Pick<NationView, 'gold' | 'population'>
  onOffer: (nation: string, warparty: number, gold: number, population: number) => void
}) {
  const [chosenGold, setGold] = useState(0)
  const [chosenPopulation, setPopulation] = useState(0)
  const gold = Math.min(chosenGold, held.gold)
  const population = Math.min(chosenPopulation, held.population)
  return (
    <div className="button-row">
      <CountSlider value={gold} min={0} max={held.gold} label={`Gold to offer ${attacker}`} onChange={setGold} />
      Gold
      <CountSlider
        value={population}
        min={0}
        max={held.population}
        label={`Population to offer ${attacker}`}
        onChange={setPopulation}
      />
      Population
      <button onClick={() => onOffer(attacker, warparty, gold, population)} disabled={gold + population <= 0}>
        Offer Tribute
      </button>
    </div>
  )
}

/** Whether the Nation has any Dilemma, Haul, Encounter or Tribute to show this Turn. */
function hasDecisions(nation: NationView) {
  return (
    nation.dilemmas.length > 0 ||
    nation.hauls.length > 0 ||
    nation.encounters.length > 0 ||
    nation.arrivals.length > 0 ||
    nation.tributes.some((t) => t.to === nation.nation || t.answer === 'ACCEPTED')
  )
}

/**
 * The highlighted strip of this Turn's decisions: Dilemmas, Hauls seized from caught Spies, Encounters, enemy
 * Warparties that arrived
 * with a form to offer Tribute, and Tribute offered to this Nation with buttons to accept or refuse it;
 * nothing if there are none this Turn.
 */
export function DecisionsPanel({
  nation,
  rejection,
  onAnswerDilemma,
  onAnswerHaul,
  onAnswerEncounter,
  onOfferTribute,
  onAnswerTribute,
}: {
  nation: NationView
  rejection: string | null
  onAnswerDilemma: (dilemma: string, choice: string) => void
  onAnswerHaul: (haul: number, keep: boolean) => void
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
    nation.hauls.filter((h) => h.choice === 'NONE').length +
    nation.encounters.filter((e) => sides(e, nation.nation)[0].choice === 'NONE').length +
    nation.arrivals.filter((a) => !offeredFor(a.nation, a.warparty)).length +
    received.filter((t) => t.answer === 'NONE').length

  return (
    <Panel
      title="Decisions"
      icon="warning"
      className="dash__decisions panel--highlight"
      actions={open > 0 ? <span className="tag tag--accent">{open} open</span> : undefined}
      rejection={rejection}
    >
      <ul className="decisions">
        {nation.dilemmas.map((d) => (
          <DilemmaDecision key={d.name} dilemma={d} onAnswer={onAnswerDilemma} />
        ))}
        {nation.hauls.map((h) => (
          <HaulChoice key={h.id} decision={h} map={nation.map} onAnswer={onAnswerHaul} />
        ))}
        {nation.encounters.map((e) => (
          <EncounterDecision
            key={sides(e, nation.nation)[0].warparty}
            encounter={e}
            nation={nation.nation}
            map={named(nation)}
            onAnswer={onAnswerEncounter}
          />
        ))}
        {nation.arrivals.map((a) => {
          const offered = offeredFor(a.nation, a.warparty)
          const attacker = <MapName name={a.nation} map={nation.map} />
          return (
            <Decision key={`${a.nation}-${a.warparty}`} kind="Warparty arrived" open={!offered} answeredTag="offered">
              <p>A Warparty from {attacker} has arrived; the Battle will be fought as this Turn ends.</p>
              {!offered ? (
                <TributeOffer attacker={a.nation} warparty={a.warparty} held={nation} onOffer={onOfferTribute} />
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
              <MapName name={t.to} map={nation.map} /> accepted your Tribute of {describeTribute(t)}, and its Warparty
              turned back home.
            </p>
          </Decision>
        ))}
        {received.map((t) => (
          <Decision
            key={`${t.from}-${t.warparty}`}
            kind="Tribute offered"
            open={t.answer === 'NONE'}
            answeredTag="answered"
          >
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
