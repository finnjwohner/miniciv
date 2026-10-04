import { Fragment, useEffect, useEffectEvent } from 'react'
import type { GameView, NetChange, PlayerView, TurnReport, TurnReportKind } from '../api'
import { useCue, useSettings } from '../settings'
import { Icon } from '../ui/Icon'
import { NationName } from '../ui/NationName'
import { Panel } from '../ui/Panel'
import { formatChange, formatCountdown } from './format'

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

/** Each resource as the net-change line names it, in the order it names them. */
const RESOURCE_NAMES: [keyof NetChange, string][] = [
  ['gold', 'Gold'],
  ['population', 'Population'],
  ['research', 'Research'],
]

/**
 * How the Nation's resources moved as the last Turn resolved, such as "+14 Gold · −2 Research",
 * gains in green and losses in red, leaving out those that did not change; nothing if none did.
 */
function NetChangeLine({ netChange }: { netChange: NetChange }) {
  const changed = RESOURCE_NAMES.filter(([resource]) => netChange[resource] !== 0)
  if (changed.length === 0) return null
  return (
    <p className="report__net">
      {changed.map(([resource, name], i) => {
        const amount = netChange[resource]
        return (
          <Fragment key={resource}>
            {i > 0 && ' · '}
            <span className={amount > 0 ? 'report__gain' : 'report__loss'}>{`${formatChange(amount)} ${name}`}</span>
          </Fragment>
        )
      })}
    </p>
  )
}

/** The Turn Report's sections with something in them, each under its heading, in the order sent. */
function ReportSections({ report }: { report: TurnReport | null }) {
  const sections = report?.sections.filter((section) => section.lines.length > 0) ?? []
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
 * What happened as the last Turn resolved: how the resources moved, then by section, filling the
 * space left and scrolling within it.
 */
export function TurnReportPanel({ report }: { report: TurnReport | null }) {
  return (
    <Panel title="Turn Report" icon="book" className="dash__report">
      {report && <NetChangeLine netChange={report.netChange} />}
      <ReportSections report={report} />
    </Panel>
  )
}

/** How many of the Turn's last seconds count as time running out. */
const RUNNING_OUT_SECONDS = 10

/**
 * Whether time is running out on a Player still deciding, counted down from `millisLeft`: the last
 * seconds of a Turn not Paused. The tick cue plays as each of those seconds starts.
 */
function useTimeRunningOut(millisLeft: number, deciding: boolean, paused: boolean) {
  const play = useCue()
  const secondsLeft = Math.ceil(Math.max(0, millisLeft) / 1000)
  const runningOut = deciding && !paused && secondsLeft > 0 && secondsLeft <= RUNNING_OUT_SECONDS
  const tick = useEffectEvent(() => play('tick'))
  useEffect(() => {
    if (runningOut) tick()
  }, [runningOut, secondsLeft])
  return runningOut
}

/**
 * The Turn timer, counting down `millisLeft`, and the button to end the Turn, while the Player is
 * playing, and how each Player stands: who is done, deciding, disconnected or out of the Game. As time
 * runs out on a Player still deciding, End Turn pulses and the tick cue plays each second.
 */
export function TurnPanel({
  game,
  players,
  playerId,
  playing,
  millisLeft,
  onDone,
}: {
  game: GameView
  players: PlayerView[]
  playerId: string
  playing: boolean
  millisLeft: number
  onDone: (turn: number) => void
}) {
  const me = players.find((p) => p.id === playerId)
  const paused = game.waitingFor.length > 0
  const deciding = playing && me !== undefined && !me.done
  const runningOut = useTimeRunningOut(millisLeft, deciding, paused)
  const { settings } = useSettings()
  const pulsing = runningOut && !settings.reducedMotion
  return (
    <Panel
      title={`Turn ${game.turn}`}
      icon="hourglass"
      className="dash__turn panel--prominent"
      actions={paused && !game.winner ? <span className="tag tag--bad">paused</span> : undefined}
    >
      <div className="turn">
        {playing && (
          <div className="turn__controls">
            <div className={paused ? 'turn__timer turn__timer--paused' : 'turn__timer'} aria-label="Time left">
              {formatCountdown(millisLeft)}
            </div>
            <button
              className={`button--primary turn__end${pulsing ? ' turn__end--pulse' : ''}`}
              data-cue="endTurn"
              onClick={() => onDone(game.turn)}
              disabled={!me || me.done}
            >
              {me?.done ? 'Waiting for the others…' : 'End Turn'}
            </button>
          </div>
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
    </Panel>
  )
}
