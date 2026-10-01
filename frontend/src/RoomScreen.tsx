import { inviteLink, type RoomView } from './api'

/** Who has joined and who is ready; the Game starts once 2–4 Players are all ready. */
function RoomScreen({
  room,
  playerId,
  onReady,
}: {
  room: RoomView
  playerId: string
  onReady: (ready: boolean) => void
}) {
  const me = room.players.find((p) => p.id === playerId)

  return (
    <section>
      <h2>Room {room.code}</h2>
      <p>
        Invite link: <a href={inviteLink(room.code)}>{inviteLink(room.code)}</a>
      </p>
      <ul>
        {room.players.map((p) => (
          <li key={p.id}>
            {p.nationName}
            {p.id === playerId && ' (you)'} — {p.ready ? 'ready' : 'not ready'}
          </li>
        ))}
      </ul>
      {me && <button onClick={() => onReady(!me.ready)}>{me.ready ? 'Not ready' : 'Ready'}</button>}
      <p>The Game starts when 2–4 Players are all ready.</p>
    </section>
  )
}

export default RoomScreen
