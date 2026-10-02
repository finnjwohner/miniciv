import { useEffect, useState } from 'react'
import { storedMembership, type Membership } from './api'
import GameScreen from './game/GameScreen'
import Home from './Home'
import JoinRoom from './JoinRoom'
import RoomScreen from './RoomScreen'
import { Icon } from './ui/Icon'
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
    <>
      <header className="topbar">
        <Icon name="castle" />
        <h1>miniciv</h1>
      </header>
      <main>
        {roomCode ? (
          <Room key={roomCode} code={decodeURIComponent(roomCode).toUpperCase()} />
        ) : (
          <Home onJoined={(code) => navigate(`/rooms/${code}`)} />
        )}
      </main>
    </>
  )
}

function Room({ code }: { code: string }) {
  const [membership, setMembership] = useState(() => storedMembership(code))
  if (!membership) return <JoinRoom code={code} onJoined={setMembership} />
  return <JoinedRoom code={code} membership={membership} />
}

function JoinedRoom({ code, membership }: { code: string; membership: Membership }) {
  const {
    room,
    nation,
    rejection,
    setReady,
    startBuilding,
    repairBuilding,
    rebuildBuilding,
    trainUnits,
    sendWarparty,
    recallWarparty,
    sendSpy,
    answerDilemma,
    unlockTechnology,
    offerTribute,
    answerTribute,
    answerEncounter,
    markDone,
  } = useRoom(code, membership.token)
  if (!room)
    return (
      <div className="screen screen--narrow">
        <p className="muted">Connecting to Room {code}…</p>
      </div>
    )
  if (room.game)
    return (
      <div className="screen screen--wide">
        <GameScreen
          room={room}
          nation={nation}
          playerId={membership.playerId}
          rejection={rejection}
          onStartBuilding={startBuilding}
          onRepairBuilding={repairBuilding}
          onRebuildBuilding={rebuildBuilding}
          onTrainUnits={trainUnits}
          onSendWarparty={sendWarparty}
          onRecallWarparty={recallWarparty}
          onSendSpy={sendSpy}
          onAnswerDilemma={answerDilemma}
          onUnlockTechnology={unlockTechnology}
          onOfferTribute={offerTribute}
          onAnswerTribute={answerTribute}
          onAnswerEncounter={answerEncounter}
          onDone={markDone}
        />
      </div>
    )
  return <RoomScreen room={room} playerId={membership.playerId} onReady={setReady} />
}

export default App
