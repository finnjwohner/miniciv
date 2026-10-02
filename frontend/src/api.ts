// Talks to the server's REST API and remembers which Rooms this browser has joined.

export type PlayerView = { id: string; nationName: string; ready: boolean; done: boolean }
/**
 * A Nation whose Player is disconnected; `millisLeft` is how much longer, when the server sent it,
 * they may stay disconnected before their Nation is removed.
 */
export type Waiting = { nation: string; millisLeft: number }
/**
 * `millisLeft` is how long was left in the Turn when the server sent it, 0 once the Game is won; it
 * does not run down while the Game is paused. `conquered` lists the Nations knocked out so far,
 * `removed` those removed as their Players were disconnected for too long; `winner` is null while
 * the Game goes on. The Game is paused while `waitingFor` lists anyone.
 */
export type GameView = {
  turn: number
  nations: string[]
  millisLeft: number
  conquered: string[]
  removed: string[]
  winner: string | null
  waitingFor: Waiting[]
}
/** Room-wide state; `version` rises with every change. */
export type RoomView = { code: string; version: number; players: PlayerView[]; game: GameView | null }

/** What happened to a Nation as a Turn resolved. */
export type TurnReport = { turn: number; lines: string[] }
/** A kind of Building; once complete it adds its Population and Gold every Turn, and raises Defense while standing. */
export type BuildingType = {
  name: string
  gold: number
  turns: number
  populationPerTurn: number
  goldPerPopulation: number
  defense: number
}
/**
 * The Defense a Nation's defensive Buildings of one type have lost and are not yet being repaired or
 * rebuilt for: `destroyed` of them are destroyed, and one more has lost `damaged` Defense (0 if none has).
 * Repairing that one costs `repairGold` and takes `repairTurns`; rebuilding a destroyed one costs what
 * constructing it does.
 */
export type BuildingDamage = {
  building: string
  destroyed: number
  damaged: number
  repairGold: number
  repairTurns: number
}
/** A defensive Building being repaired, or rebuilt if `rebuild`, restoring `defense` once done. */
export type Repair = { building: string; rebuild: boolean; defense: number; turnsLeft: number }
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
/** What a Dilemma's choice changes; resources it would take below 0 stop at 0. */
export type Effects = { gold: number; population: number; research: number; damageDefense: number; travelTurns: number }
/** One choice a Dilemma offers; `worst` is the one that applies if the Player does not choose before the Turn ends. */
export type Choice = { name: string; effects: Effects; worst: boolean; text: string }
/**
 * A Dilemma this Player may answer, by its `name`, until the Turn ends; `chosen` is the name of the
 * choice they made, or null. The choice made, or else the worst, applies as the Turn ends.
 */
export type Dilemma = { name: string; text: string; choices: Choice[]; chosen: string | null }
/**
 * A Nation on the Map, as another Nation knows it: its name, and whether it is still in the Game,
 * was conquered, or was removed as its Player was disconnected for too long.
 */
export type MapNation = { name: string; standing: 'IN_GAME' | 'CONQUERED' | 'REMOVED' }
/**
 * What only this Player sees: their own Nation, and the Map with just the names of the others.
 * `version` rises with every change to the Nation.
 */
export type NationView = {
  version: number
  turn: number
  nation: string
  gold: number
  population: number
  research: number
  /** Every Nation that took part in the Game, this one included, in the order they joined. */
  map: MapNation[]
  availableBuildings: BuildingType[]
  /** How many of each Building the Nation has completed. */
  buildings: Record<string, number>
  construction: Construction[]
  /** The defensive Buildings being repaired or rebuilt, in the order started. */
  repairs: Repair[]
  /** The Nation's Defense, raised by its defensive Buildings. */
  defense: number
  /** The defensive Buildings that have lost Defense, by type. */
  damage: BuildingDamage[]
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
  /** The Dilemmas awaiting this Player's choice before the Turn ends, in the order they happened. */
  dilemmas: Dilemma[]
  /** The Nation or Nations that conquered this one, such as "Rome and Egypt", knocking this Player out, or null. */
  conqueredBy: string | null
  /** The Nation that won the Game, or null while it goes on. */
  winner: string | null
}

/** The other Nations still in the Game, in the order they joined. */
export function otherNations(nation: NationView): string[] {
  return nation.map.filter((n) => n.standing === 'IN_GAME' && n.name !== nation.nation).map((n) => n.name)
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
