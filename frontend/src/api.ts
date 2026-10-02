// Talks to the server's REST API and remembers which Rooms this browser has joined.

export type PlayerView = { id: string; nationName: string; ready: boolean; done: boolean }
/** `millisLeft` is how long was left in the Turn when the server sent it. */
export type GameView = { turn: number; nations: string[]; millisLeft: number }
/** Room-wide state; `version` rises with every change. */
export type RoomView = { code: string; version: number; players: PlayerView[]; game: GameView | null }

/** What happened to a Nation as a Turn resolved. */
export type TurnReport = { turn: number; lines: string[] }
/** A kind of Building; once complete it adds its Population and Gold every Turn. */
export type BuildingType = {
  name: string
  gold: number
  turns: number
  populationPerTurn: number
  goldPerPopulation: number
}
/** A Building under construction. */
export type Construction = { building: string; turnsLeft: number }
/** A kind of Unit, trained by paying Gold and Population upfront and then Gold upkeep every Turn. */
export type UnitType = {
  name: string
  gold: number
  population: number
  turns: number
  upkeep: number
  strength: number
  /** The Unit types this one multiplies its Strength against. */
  counters: string[]
}
/** Units of one type in training; they join the Army at the start of `readyOnTurn`. */
export type Training = { unit: string; count: number; readyOnTurn: number }
/**
 * Units sent abroad; `homeward` once it has fought at its target and is heading back.
 * `turnsLeft` is how many more Turns must resolve before it gets where it is heading;
 * 0 once it has arrived at its target and waits for the Battle at the end of this Turn.
 * `plunder` is the Gold and Research it took from its target and is carrying home.
 */
export type Warparty = {
  target: string
  units: Record<string, number>
  homeward: boolean
  turnsLeft: number
  plunder: { gold: number; research: number }
}
/**
 * What only this Player sees: their own Nation, and just the names of the others.
 * `version` rises with every change to the Nation.
 */
export type NationView = {
  version: number
  turn: number
  nation: string
  gold: number
  population: number
  research: number
  otherNations: string[]
  availableBuildings: BuildingType[]
  /** How many of each Building the Nation has completed. */
  buildings: Record<string, number>
  construction: Construction[]
  availableUnits: UnitType[]
  /** How many of each Unit type the Nation has at home. */
  army: Record<string, number>
  training: Training[]
  /** The Nation's Warparties abroad, in the order sent. */
  warparties: Warparty[]
  /** How many Turns a Warparty takes to reach another Nation. */
  travelTurns: number
  /** The Gold the Nation's Units cost each Turn, at home and abroad. */
  upkeep: number
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
