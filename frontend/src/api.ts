// Talks to the server's REST API and remembers which Rooms this browser has joined.

/**
 * A Player as everyone in the Room sees them: their Nation, its Ruler (empty until named), and the
 * colour and emblem they picked, each unique in the Room.
 */
export type PlayerView = {
  id: string
  nationName: string
  ruler: string
  colour: string
  emblem: string
  ready: boolean
  done: boolean
}
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
/**
 * Room-wide state; `version` rises with every change. `colours` and `emblems` are what a Player may
 * pick for their Nation; emblems are pixel icon names.
 */
export type RoomView = {
  code: string
  version: number
  colours: string[]
  emblems: string[]
  players: PlayerView[]
  game: GameView | null
}

/** The kinds of section a Turn Report is grouped into. */
export type TurnReportKind =
  'DECISIONS_AND_ALERTS' | 'WARFARE' | 'COMPLETED' | 'EVENTS' | 'SPIES_AND_SCOUTS' | 'RESOURCES'
/** A resource a Nation owns and spends. */
export type Resource = 'GOLD' | 'POPULATION' | 'RESEARCH'
/**
 * An amount of a resource in a Turn Report line, such as 20 Gold, never negative, and what it was to the
 * Nation reading it: a gain, a loss, or neutral when it is about another Nation or nothing changed hands.
 */
export type ReportAmount = { resource: Resource; amount: number; effect: 'GAIN' | 'LOSS' | 'NEUTRAL' }
/** One line of a Turn Report: its plain text and amounts, in the order read. */
export type ReportLine = { parts: ({ text: string } | ReportAmount)[] }
/** One section of a Turn Report: what happened of one kind, in the order it happened. */
export type TurnReportSection = { kind: TurnReportKind; lines: ReportLine[] }
/** How a Nation's Gold, Population and Research moved as a Turn resolved, not counting what it spent during the Turn. */
export type NetChange = { gold: number; population: number; research: number }
/** What happened to a Nation as a Turn resolved: how its resources moved, and the sections with something in them, in their fixed order. */
export type TurnReport = {
  turn: number
  netChange: NetChange
  sections: TurnReportSection[]
}
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
 * The harm a Nation's Buildings of one type have taken and that is not yet being repaired or rebuilt:
 * `damaged` of them are Damaged, and Repairing one costs `repairGold` and takes `repairTurns`.
 * A defensive type has at most one Damaged, which has lost `defenseLost` Defense, and `destroyed` of
 * them are Destroyed, Rebuilding one costing `rebuildGold` and taking `rebuildTurns`. Any other type
 * is never Destroyed, so those are null, and its Damaged Buildings add nothing until Repaired.
 */
export type BuildingDamage = {
  building: string
  damaged: number
  repairGold: number
  repairTurns: number
} & (
  | { defenseLost: number; destroyed: number; rebuildGold: number; rebuildTurns: number }
  | { defenseLost: null; destroyed: null; rebuildGold: null; rebuildTurns: null }
)
/**
 * A Building being repaired, or rebuilt if `rebuild`; a defensive one restores `defense` once done,
 * which is 0 for any other.
 */
export type Repair = {
  building: string
  rebuild: boolean
  defense: number
  turnsLeft: number
}
/** A Building under construction. */
export type Construction = { building: string; turnsLeft: number }
/**
 * What a Nation's Gold will change by as this Turn resolves: its Population's and Buildings' income less its
 * Units' upkeep, after any it cannot pay for desert, plus its active Lasting Event's change. Events, Dilemmas,
 * Battles, Plunder and Tribute aside.
 */
