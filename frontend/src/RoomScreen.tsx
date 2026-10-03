import { useEffect, useRef, useState } from 'react'
import { inviteLink, type PlayerView, type RoomView } from './api'
import { Rejection } from './game/controls'
import { suggestRulerName } from './rulerNames'
import { Emblem, Icon } from './ui/Icon'
import { NationEmblem } from './ui/NationName'
import { Panel } from './ui/Panel'

/**
 * This Player's Nation: its Ruler's name, pre-filled with a suggestion that is saved straight away so the
 * Player can ready up at once, with a reroll, and pickers for its colour and emblem that grey out those
 * other Players have.
 */
function YourNation({
  room,
  me,
  rejection,
  onIdentity,
}: {
  room: RoomView
  me: PlayerView
  rejection: string | null
  onIdentity: (ruler: string, colour: string, emblem: string) => void
}) {
  const [draft, setDraft] = useState(() => me.ruler || suggestRulerName())
  // Follow the Ruler's name as the server has it, such as after a change in another tab.
  const [savedRuler, setSavedRuler] = useState(me.ruler)
  if (me.ruler !== savedRuler) {
    setSavedRuler(me.ruler)
    if (me.ruler) setDraft(me.ruler)
  }
  const suggested = useRef(false)
  useEffect(() => {
    if (me.ruler === '' && !suggested.current) {
      suggested.current = true
      onIdentity(draft, me.colour, me.emblem)
    }
  }, [me, draft, onIdentity])
  const others = room.players.filter((p) => p.id !== me.id)
  const ruler = draft.trim() || me.ruler
  // A Ruler must have a name, so an emptied box goes back to the one they have.
  const saveName = () => {
    if (!draft.trim()) setDraft(me.ruler)
    else if (draft.trim() !== me.ruler) onIdentity(draft, me.colour, me.emblem)
  }
  const reroll = () => {
    const name = suggestRulerName(draft)
    setDraft(name)
    onIdentity(name, me.colour, me.emblem)
  }

  return (
    <Panel title="Your Nation" icon="flag" actions={<NationEmblem colour={me.colour} emblem={me.emblem} size={12} />}>
      <div className="stack">
        <Rejection reason={rejection} />
        <form
          className="field"
          onSubmit={(e) => {
            e.preventDefault()
            saveName()
          }}
        >
          <label className="field__label" htmlFor="ruler-name">
            Ruler of {me.nationName}
          </label>
          <div className="field__row">
            <input
              id="ruler-name"
              value={draft}
              maxLength={20}
              onChange={(e) => setDraft(e.target.value)}
              onBlur={saveName}
            />
            <button type="button" onClick={reroll} aria-label="Suggest another name" title="Suggest another name">
              🎲
            </button>
          </div>
        </form>
        <div className="field">
          <span className="field__label">Colour</span>
          <div className="picker">
            {room.colours.map((colour) => {
              const taken = others.find((p) => p.colour === colour)
              return (
                <button
                  key={colour}
                  className="picker__choice picker__choice--swatch"
                  style={{ background: colour }}
                  aria-label={taken ? `Colour ${colour}, taken by ${taken.nationName}` : `Colour ${colour}`}
                  aria-pressed={colour === me.colour}
                  disabled={!!taken}
                  onClick={() => onIdentity(ruler, colour, me.emblem)}
                />
              )
            })}
          </div>
        </div>
        <div className="field">
          <span className="field__label">Emblem</span>
          <div className="picker">
            {room.emblems.map((emblem) => {
              const taken = others.find((p) => p.emblem === emblem)
              return (
                <button
                  key={emblem}
                  className="picker__choice"
                  style={emblem === me.emblem ? { color: me.colour } : undefined}
                  aria-label={taken ? `Emblem ${emblem}, taken by ${taken.nationName}` : `Emblem ${emblem}`}
                  aria-pressed={emblem === me.emblem}
                  disabled={!!taken}
                  onClick={() => onIdentity(ruler, me.colour, emblem)}
                >
                  <Emblem emblem={emblem} />
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </Panel>
  )
}

/**
 * Who has joined, with each Nation's Ruler, colour and emblem, and who is ready; this Player names their
 * Ruler and picks their colour and emblem. The Game starts once 2–4 Players are all ready.
 */
function RoomScreen({
  room,
  playerId,
  rejection,
  onReady,
  onIdentity,
}: {
  room: RoomView
  playerId: string
  rejection: string | null
  onReady: (ready: boolean) => void
  onIdentity: (ruler: string, colour: string, emblem: string) => void
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
            <li
              key={p.id}
              className={p.id === playerId ? 'roster__nation roster__nation--you' : 'roster__nation'}
              style={{ borderLeftColor: p.colour }}
            >
              <NationEmblem colour={p.colour} emblem={p.emblem} />
              <span className="roster__name">
                {p.nationName}
                <span className="roster__ruler">{p.ruler ? `Ruled by ${p.ruler}` : 'No Ruler named yet'}</span>
              </span>
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
        {me && <YourNation room={room} me={me} rejection={rejection} onIdentity={onIdentity} />}
        <Panel title="Ready up" icon="play">
          <div className="stack">
            <p className="muted">The Game starts when 2–4 Players are all ready.</p>
            {me && (
              <button
                className={me.ready ? undefined : 'button--primary'}
                onClick={() => onReady(!me.ready)}
                disabled={!me.ready && !me.ruler}
              >
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
