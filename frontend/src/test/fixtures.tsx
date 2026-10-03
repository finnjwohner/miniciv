// A Game in progress, as the server would send it, for tests to render and adjust.
import { render } from '@testing-library/react'
import type { ComponentProps } from 'react'
import type { BuildingType, Facts, GameView, NationView, PlayerView, UnitType } from '../api'
import GameScreen from '../game/GameScreen'
import type { ReceivedRoom } from '../useRoom'

/** The Player the tests play as, by default. */
export const ME = 'player-rome'

/** Rome (the tests' Player) and Egypt, both still deciding. */
export function playerViews(): PlayerView[] {
  return [
    {
      id: ME,
      nationName: 'Rome',
      ruler: 'Augustus',
      colour: '#c0392b',
      emblem: 'sword',
      ready: true,
      done: false,
    },
    {
      id: 'player-egypt',
      nationName: 'Egypt',
      ruler: 'Cleopatra',
      colour: '#2980b9',
      emblem: 'sun',
      ready: true,
      done: false,
    },
  ]
}

/** Turn 3 of a Game between Rome and Egypt, with a minute left and no one disconnected. */
export function gameView(overrides: Partial<GameView> = {}): GameView {
  return {
    turn: 3,
    nations: ['Rome', 'Egypt'],
    millisLeft: 60_000,
    conquered: [],
    removed: [],
    winner: null,
    waitingFor: [],
    ...overrides,
  }
}

/** The Room the Game is played in, as just received; pass `game` to adjust it. */
export function roomView(overrides: Partial<ReceivedRoom> = {}): ReceivedRoom {
  return {
    code: 'ABCD',
    version: 1,
    colours: ['#c0392b', '#2980b9', '#27ae60', '#f39c12'],
    emblems: ['sword', 'sun', 'crown', 'tree'],
    players: playerViews(),
    game: gameView(),
    receivedAt: Date.now(),
    ...overrides,
  }
}

/**
 * Rome on Turn 3: a few Buildings, a Library under construction, Archers in training, a Dilemma
 * awaiting a choice and a Turn Report from the last Turn.
 */
export function nationView(overrides: Partial<NationView> = {}): NationView {
  return {
    version: 1,
    turn: 3,
    nation: 'Rome',
    gold: 120,
    population: 14,
    research: 12,
    map: [
      { name: 'Rome', ruler: 'Augustus', colour: '#c0392b', emblem: 'sword', standing: 'IN_GAME', facts: noFacts() },
      { name: 'Egypt', ruler: 'Cleopatra', colour: '#2980b9', emblem: 'sun', standing: 'IN_GAME', facts: noFacts() },
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
    turnReport: { turn: 2, lines: ['Your Farm was completed.', 'A good harvest filled your granaries (+4 Population).'] },
    dilemmas: [
      {
        name: 'Flooded river',
        text: 'The river has burst its banks near your fields.',
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
 * Renders the Game screen as the Player `playerId` (Rome by default), with every action a no-op
 * unless passed in `props`.
 */
export function renderGame(props: Partial<ComponentProps<typeof GameScreen>> = {}) {
  const noop = () => {}
  return render(
    <GameScreen
      room={roomView()}
      nation={nationView()}
      playerId={ME}
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
      {...props}
    />,
  )
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
