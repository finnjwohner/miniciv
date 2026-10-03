import { useEffect, useState, type ReactNode } from 'react'
import type { NationView } from '../api'
import { Icon, type IconName } from '../ui/Icon'
import type { ReceivedRoom } from '../useRoom'
import { ArmyPanel, MapPanel, SpiesPanel } from './AbroadPanels'
import { DecisionsPanel } from './DecisionsPanel'
import { formatCountdown } from './format'
import { BuildPanel, ResourcesPanel, TechTreePanel, TrainPanel } from './NationPanels'
import { TurnPanel } from './TurnPanel'

/** The time now, updated a few times a second. */
function useNow() {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(timer)
  }, [])
  return now
}

/** A full-width notice above the dashboard: the Game won, the Player out of it, or the Game paused. */
function Banner({ tone, icon, children }: { tone: 'accent' | 'bad' | 'muted'; icon: IconName; children: ReactNode }) {
  return (
    <div role="status" className={`banner banner--${tone}`}>
      <Icon name={icon} />
      <div className="banner__body">{children}</div>
    </div>
  )
}

/** The panels an action can be taken in, so a rejection is shown where its action was. */
type Area = 'decisions' | 'build' | 'train' | 'tech' | 'army' | 'spies'

/**
 * One Turn of the Game as this Player sees it, as a dashboard: the Turn Report and End Turn button
 * first, this Turn's decisions under them, and panels for everything else the Player can do.
 */
function GameScreen({
  room,
  nation,
  playerId,
  rejection,
  onStartBuilding,
  onRepairBuilding,
  onRebuildBuilding,
  onSellBuilding,
  onTrainUnits,
  onSendWarparty,
  onRecallWarparty,
  onSendSpy,
  onAnswerDilemma,
  onUnlockTechnology,
  onOfferTribute,
  onAnswerTribute,
  onAnswerEncounter,
  onDone,
}: {
  room: ReceivedRoom
  nation: NationView | null
  playerId: string
  rejection: string | null
  onStartBuilding: (building: string) => void
  onRepairBuilding: (building: string) => void
  onRebuildBuilding: (building: string) => void
  onSellBuilding: (building: string) => void
  onTrainUnits: (unit: string, count: number) => void
  onSendWarparty: (target: string, units: Record<string, number>) => void
  onRecallWarparty: (warparty: number) => void
  onSendSpy: (target: string) => void
  onAnswerDilemma: (dilemma: string, choice: string) => void
  onUnlockTechnology: (technology: string) => void
  onOfferTribute: (nation: string, warparty: number, gold: number, population: number) => void
  onAnswerTribute: (warparty: number, accept: boolean) => void
  onAnswerEncounter: (warparty: number, fight: boolean) => void
  onDone: (turn: number) => void
}) {
  const now = useNow()
  const [actedIn, setActedIn] = useState<Area | null>(null)
  const game = room.game!
  const me = room.players.find((p) => p.id === playerId)
  const turnEndsAt = room.receivedAt + game.millisLeft
  const knockedOut = me !== undefined && game.conquered.includes(me.nationName)
  const removed = me !== undefined && game.removed.includes(me.nationName)
  const playing = !game.winner && !knockedOut && !removed
  const paused = game.waitingFor.length > 0
  const othersRemoved = game.removed.filter((n) => n !== me?.nationName)

  /** Remembers which panel an action was taken in before taking it. */
  const from =
    <Args extends unknown[]>(area: Area, act: (...args: Args) => void) =>
    (...args: Args) => {
      setActedIn(area)
      act(...args)
    }
  const rejectionIn = (area: Area) => (actedIn === area ? rejection : null)

  return (
    <div className="game">
      {game.winner && (
        <Banner tone="accent" icon="trophy">
          <strong>{game.winner === me?.nationName ? 'You have won the Game!' : `${game.winner} has won the Game.`}</strong>
        </Banner>
      )}
      {knockedOut && (
        <Banner tone="bad" icon="skull">
          <strong>
            Your Nation was conquered{nation?.conqueredBy && ` by ${nation.conqueredBy}`}. You are knocked out of the
            Game.
          </strong>
        </Banner>
      )}
      {removed && (
        <Banner tone="bad" icon="skull">
          <strong>Your Nation was removed from the Game, as you were disconnected for too long.</strong>
        </Banner>
      )}
      {!game.winner && paused && (
        <Banner tone="bad" icon="pause">
          <strong>The Game is paused.</strong>
          <ul>
            {game.waitingFor.map((w) => (
              <li key={w.nation}>
                Waiting for {w.nation} to reconnect; removed from the Game in{' '}
                {formatCountdown(room.receivedAt + w.millisLeft - now)}.
              </li>
            ))}
          </ul>
        </Banner>
      )}
      {othersRemoved.length > 0 && (
        <Banner tone="muted" icon="warning">
          Removed from the Game, as their Players were disconnected for too long: {othersRemoved.join(', ')}.
        </Banner>
      )}

      <div className={playing ? 'dash' : 'dash dash--watching'}>
        <TurnPanel
          game={game}
          nation={nation}
          players={room.players}
          playerId={playerId}
          playing={playing}
          countdown={formatCountdown(paused ? game.millisLeft : turnEndsAt - now)}
          onDone={onDone}
        />
        {nation && playing && (
          <>
            <DecisionsPanel
              nation={nation}
              rejection={rejectionIn('decisions')}
              onAnswerDilemma={from('decisions', onAnswerDilemma)}
              onAnswerEncounter={from('decisions', onAnswerEncounter)}
              onOfferTribute={from('decisions', onOfferTribute)}
              onAnswerTribute={from('decisions', onAnswerTribute)}
            />
            <ResourcesPanel nation={nation} />
            <BuildPanel
              nation={nation}
              rejection={rejectionIn('build')}
              onStart={from('build', onStartBuilding)}
              onRepair={from('build', onRepairBuilding)}
              onRebuild={from('build', onRebuildBuilding)}
              onSell={from('build', onSellBuilding)}
            />
            <TrainPanel nation={nation} rejection={rejectionIn('train')} onTrain={from('train', onTrainUnits)} />
            <TechTreePanel
              nation={nation}
              rejection={rejectionIn('tech')}
              onUnlock={from('tech', onUnlockTechnology)}
            />
            <ArmyPanel
              nation={nation}
              rejection={rejectionIn('army')}
              onSend={from('army', onSendWarparty)}
              onRecall={from('army', onRecallWarparty)}
            />
            <SpiesPanel nation={nation} rejection={rejectionIn('spies')} onSend={from('spies', onSendSpy)} />
          </>
        )}
        {nation && <MapPanel nation={nation} />}
        {!nation && playing && <p className="muted">Loading your Nation…</p>}
      </div>
    </div>
  )
}

export default GameScreen
