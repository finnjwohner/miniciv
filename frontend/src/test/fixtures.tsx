// A Game in progress, as the server would send it, for tests to render and adjust.
import { render } from '@testing-library/react'
import type { ComponentProps } from 'react'
import type { BuildingType, Facts, GameView, NationView, PlayerView, UnitType } from '../api'
import GameScreen from '../game/GameScreen'
import type { ReceivedRoom } from '../useRoom'

/** The id of the Player the tests play as, by default: Rome's. */
export const MY_PLAYER_ID = 'player-rome'

/**
 * The Turn the Game is on. A Nation view and its Game view each carry the Turn; to test another,
 * override both.
 */
export const TURN = 3

/** Each Nation's name, Ruler, colour and emblem, as its Player picked them in the Room. */
const ROME = { name: 'Rome', ruler: 'Augustus', colour: '#c0392b', emblem: 'sword' }
const EGYPT = { name: 'Egypt', ruler: 'Cleopatra', colour: '#2980b9', emblem: 'sun' }

/** The tests' Player, playing Rome, and Egypt's Player, both still deciding. */
function playerViews(): PlayerView[] {
  return [
    { id: MY_PLAYER_ID, ...player(ROME) },
    { id: 'player-egypt', ...player(EGYPT) },
  ]
}

/** The Turn of a Game between Rome and Egypt, with a minute left and no one disconnected. */
function gameView(overrides: Partial<GameView> = {}): GameView {
  return {
    turn: TURN,
    nations: [ROME.name, EGYPT.name],
    millisLeft: 60_000,
    conquered: [],
    removed: [],
    winner: null,
    waitingFor: [],
    ...overrides,
  }
}

/**
 * The Room the Game is played in, as just received; pass `game` to adjust it. The Turn timer counts
 * down from `receivedAt`, the time now, so a test checking it should fake the clock.
 */
export function roomView(overrides: Partial<ReceivedRoom> = {}): ReceivedRoom {
  return {
    code: 'ABCD',
    version: 1,
    colours: [ROME.colour, EGYPT.colour, '#27ae60', '#f39c12'],
    emblems: [ROME.emblem, EGYPT.emblem, 'crown', 'tree'],
    players: playerViews(),
    game: gameView(),
    receivedAt: Date.now(),
    ...overrides,
  }
}

/**
 * Rome on the Game's Turn: a few Buildings, a Library under construction, Archers in training, a Dilemma
 * awaiting a choice and a Turn Report from the last Turn.
 */
export function nationView(overrides: Partial<NationView> = {}): NationView {
  return {
    version: 1,
    turn: TURN,
    nation: ROME.name,
    gold: 120,
    population: 14,
    research: 12,
    map: [
      { ...ROME, standing: 'IN_GAME', facts: noFacts() },
      { ...EGYPT, standing: 'IN_GAME', facts: noFacts() },
    ],
    availableBuildings: [
      building({ name: 'Farm', gold: 30, turns: 2, populationPerTurn: 1 }),
      building({ name: 'Market', gold: 40, turns: 2, goldPerTurn: 3 }),
      building({ name: 'Library', gold: 50, turns: 3, researchPerTurn: 2 }),
      building({ name: 'Palisade', gold: 40, turns: 2, defense: 6 }),
    ],
    buildings: { Farm: 2, Market: 1, Palisade: 1 },
    construction: [{ building: 'Library', turnsLeft: 2 }],
    repairs: [],
    defense: 6,
    damage: [],
    sellValues: { Farm: 15, Market: 20, Palisade: 20 },
    availableUnits: [
      unit({ name: 'Warrior', gold: 20, population: 2, turns: 1, upkeep: 1, strength: 3, counters: ['Horseman'] }),
      unit({ name: 'Archer', gold: 25, population: 2, turns: 2, upkeep: 1, strength: 3, counters: ['Warrior'] }),
      unit({ name: 'Scout', gold: 15, population: 1, turns: 1, upkeep: 1, strength: 0, scout: true }),
    ],
    army: { Warrior: 3 },
    training: [{ unit: 'Archer', count: 2, readyOnTurn: 5 }],
    warparties: [],
    spies: [],
    travelBrackets: [
      { strength: 0, turns: 1 },
      { strength: 10, turns: 2 },
      { strength: 25, turns: 3 },
    ],
    spyTravelTurns: 2,
    upkeep: 3,
    arrivals: [],
    tributes: [],
    encounters: [],
    techTree: [
      { name: 'Bronze Working', research: 10, prerequisites: [], unlocks: ['Archer'], standing: 'UNLOCKED' },
      { name: 'Writing', research: 15, prerequisites: [], unlocks: ['Spy', 'Library'], standing: 'UNLOCKED' },
      { name: 'Iron Working', research: 25, prerequisites: ['Bronze Working'], unlocks: ['Horseman'], standing: 'AVAILABLE' },
    ],
    turnReport: {
      turn: 2,
      sections: [
        { kind: 'COMPLETED', lines: ['Your Farm has been built.'] },
        { kind: 'EVENTS', lines: ['A good harvest filled your granaries: your Nation gained 4 Population.'] },
      ],
    },
    dilemmas: [
      {
        name: 'Flooded river',
        text: 'The river has burst its banks near your fields',
        choices: [
          { name: 'Build levees', worst: false },
          { name: 'Let it flood', worst: true },
        ],
        chosen: null,
      },
    ],
    conqueredBy: null,
    winner: null,
    ...overrides,
  }
}

/**
 * Renders the Game screen as the Player `playerId` (Rome's by default), with every action a no-op
 * unless passed in `props`. `rerenderGame` re-renders it with other props, as when the server sends a new
 * Nation view or a rejection, keeping what the Player has open.
 */
export function renderGame(props: Partial<ComponentProps<typeof GameScreen>> = {}) {
  const noop = () => {}
  const gameScreen = (overrides: Partial<ComponentProps<typeof GameScreen>>) => (
    <GameScreen
      room={roomView()}
      nation={nationView()}
      playerId={MY_PLAYER_ID}
      rejection={null}
      onStartBuilding={noop}
      onRepairBuilding={noop}
      onRebuildBuilding={noop}
      onSellBuilding={noop}
      onTrainUnits={noop}
      onSendWarparty={noop}
      onRecallWarparty={noop}
      onSendSpy={noop}
      onAnswerDilemma={noop}
      onUnlockTechnology={noop}
      onOfferTribute={noop}
      onAnswerTribute={noop}
      onAnswerEncounter={noop}
      onDone={noop}
      {...overrides}
    />
  )
  const result = render(gameScreen(props))
  return { ...result, rerenderGame: (overrides: Partial<ComponentProps<typeof GameScreen>>) => result.rerender(gameScreen(overrides)) }
}

function player(nation: typeof ROME): Omit<PlayerView, 'id'> {
  return { nationName: nation.name, ruler: nation.ruler, colour: nation.colour, emblem: nation.emblem, ready: true, done: false }
}

function noFacts(): Facts {
  return { gold: null, population: null, research: null, army: null, buildings: null, defense: null }
}

function building(type: Pick<BuildingType, 'name' | 'gold' | 'turns'> & Partial<BuildingType>): BuildingType {
  return { populationPerTurn: 0, goldPerTurn: 0, goldPerPopulation: 0, researchPerTurn: 0, defense: 0, ...type }
}

function unit(type: Omit<UnitType, 'counters' | 'scout' | 'spy'> & Partial<UnitType>): UnitType {
  return { counters: [], scout: false, spy: false, ...type }
}
