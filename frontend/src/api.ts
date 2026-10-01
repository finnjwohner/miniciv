// Talks to the server's REST API and remembers which Rooms this browser has joined.

export type PlayerView = { id: string; nationName: string; ready: boolean; done: boolean }
/** `millisLeft` is how long was left in the Turn when the server sent it. */
export type GameView = { turn: number; nations: string[]; millisLeft: number }
/** Room-wide state; `version` rises with every change. */
export type RoomView = { code: string; version: number; players: PlayerView[]; game: GameView | null }

/** What happened to a Nation as a Turn resolved. */
export type TurnReport = { turn: number; lines: string[] }
/** What only this Player sees: their own Nation, and just the names of the others. */
export type NationView = {
  turn: number
  nation: string
  gold: number
  population: number
  research: number
  otherNations: string[]
  turnReport: TurnReport | null
}

/** What this browser keeps after joining a Room; the token is secret. */
export type Membership = { playerId: string; token: string }

export async function createRoom(): Promise<string> {
  const res = await fetch('/api/rooms', { method: 'POST' })
  if (!res.ok) throw new Error('Could not create a Room.')
  const body: { code: string } = await res.json()
  return body.code
}

/** Joins a Room and stores the issued Player token; throws with the server's reason if refused. */
export async function joinRoom(code: string, nationName: string): Promise<Membership> {
  const res = await fetch(`/api/rooms/${encodeURIComponent(code)}/players`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nationName }),
  })
  const body = await res.json().catch(() => null)
  if (!res.ok) throw new Error(body?.detail ?? 'Could not join the Room.')
  const membership: Membership = { playerId: body.playerId, token: body.token }
  memberships.set(code, membership)
  try {
    localStorage.setItem(storageKey(code), JSON.stringify(membership))
  } catch {
    // Without storage the Player can still play in this tab; only rejoining after a reload is lost.
  }
  return membership
}

/** Memberships joined in this tab, in case localStorage is unavailable. */
const memberships = new Map<string, Membership>()

export function storedMembership(code: string): Membership | null {
  try {
    const stored = localStorage.getItem(storageKey(code))
    if (stored) return JSON.parse(stored)
  } catch {
    // Fall back to this tab's memory.
  }
  return memberships.get(code) ?? null
}

export function inviteLink(code: string): string {
  return `${window.location.origin}/rooms/${code}`
}

function storageKey(code: string) {
  return `miniciv.room.${code}`
}
