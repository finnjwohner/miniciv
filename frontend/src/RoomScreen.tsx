import { inviteLink, type RoomView } from './api'
import { Icon } from './ui/Icon'
import { Panel } from './ui/Panel'

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
  const readyCount = room.players.filter((p) => p.ready).length
  const link = inviteLink(room.code)

  return (
    <div className="screen room">
      <Panel
        title={`Room ${room.code}`}
        icon="users"
        actions={
          <span className="muted">
            {readyCount}/{room.players.length} ready
          </span>
        }
      >
        <ul className="roster">
          {room.players.map((p) => (
            <li key={p.id} className={p.id === playerId ? 'roster__nation roster__nation--you' : 'roster__nation'}>
              <Icon name="flag" />
              <span className="roster__name">{p.nationName}</span>
              {p.id === playerId && <span className="tag tag--accent">you</span>}
              {p.ready ? (
                <span className="tag tag--good">
                  <Icon name="check" size={12} />
                  ready
                </span>
              ) : (
                <span className="tag tag--muted">not ready</span>
              )}
            </li>
          ))}
        </ul>
      </Panel>
      <div className="stack">
        <Panel title="Ready up" icon="play">
          <div className="stack">
            <p className="muted">The Game starts when 2–4 Players are all ready.</p>
            {me && (
              <button className={me.ready ? undefined : 'button--primary'} onClick={() => onReady(!me.ready)}>
                {me.ready ? 'Not ready' : 'Ready'}
              </button>
            )}
          </div>
        </Panel>
        <Panel title="Invite" icon="link">
          <div className="field">
            <span className="field__label">Invite link</span>
            <a href={link} className="invite-link">
              {link}
            </a>
          </div>
        </Panel>
      </div>
    </div>
  )
}

export default RoomScreen
