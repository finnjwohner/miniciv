import type { GameView, NationView, PlayerView } from '../api'
import { Icon } from '../ui/Icon'
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
        <div className="turn__report">
          <h3 className="turn__heading">
            <Icon name="book" size={12} />
            Turn Report
          </h3>
          {nation?.turnReport && nation.turnReport.lines.length > 0 ? (
            <ul className="report">
              {nation.turnReport.lines.map((line, i) => (
                <li key={i}>{line}</li>
              ))}
            </ul>
          ) : (
            <p className="muted">Nothing has happened yet.</p>
          )}
        </div>
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
                <span className="roster__name">{p.nationName}</span>
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
