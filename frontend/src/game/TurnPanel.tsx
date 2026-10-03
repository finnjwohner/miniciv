import { TURN_REPORT_KINDS } from '../api'
import type { GameView, NationView, PlayerView, TurnReport, TurnReportKind } from '../api'
import { Icon } from '../ui/Icon'
import { NationName } from '../ui/NationName'
import { Panel } from '../ui/Panel'

/** How a Player stands in the Game, as a tag beside their Nation. */
function PlayerStanding({ player, game }: { player: PlayerView; game: GameView }) {
  if (game.conquered.includes(player.nationName)) return <span className="tag tag--bad">knocked out</span>
  if (game.removed.includes(player.nationName)) return <span className="tag tag--muted">removed</span>
  if (player.nationName === game.winner) return <span className="tag tag--accent">winner</span>
  if (game.waitingFor.some((w) => w.nation === player.nationName))
    return <span className="tag tag--bad">disconnected</span>
  if (player.done)
    return (
      <span className="tag tag--good">
        <Icon name="check" size={12} />
        done
      </span>
    )
  return <span className="tag tag--muted">deciding</span>
}

/** Each Turn Report section's heading. */
const SECTION_HEADINGS: Record<TurnReportKind, string> = {
  DECISIONS_AND_ALERTS: 'Decisions & alerts',
  WARFARE: 'Warfare',
  COMPLETED: 'Completed',
  EVENTS: 'Events',
  SPIES_AND_SCOUTS: 'Spies & Scouts',
  RESOURCES: 'Resources',
}

/** The Turn Report's sections with something in them, each under its heading, in the fixed order. */
function ReportSections({ report }: { report: TurnReport | null }) {
  const sections = TURN_REPORT_KINDS.flatMap((kind) => {
    const lines = report?.sections.find((section) => section.kind === kind)?.lines ?? []
    return lines.length > 0 ? [{ kind, lines }] : []
  })
  if (sections.length === 0) return <p className="muted">Nothing has happened yet.</p>
  return sections.map(({ kind, lines }) => (
    <div key={kind} className="report__section">
      <h4 className="report__heading" id={`report-${kind}`}>
        {SECTION_HEADINGS[kind]}
      </h4>
      <ul className="report" aria-labelledby={`report-${kind}`}>
        {lines.map((line, i) => (
          <li key={i}>{line}</li>
        ))}
      </ul>
    </div>
  ))
}

/**
 * The heart of the Game screen: what happened as the last Turn resolved, and, while the Player is
 * playing, the Turn timer and the button to end their Turn, with which Players are done.
 */
export function TurnPanel({
  game,
  nation,
  players,
  playerId,
  playing,
  countdown,
  onDone,
}: {
  game: GameView
  nation: NationView | null
  players: PlayerView[]
  playerId: string
  playing: boolean
  countdown: string
  onDone: (turn: number) => void
}) {
  const me = players.find((p) => p.id === playerId)
  const paused = game.waitingFor.length > 0
  return (
    <Panel
      title={`Turn ${game.turn}`}
      icon="hourglass"
      className="dash__turn panel--prominent"
      actions={paused && !game.winner ? <span className="tag tag--bad">paused</span> : undefined}
    >
      <div className="turn">
        <section className="turn__report" aria-labelledby="turn-report">
          <h3 className="turn__heading" id="turn-report">
            <Icon name="book" size={12} />
            Turn Report
          </h3>
          <ReportSections report={nation?.turnReport ?? null} />
        </section>
        <div className="turn__side">
          {playing && (
            <>
              <div className={paused ? 'turn__timer turn__timer--paused' : 'turn__timer'} aria-label="Time left">
                {countdown}
              </div>
              <button className="button--primary turn__end" onClick={() => onDone(game.turn)} disabled={!me || me.done}>
                {me?.done ? 'Waiting for the others…' : 'End Turn'}
              </button>
            </>
          )}
          <ul className="roster roster--compact">
            {players.map((p) => (
              <li key={p.id} className={p.id === playerId ? 'roster__nation roster__nation--you' : 'roster__nation'}>
                <span className="roster__name">
                  <NationName name={p.nationName} colour={p.colour} emblem={p.emblem} />
                </span>
                {p.id === playerId && <span className="tag tag--accent">you</span>}
                <PlayerStanding player={p} game={game} />
              </li>
            ))}
          </ul>
        </div>
      </div>
    </Panel>
  )
}
