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
/** A kind of Building; once complete it adds its Population, Gold and Research every Turn, and raises Defense while standing. */
export type BuildingType = {
  name: string
  gold: number
  turns: number
  populationPerTurn: number
  goldPerTurn: number
  goldPerPopulation: number
  researchPerTurn: number
  defense: number
}
/**
 * The Defense a Nation's defensive Buildings of one type have lost and are not yet being repaired or
 * rebuilt for: `destroyed` of them are destroyed, and one more has lost `damaged` Defense (0 if none has).
 * Repairing that one costs `repairGold` and takes `repairTurns`; rebuilding a destroyed one costs
 * `rebuildGold` and takes `rebuildTurns`.
 */
export type BuildingDamage = {
  building: string
  destroyed: number
  damaged: number
  repairGold: number
  repairTurns: number
  rebuildGold: number
  rebuildTurns: number
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
  /** Whether it is a Scout: never fights or leaves its Nation, but may discover other Nations' Warparties abroad. */
  scout: boolean
  /** Whether it is a Spy: sent on its own, never in a Warparty, to stay in another Nation and report its hidden state. */
  spy: boolean
}
/** Units of one type in training; they join the Army at the start of `readyOnTurn`. */
export type Training = { unit: string; count: number; readyOnTurn: number }
/**
 * Units sent abroad, told apart by `id`; `homeward` once it has fought at its target, or turned back
 * before it, and is heading back.
 * `turnsLeft` is how many more Turns must resolve before it gets where it is heading;
 * 0 once it has arrived at its target and waits for the Battle at the end of this Turn.
 * `turnsTravelled` is how many Turns it travelled toward its target, which a Recall takes to get home.
 * `plunder` is the Gold and Research it took from its target and is carrying home.
 */
export type Warparty = {
  id: number
  target: string
  units: Record<string, number>
  homeward: boolean
  turnsLeft: number
  turnsTravelled: number
  plunder: { gold: number; research: number }
}
/**
 * A Spy sent abroad, told apart by `id`; `turnsLeft` is how many more Turns must resolve before it gets
 * where it is heading, 0 once it is in place at its target, where it stays and now and then reports.
 * `homeward` once its target is out of the Game and it is heading back.
 */
export type Spy = {
  id: number
  unit: string
  target: string
  homeward: boolean
  turnsLeft: number
  turnsTravelled: number
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
 * A Technology in the Tech Tree as this Nation stands with it: `UNLOCKED`, `AVAILABLE` once all its
 * `prerequisites` are unlocked, otherwise `LOCKED`. Unlocking it costs `research` and gives access to the
 * Unit and Building types it `unlocks`.
 */
export type TechTreeEntry = {
  name: string
  research: number
  prerequisites: string[]
  unlocks: string[]
  standing: 'UNLOCKED' | 'AVAILABLE' | 'LOCKED'
}
/** Another Nation's Warparty that arrived at this one and waits for the Battle as this Turn ends. */
export type ArrivedWarparty = { nation: string; warparty: number }
/**
 * Tribute offered this Turn by the defending Nation `from` to the attacking Nation `to` in return for
 * Recalling its Warparty `warparty`. `answer` is `NONE` until the attacker accepts or refuses; unanswered
 * as the Turn ends, the offer lapses and the Battle goes ahead.
 */
export type Tribute = {
  from: string
  to: string
  warparty: number
  gold: number
  population: number
  answer: 'NONE' | 'ACCEPTED' | 'REFUSED'
}
/** One of the Warparties in an Encounter, as it was when they met, and what its Player chose so far. */
export type EncounterSide = {
  nation: string
  warparty: number
  units: Record<string, number>
  choice: 'NONE' | 'FIGHT' | 'PASS'
}
/**
 * Two Warparties from different Nations that met by chance abroad as the last Turn resolved; if either
 * Player chooses to fight before this Turn ends, they fight as it ends.
 */
export type Encounter = { first: EncounterSide; second: EncounterSide }
/** Something learned of another Nation's hidden state, on the Turn whose resolution it was learned in. */
export type Fact<T> = { value: T; turn: number }
/**
 * What this Nation's Spies have reported of another Nation, each the latest of its kind, or null if
 * none has been. Its Defense is learned along with its Buildings.
 */
export type Facts = {
  gold: Fact<number> | null
  population: Fact<number> | null
  research: Fact<number> | null
  army: Fact<Record<string, number>> | null
  buildings: Fact<Record<string, number>> | null
  defense: Fact<number> | null
}
/**
 * A Nation on the Map, as another Nation knows it: its name, whether it is still in the Game,
 * was conquered, or was removed as its Player was disconnected for too long, and what this Nation has
 * learned of it.
 */
export type MapNation = { name: string; standing: 'IN_GAME' | 'CONQUERED' | 'REMOVED'; facts: Facts }
/**
 * What only this Player sees: their own Nation, and the Map with the names of the others and what their
 * Spies reported of them.
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
  /** The Building types the Nation may start: those needing no Technology, or one it has unlocked. */
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
  /** The Unit types the Nation may train: those needing no Technology, or one it has unlocked. */
  availableUnits: UnitType[]
  /** How many of each Unit type the Nation has at home. */
  army: Record<string, number>
  training: Training[]
  /** The Nation's Warparties abroad, in the order sent. */
  warparties: Warparty[]
  /** The Nation's Spies abroad, in the order sent. */
  spies: Spy[]
  /** How many Turns a Warparty takes to reach another Nation. */
  travelTurns: number
  /** The Gold the Nation's Units cost each Turn, at home and abroad. */
  upkeep: number
  /** Other Nations' Warparties that arrived at this one and wait for the Battle as this Turn ends. */
  arrivals: ArrivedWarparty[]
  /** The Tribute this Nation offered or was offered this Turn, in the order offered. */
  tributes: Tribute[]
  /** The Encounters this Nation's Warparties had as the last Turn resolved, in the order they met. */
  encounters: Encounter[]
  /** Every Technology in the Tech Tree, in the ruleset's order. */
  techTree: TechTreeEntry[]
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