export type GoldForecast = {
  income: number
  upkeep: number
  /** How much the active Lasting Event will change Gold by; 0 if there is none. */
  lastingGold: number
}
/** An Event whose change applies again as each of its Turns left resolves, such as a Drought. */
export type LastingEvent = {
  name: string
  /** How many more Turns it applies on, this one included. */
  turnsLeft: number
  /** How much Gold, Population and Research change by each Turn. */
  gold: number
  population: number
  research: number
  /** Whether Population does not grow. */
  noGrowth: boolean
}
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
  /**
   * Whether it is a Scout: roams the wilds from the moment it is trained, never in the Army and never fighting,
   * but may discover other Nations' Warparties abroad and catch their travelling Spies.
   */
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
 * `turnsTravelled` is how many Turns it travelled toward its target, which a Recall takes to get home,
 * less in proportion if it has lost Strength since it was sent.
 * `plunder` is the Gold and Research it took from its target and is carrying home.
 * `strength` is its Units' Strength added up, without counters, which set how long it travels;
 * `strengthSent` is its Strength when it was sent, before any losses on the way.
 */
export type Warparty = {
  id: number
  target: string
  units: Record<string, number>
  strength: number
  homeward: boolean
  turnsLeft: number
  turnsTravelled: number
  plunder: { gold: number; research: number }
  strengthSent: number
}
/** A Warparty whose Strength is at least `strength`, but below the next bracket's, travels `turns` Turns. */
export type TravelBracket = { strength: number; turns: number }

/** How many Turns a Warparty with the given Strength takes to reach another Nation. */
export function travelTurns(brackets: TravelBracket[], strength: number): number {
  return brackets.reduce(
    (turns, bracket) => (strength >= bracket.strength ? bracket.turns : turns),
    brackets[0]?.turns ?? 0,
  )
}

/**
 * How many Turns a Warparty would take to get home if Recalled now: as many as it has travelled, less in
 * proportion if its Strength has fallen into a faster bracket since it was sent, rounded up; at least one.
 */
export function recallTurns(brackets: TravelBracket[], warparty: Warparty): number {
  const turns = Math.ceil(
    (warparty.turnsTravelled * travelTurns(brackets, warparty.strength)) / travelTurns(brackets, warparty.strengthSent),
  )
  return Math.max(1, Math.min(warparty.turnsTravelled, turns))
}

/**
 * A Spy sent abroad, told apart by `id`; `turnsLeft` is how many more Turns must resolve before it gets
 * where it is heading, 0 once it is in place at its target, where it stays. `homeward` once its target is
 * out of the Game and it is heading back. `mission` is the name of the Mission it is on, with
 * `missionTurnsLeft` Turns to go, or null while it is Idle.
 */
export type Spy = {
  id: number
  unit: string
  target: string
  homeward: boolean
  turnsLeft: number
  turnsTravelled: number
  mission: string | null
  missionTurnsLeft: number
}
/** The kinds of Fact a Mission may report. */
export type FactKind = 'GOLD' | 'POPULATION' | 'RESEARCH' | 'ARMY' | 'BUILDINGS'
/**
 * What a hostile Mission that succeeds does to the Nation its Spy is at: Defense worn down, non-defensive
 * Buildings Damaged, the share of the Army killed, and Population lost.
 */
export type Harm = { damageDefense: number; damageBuildings: number; armyKilled: number; populationLost: number }
/**
 * What a Spy in place may be set to do, for `turns` Turns. An Intel Mission reports the kind of Fact `reports`
 * when it ends; a hostile one, with `harm`, succeeds with probability `chance` when it ends, and its target is
 * told `text` without being told who sent the Spy.
 */
export type Mission = {
  name: string
  turns: number
  reports: FactKind | null
  chance: number
  harm: Harm | null
  text: string | null
}
/** A kind of Task Scouts can be set to: `LOCATE` searches for a Nation not yet Located until it is Located. */
export type TaskKind = 'LOCATE'
/** What some of the Nation's Scouts are set to do, told apart by `id`; `target` is the Nation it is about. */
export type Task = { id: number; kind: TaskKind; target: string; scouts: number }
/**
 * One choice a Dilemma offers; `worst` is the one that applies if the Player does not choose before the Turn ends.
 * What it leads to is partly luck, and neither its outcomes nor their odds are shown.
 */
