import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { MAX_MESSAGE_LENGTH, type Message, type PlayerView } from '../api'
import { Icon } from '../ui/Icon'
import { NationEmblem } from '../ui/NationName'
import { Panel } from '../ui/Panel'
import { TopbarSlot } from '../ui/TopbarSlot'

/** The id of the last Message read from each Nation, by its name. */
type LastRead = Record<string, number>

function readKey(code: string, playerId: string) {
  return `miniciv.read.${code}.${playerId}`
}

/**
 * Which Messages the Player has read in each conversation, kept in this browser so a reload does not
 * show the whole history as unread.
 */
function useLastRead(code: string, playerId: string) {
  const [lastRead, setLastRead] = useState<LastRead>(() => {
    try {
      return JSON.parse(localStorage.getItem(readKey(code, playerId)) ?? '{}')
    } catch {
      return {}
    }
  })
  const markRead = (nation: string, id: number) => {
    if ((lastRead[nation] ?? 0) >= id) return
    const next = { ...lastRead, [nation]: id }
    setLastRead(next)
    try {
      localStorage.setItem(readKey(code, playerId), JSON.stringify(next))
    } catch {
      // Unread counts then only last as long as this page.
    }
  }
  return { lastRead, markRead }
}

/** A count of unread Messages, or nothing when there are none. */
function UnreadBadge({ count }: { count: number }) {
  if (count === 0) return null
  return (
    <span className="unread" aria-hidden>
      {count}
    </span>
  )
}

/**
 * The Player's private Messages: a toggle in the top bar with the total unread, and the drawer it opens
 * with one conversation per other Player, each with its own unread count. A conversation is read while
 * it is open. Escape or the toggle closes the drawer. A Removed Nation can neither send nor receive.
 */
export function MessagesDrawer({
  code,
  players,
  playerId,
  removed,
  messages,
  rejection,
  onSend,
}: {
  code: string
  players: PlayerView[]
  playerId: string
  removed: string[]
  messages: Message[]
  rejection: string | null
  onSend: (to: string, text: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [withNation, setWithNation] = useState<string | null>(null)
  const { lastRead, markRead } = useLastRead(code, playerId)
  const toggle = useRef<HTMLButtonElement>(null)
  const me = players.find((p) => p.id === playerId)?.nationName
  const others = players.filter((p) => p.id !== playerId)
  /** The conversation the Player is reading now, if any. */
  const reading = open ? withNation : null

  const incoming = (nation: string) => messages.filter((m) => m.from === nation && m.to === me)
  const unread = (nation: string) =>
    nation === reading ? 0 : incoming(nation).filter((m) => m.id > (lastRead[nation] ?? 0)).length
  const totalUnread = others.reduce((total, p) => total + unread(p.nationName), 0)

  const latestReading = reading ? incoming(reading).at(-1)?.id : undefined
  useEffect(() => {
    if (reading && latestReading !== undefined) markRead(reading, latestReading)
    // markRead is new each render; only a new conversation or Message to read matters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reading, latestReading])

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: globalThis.KeyboardEvent) => {
      if (e.key !== 'Escape') return
      setOpen(false)
      toggle.current?.focus()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  return (
    <>
      <TopbarSlot>
        <button
          ref={toggle}
          className="messages-toggle"
          aria-label={totalUnread > 0 ? `Messages, ${totalUnread} unread` : 'Messages'}
          title="Messages"
          aria-haspopup="dialog"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <Icon name="message" size={24} />
          <UnreadBadge count={totalUnread} />
        </button>
      </TopbarSlot>
      {open && (
        <Panel title="Messages" icon="message" className="messages-drawer" role="dialog" rejection={rejection}>
          <ul className="conversations">
            {others.map((p) => (
              <li key={p.id}>
                <button
                  className={p.nationName === withNation ? 'conversation conversation--open' : 'conversation'}
                  style={{ borderColor: p.nationName === withNation ? p.colour : undefined }}
                  aria-label={unread(p.nationName) > 0 ? `${p.nationName}, ${unread(p.nationName)} unread` : p.nationName}
                  aria-pressed={p.nationName === withNation}
                  onClick={() => setWithNation(p.nationName)}
                >
                  <NationEmblem colour={p.colour} emblem={p.emblem} size={12} />
                  <span style={{ color: p.colour }}>{p.nationName}</span>
                  <UnreadBadge count={unread(p.nationName)} />
                </button>
              </li>
            ))}
          </ul>
          {withNation ? (
            <Conversation
              nation={withNation}
              messages={messages.filter((m) => m.from === withNation || m.to === withNation)}
              me={me}
              blocked={
                me !== undefined && removed.includes(me)
                  ? 'Your Nation was removed from the Game, so you can no longer send Messages.'
                  : removed.includes(withNation)
                    ? `${withNation} was removed from the Game, so it can no longer receive Messages.`
                    : null
              }
              onSend={(text) => onSend(withNation, text)}
            />
          ) : (
            <p className="muted">Choose a Nation to message privately. Only the two of you see your Messages.</p>
          )}
        </Panel>
      )}
    </>
  )
}

/**
 * The Messages between the Player and one other Nation, oldest first, kept scrolled to the newest, and
 * a box to write the next one in unless `blocked` says why not. Enter sends; Shift+Enter starts a new line.
 */
function Conversation({
  nation,
  messages,
  me,
  blocked,
  onSend,
}: {
  nation: string
  messages: Message[]
  me: string | undefined
  blocked: string | null
  onSend: (text: string) => void
}) {
  const [draft, setDraft] = useState('')
  const thread = useRef<HTMLOListElement>(null)

  useEffect(() => {
    if (thread.current) thread.current.scrollTop = thread.current.scrollHeight
  }, [messages.length, nation])

  const send = (e?: FormEvent) => {
    e?.preventDefault()
    if (draft.trim() === '') return
    onSend(draft)
    setDraft('')
  }
  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) send(e)
  }

  return (
    <div className="conversation-view">
      <ol className="thread" ref={thread} aria-label={`Messages with ${nation}`}>
        {messages.length === 0 && <li className="muted">No Messages with {nation} yet.</li>}
        {messages.map((m) => (
          <li key={m.id} className={m.from === me ? 'thread__message thread__message--mine' : 'thread__message'}>
            {m.text}
          </li>
        ))}
      </ol>
      {blocked ? (
        <p className="muted">{blocked}</p>
      ) : (
        <form className="compose" onSubmit={send}>
          <textarea
            aria-label={`Message to ${nation}`}
            rows={2}
            maxLength={MAX_MESSAGE_LENGTH}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKeyDown}
          />
          <div className="compose__bar">
            <span className="muted">
              {draft.length}/{MAX_MESSAGE_LENGTH}
            </span>
            <button type="submit" disabled={draft.trim() === ''}>
              Send
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
