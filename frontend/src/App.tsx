import { useEffect, useState } from 'react'
import { forgetMembership, storedMembership, type Membership } from './api'
import GameScreen from './game/GameScreen'
import Home from './Home'
import JoinRoom from './JoinRoom'
import RoomScreen from './RoomScreen'
import { SettingsProvider } from './SettingsProvider'
import { Icon } from './ui/Icon'
import { SettingsMenu } from './ui/SettingsMenu'
import { TOPBAR_SLOT } from './ui/TopbarSlot'
import { useRoom } from './useRoom'

function usePath() {
  const [path, setPath] = useState(window.location.pathname)
  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])
  const navigate = (to: string) => {
    window.history.pushState(null, '', to)
    setPath(to)
  }
  return { path, navigate }
}

function App() {
  const { path, navigate } = usePath()
  const roomCode = path.match(/^\/rooms\/([^/]+)\/?$/)?.[1]

  return (
    <SettingsProvider>
      <header className="topbar">
        <Icon name="castle" />
        <h1>miniciv</h1>
        <div id={TOPBAR_SLOT} className="topbar__slot" />
        <SettingsMenu />
      </header>
      <main>
        {roomCode ? (
          <Room key={roomCode} code={decodeURIComponent(roomCode).trim().toLowerCase().replace(/\s+/g, '-')} onLeft={() => navigate('/')} />
        ) : (
          <Home onJoined={(code) => navigate(`/rooms/${code}`)} />
        )}
      </main>
    </SettingsProvider>
  )
}

function Room({ code, onLeft }: { code: string; onLeft: () => void }) {
  const [membership, setMembership] = useState(() => storedMembership(code))
  if (!membership) return <JoinRoom code={code} onJoined={setMembership} />
  return <JoinedRoom code={code} membership={membership} onLeft={onLeft} />
}

function JoinedRoom({ code, membership, onLeft }: { code: string; membership: Membership; onLeft: () => void }) {
  const {
    room,
    nation,
    messages,
    rejection,
    unreachable,
    setReady,
    setIdentity,
    startBuilding,
    repairBuilding,
    rebuildBuilding,
    sellBuilding,
    trainUnits,
    sendWarparty,
    recallWarparty,
    sendSpy,
    setMission,
    setTask,
    cancelTask,
    answerDilemma,
    answerHaul,
    unlockTechnology,
    offerTribute,
    answerTribute,
    answerEncounter,
    sendMessage,
    markDone,
    leaveGame,
    playAgain,
  } = useRoom(code, membership.token, membership.playerId)
  // Browsers don't say why a connection was refused, such as the server having as many as it allows.
  const unreachableAlert = unreachable && (
    <p className="alert" role="alert">
      Can't connect to miniciv right now. Retrying…
    </p>
  )
  if (!room)
    return (
      <div className="screen screen--narrow">
        {unreachableAlert || <p className="muted">Connecting to Room {code}…</p>}
      </div>
    )
  // A Player who has not pressed Play again still sees the Game that ended, while others are back in the Room.
  const previous = room.previous
  const gameRoom =
    !room.game && previous?.players.some((p) => p.id === membership.playerId)
      ? { ...room, game: previous.game, players: [...previous.players, ...room.players] }
      : room
  // A Player who never pressed Play again is left behind once the next Game starts without them.
  if (gameRoom.game && !gameRoom.players.some((p) => p.id === membership.playerId))
    return (
      <div className="screen screen--narrow">
        <p className="muted">This Room has moved on to a new Game without you.</p>
        <button
          className="button--primary"
          onClick={() => {
            forgetMembership(code)
            onLeft()
          }}
        >
          Back to the menu
        </button>
      </div>
    )
  if (gameRoom.game)
    return (
      <div className="screen screen--wide">
        {unreachableAlert}
        <GameScreen
          room={gameRoom}
          nation={nation}
          playerId={membership.playerId}
          rejection={rejection}
          onStartBuilding={startBuilding}
          onRepairBuilding={repairBuilding}
          onRebuildBuilding={rebuildBuilding}
          onSellBuilding={sellBuilding}
          onTrainUnits={trainUnits}
          onSendWarparty={sendWarparty}
          onRecallWarparty={recallWarparty}
          onSendSpy={sendSpy}
          onSetMission={setMission}
          onSetTask={setTask}
          onCancelTask={cancelTask}
          onAnswerDilemma={answerDilemma}
          onAnswerHaul={answerHaul}
          onUnlockTechnology={unlockTechnology}
          onOfferTribute={offerTribute}
          onAnswerTribute={answerTribute}
          onAnswerEncounter={answerEncounter}
          onDone={markDone}
          messages={messages}
          onSendMessage={sendMessage}
          onPlayAgain={playAgain}
          onLeave={() => {
            leaveGame()
            forgetMembership(code)
            onLeft()
          }}
        />
      </div>
    )
  return (
    <>
      {unreachableAlert}
      <RoomScreen
        room={room}
        playerId={membership.playerId}
        rejection={rejection}
        onReady={setReady}
        onIdentity={setIdentity}
      />
    </>
  )
}

export default App