export type Choice = { name: string; worst: boolean }
/**
 * A Dilemma this Player may answer, by its `name`, until the Turn ends; `chosen` is the name of the
 * choice they made, or null. The choice made, or else the worst, applies as the Turn ends.
 */
export type Dilemma = {
  name: string
  text: string
  choices: Choice[]
  chosen: string | null
}
/**
 * A Technology in the Tech Tree as this Nation stands with it: `UNLOCKED`, `AVAILABLE` once all its
 * `prerequisites` are unlocked, `LOCKED` while each of them is unlocked or available, otherwise `HIDDEN`.
 * Unlocking it costs `research` and gives access to the Unit and Building types it `unlocks`.
 */
export type TechTreeEntry = KnownTechnology | HiddenTechnology
export type KnownTechnology = {
  name: string
  research: number
  prerequisites: string[]
  unlocks: string[]
  standing: 'UNLOCKED' | 'AVAILABLE' | 'LOCKED'
}
/** A Technology more than one step beyond what the Nation can unlock now, sent with nothing to tell which. */
export type HiddenTechnology = { name: null; research: 0; prerequisites: []; unlocks: []; standing: 'HIDDEN' }
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
  /** How many of each non-defensive Building were Damaged, learned along with its Buildings. */
  damaged: Fact<Record<string, number>> | null
}
/**
 * A Nation on the Map, as another Nation knows it: its name, Ruler, colour and emblem, public from the
 * start, whether it is still in the Game, was conquered, or was removed as its Player was disconnected
 * for too long, and what this Nation has learned of it.
 */
export type MapNation = {
  name: string
  ruler: string
  colour: string
  emblem: string
  standing: 'IN_GAME' | 'CONQUERED' | 'REMOVED'
  facts: Facts
}
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
  /** The Gold Selling one Building of each type would return now, for the types the Nation may Sell one of. */
  sellValues: Record<string, number>
  /** The Unit types the Nation may train: those needing no Technology, or one it has unlocked. */
  availableUnits: UnitType[]
  /** How many of each Unit type the Nation has at home; never its Scouts. */
  army: Record<string, number>
  /** How many Scouts the Nation has roaming the wilds, Idle or on Tasks. */
  scouts: number
  /** How many of its Scouts are on no Task. */
  idleScouts: number
  /** The Tasks its Scouts are set to, in the order set. */
  tasks: Task[]
  /** The Nations it has Located, and so may send Spies and Warparties to, in the order Located. */
  located: string[]
  training: Training[]
  /** The Nation's Warparties abroad, in the order sent. */
  warparties: Warparty[]
  /** The Nation's Spies abroad, in the order sent, each with its Mission and the Turns left on it. */
  spies: Spy[]
  /** The Missions its Spies in place may be set to, in the ruleset's order. */
  missions: Mission[]
  /** How many Turns a Warparty takes to reach another Nation, by its Strength, from the weakest up. */
  travelBrackets: TravelBracket[]
  /** How many Turns a Spy takes to reach another Nation. */
  spyTravelTurns: number
  /** The Gold the Nation's Units cost each Turn, at home and abroad. */
  upkeep: number
  /** The Gold the Nation will gain or lose as this Turn resolves, if nothing but its income, upkeep and Lasting Event changes it. */
  goldForecast: GoldForecast
  /** The Lasting Event active for the Nation, or null if none is. */
  lastingEvent: LastingEvent | null
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

/** The other Nations still in the Game that the Nation has Located, and so may send Spies and Warparties to. */
export function locatedNations(nation: NationView): string[] {
  return otherNations(nation).filter((n) => nation.located.includes(n))
}

/** The other Nations still in the Game that the Nation has neither Located nor set Scouts to Locate. */
export function locatableNations(nation: NationView): string[] {
  const locating = nation.tasks.filter((t) => t.kind === 'LOCATE').map((t) => t.target)
  return otherNations(nation).filter((n) => !nation.located.includes(n) && !locating.includes(n))
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
