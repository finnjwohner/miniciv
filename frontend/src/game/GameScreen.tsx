import { useEffect, useState, type ReactNode } from 'react'
import type { Message, NationView, TaskKind } from '../api'
import { Icon, type IconName } from '../ui/Icon'
import { NationName } from '../ui/NationName'
import type { ReceivedRoom } from '../useRoom'
import { ArmyPanel, MapPanel, SpiesPanel } from './AbroadPanels'
import { ActionTabs, type ActionTab } from './ActionTabs'
import { DecisionsPanel } from './DecisionsPanel'
import { formatCountdown } from './format'
import { LeaveGame } from './LeaveGame'
import { MessagesDrawer } from './MessagesDrawer'
import { BuildPanel, ResourcesPanel, TechTreePanel, TrainPanel } from './NationPanels'
import { TurnPanel, TurnReportPanel } from './TurnPanel'
import { useTurnStartCues } from './turnStart'

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
type Area = 'decisions' | 'messages' | 'play-again' | ActionTab

/**
 * One Turn of the Game as this Player sees it, as a dashboard. On a wide screen it fits without page
 * scroll in three columns: the Map; the Turn timer and End Turn, Resources, this Turn's decisions and the
 * Turn Report; and the action panels in tabs, which a Player out of the Game has none of. Narrower
 * screens stack the panels, the Turn panel first. Each new Turn arrives with its cues. The Player's
 * private Messages open in a drawer from the top bar.
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
  onSetMission,
  onSetTask,
  onCancelTask,
  onAnswerDilemma,
  onAnswerHaul,
  onUnlockTechnology,
  onOfferTribute,
  onAnswerTribute,
  onAnswerEncounter,
  onDone,
  messages,
  onSendMessage,
  onLeave,
  onPlayAgain,
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
  onSetMission: (spy: number, mission: string) => void
  onSetTask: (kind: TaskKind, target: string | null, scouts: number) => void
  onCancelTask: (task: number) => void
  onAnswerDilemma: (dilemma: string, choice: string) => void
  onAnswerHaul: (haul: number, keep: boolean) => void
  onUnlockTechnology: (technology: string) => void
  onOfferTribute: (nation: string, warparty: number, gold: number, population: number) => void
  onAnswerTribute: (warparty: number, accept: boolean) => void
  onAnswerEncounter: (warparty: number, fight: boolean) => void
  onDone: (turn: number) => void
  messages: Message[]
  onSendMessage: (to: string, text: string) => void
  onLeave: () => void
  onPlayAgain: () => void
}) {
  const now = useNow()
  useTurnStartCues(nation)
  const [actedIn, setActedIn] = useState<Area | null>(null)
  const game = room.game!
  const me = room.players.find((p) => p.id === playerId)
  const turnEndsAt = room.receivedAt + game.millisLeft
  const knockedOut = me !== undefined && game.conquered.includes(me.nationName)
  const removed = me !== undefined && game.removed.includes(me.nationName)
  const playing = !game.winner && !knockedOut && !removed
  /** Whether the Player has action panels to show, in tabs. */
  const actionable = playing && nation !== null
  const paused = game.waitingFor.length > 0
  const othersRemoved = game.removed.filter((n) => n !== me?.nationName)
  /** A Nation's name with its colour and emblem, as its Player chose them. */
  const named = (name: string) => {
    const player = room.players.find((p) => p.nationName === name)
    return <NationName name={name} colour={player?.colour} emblem={player?.emblem} />
  }

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
      <LeaveGame onLeave={onLeave} />
      <MessagesDrawer
        code={room.code}
        players={room.players}
        playerId={playerId}
        removed={game.removed}
        messages={messages}
        rejection={rejectionIn('messages')}
        onSend={from('messages', onSendMessage)}
      />
      {game.winner && (
        <Banner tone="accent" icon="trophy">
          <strong>
            {game.winner === me?.nationName ? 'You have won the Game!' : <>{named(game.winner)} has won the Game.</>}
          </strong>
          <button className="button--primary" onClick={from('play-again', onPlayAgain)}>
            Play again
          </button>
          {rejectionIn('play-again') && <p>{rejectionIn('play-again')}</p>}
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
                Waiting for {named(w.nation)} to reconnect; removed from the Game in{' '}
                {formatCountdown(room.receivedAt + w.millisLeft - now)}.
              </li>
            ))}
          </ul>
        </Banner>
      )}
      {othersRemoved.length > 0 && (
        <Banner tone="muted" icon="warning">
          Removed from the Game, as their Players were disconnected for too long:{' '}
          {othersRemoved.map((n, i) => (
            <span key={n}>
              {i > 0 && ', '}
              {named(n)}
            </span>
          ))}
          .
        </Banner>
      )}

      <div className={actionable ? 'dash' : 'dash dash--watching'}>
        {nation && <MapPanel nation={nation} />}
        <div className="dash__centre">
          <TurnPanel
            game={game}
            players={room.players}
            playerId={playerId}
            playing={playing}
            millisLeft={paused ? game.millisLeft : turnEndsAt - now}
            onDone={onDone}
          />
          {nation && playing && (
            <>
              <ResourcesPanel nation={nation} />
              <DecisionsPanel
                nation={nation}
                rejection={rejectionIn('decisions')}
                onAnswerDilemma={from('decisions', onAnswerDilemma)}
                onAnswerHaul={from('decisions', onAnswerHaul)}
                onAnswerEncounter={from('decisions', onAnswerEncounter)}
                onOfferTribute={from('decisions', onOfferTribute)}
                onAnswerTribute={from('decisions', onAnswerTribute)}
              />
            </>
          )}
          {nation && <TurnReportPanel report={nation.turnReport} />}
          {!nation && playing && <p className="muted">Loading your Nation…</p>}
        </div>
        {nation && actionable && (
          <ActionTabs
            nation={nation}
            panels={{
              build: (
                <BuildPanel
                  nation={nation}
                  rejection={rejectionIn('build')}
                  onStart={from('build', onStartBuilding)}
                  onRepair={from('build', onRepairBuilding)}
                  onRebuild={from('build', onRebuildBuilding)}
                  onSell={from('build', onSellBuilding)}
                />
              ),
              train: (
                <TrainPanel nation={nation} rejection={rejectionIn('train')} onTrain={from('train', onTrainUnits)} />
              ),
              tech: (
                <TechTreePanel
                  nation={nation}
                  rejection={rejectionIn('tech')}
                  onUnlock={from('tech', onUnlockTechnology)}
                />
              ),
              army: (
                <ArmyPanel
                  nation={nation}
                  rejection={rejectionIn('army')}
                  onSend={from('army', onSendWarparty)}
                  onRecall={from('army', onRecallWarparty)}
                />
              ),
              spies: (
                <SpiesPanel
                  nation={nation}
                  rejection={rejectionIn('spies')}
                  onSend={from('spies', onSendSpy)}
                  onSetMission={from('spies', onSetMission)}
                  onSetTask={from('spies', onSetTask)}
                  onCancelTask={from('spies', onCancelTask)}
                />
              ),
            }}
          />
        )}
      </div>
    </div>
  )
}

export default GameScreen
