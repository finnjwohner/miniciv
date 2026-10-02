import { useEffect, useState } from 'react'
import { storedMembership, type Membership } from './api'
import GameScreen from './GameScreen'
import Home from './Home'
import JoinRoom from './JoinRoom'
import RoomScreen from './RoomScreen'
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
    <main>
      <h1>miniciv</h1>
      {roomCode ? (
        <Room key={roomCode} code={decodeURIComponent(roomCode).toUpperCase()} />
      ) : (
        <Home onJoined={(code) => navigate(`/rooms/${code}`)} />
      )}
    </main>
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
    answerDilemma,
    markDone,
  } = useRoom(code, membership.token)
  if (!room) return <p>Connecting to Room {code}…</p>
  if (room.game)
    return (
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
        onAnswerDilemma={answerDilemma}
        onDone={markDone}
      />
    )
  return <RoomScreen room={room} playerId={membership.playerId} onReady={setReady} />
}

export default App
