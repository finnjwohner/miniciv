import { fireEvent, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { GameView } from '../api'
import { fakeWebAudio, gesture, timesPlayed, type Tone } from '../test/audio'
import { passes } from '../test/clock'
import { MY_PLAYER_ID, renderGame, roomView } from '../test/fixtures'
import { prefersReducedMotion } from '../test/motion'

/** The Room with `millisLeft` of the Turn left, adjusted by `game`. */
function roomWithTimeLeft(millisLeft: number, game: Partial<GameView> = {}) {
  return roomView({ game: { ...roomView().game!, millisLeft, ...game } })
}

/** The Room with the tests' Player done with the Turn and `millisLeft` of it left. */
function roomDoneWithTimeLeft(millisLeft: number) {
  const room = roomWithTimeLeft(millisLeft)
  return { ...room, players: room.players.map((p) => (p.id === MY_PLAYER_ID ? { ...p, done: true } : p)) }
}

/** The End Turn button, under either of its labels. */
function endTurn() {
  return screen.getByRole('button', { name: /End Turn|Waiting for the others/ })
}

/** Turns sound off in the settings pop-up. */
function turnSoundOff() {
  fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
  fireEvent.click(screen.getByRole('checkbox', { name: 'Sound' }))
}

describe('time running out', () => {
  let tones: Tone[]
  beforeEach(() => {
    vi.useFakeTimers()
    tones = fakeWebAudio()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('pulses End Turn and ticks each second in the last 10 seconds', () => {
    renderGame({ room: roomWithTimeLeft(12_000) })
    gesture()

    passes(1_900)
    expect(endTurn()).not.toHaveClass('turn__end--pulse')
    expect(timesPlayed(tones, 'tick')).toBe(0)

    passes(100)
    expect(endTurn()).toHaveClass('turn__end--pulse')
    expect(timesPlayed(tones, 'tick')).toBe(1)

    passes(3_000)
    expect(timesPlayed(tones, 'tick')).toBe(4)
  })

  it('stays still and quiet with more than 10 seconds left', () => {
    renderGame({ room: roomWithTimeLeft(60_000) })
    gesture()

    passes(5_000)

    expect(endTurn()).not.toHaveClass('turn__end--pulse')
    expect(timesPlayed(tones, 'tick')).toBe(0)
  })

  it('stops once the Player has ended their Turn', () => {
    const { rerenderGame } = renderGame({ room: roomWithTimeLeft(8_000) })
    gesture()
    passes(1_000)
    expect(timesPlayed(tones, 'tick')).toBe(1)

    rerenderGame({ room: roomDoneWithTimeLeft(7_000) })
    passes(3_000)

    expect(endTurn()).not.toHaveClass('turn__end--pulse')
    expect(timesPlayed(tones, 'tick')).toBe(1)
  })

  it('stays still and quiet while the Game is Paused', () => {
    renderGame({ room: roomWithTimeLeft(8_000, { waitingFor: [{ nation: 'Egypt', millisLeft: 60_000 }] }) })
    gesture()

    passes(3_000)

    expect(endTurn()).not.toHaveClass('turn__end--pulse')
    expect(timesPlayed(tones, 'tick')).toBe(0)
  })

  it('keeps End Turn still with reduced motion, still ticking', () => {
    prefersReducedMotion(true)
    renderGame({ room: roomWithTimeLeft(8_000) })
    gesture()

    passes(1_000)

    expect(endTurn()).not.toHaveClass('turn__end--pulse')
    expect(timesPlayed(tones, 'tick')).toBe(1)
  })

  it('makes no tick with sound off, still pulsing', () => {
    renderGame({ room: roomWithTimeLeft(8_000) })
    turnSoundOff()

    passes(3_000)

    expect(endTurn()).toHaveClass('turn__end--pulse')
    expect(timesPlayed(tones, 'tick')).toBe(0)
  })
})

describe('rejected action', () => {
  /**
   * Trains a Warrior, which the server is about to reject, after `first` if given, and returns a way to
   * send the rejection or clear it, as the next action does.
   */
  function trainAWarrior(first?: () => void) {
    const { rerenderGame } = renderGame()
    first?.()
    fireEvent.click(screen.getByRole('tab', { name: /Train/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Train Warrior' }))
    return (rejection: string | null = 'Not enough Population.') => rerenderGame({ rejection })
  }

  /** The Train panel, open or not. */
  function trainPanel() {
    return screen.getByRole('region', { name: 'Train' })
  }

  it('shakes the panel where it was rejected and plays the rejection cue once', () => {
    const tones = fakeWebAudio()
    const reject = trainAWarrior()

    reject()
    reject()

    expect(trainPanel()).toHaveClass('panel--shake')
    expect(screen.getByRole('region', { name: 'Build', hidden: true })).not.toHaveClass('panel--shake')
    expect(timesPlayed(tones, 'rejection')).toBe(1)
  })

  it('stops shaking as the next action clears it, and shakes again for the next rejection', () => {
    const tones = fakeWebAudio()
    const reject = trainAWarrior()
    reject()

    reject(null)
    expect(trainPanel()).not.toHaveClass('panel--shake')

    reject()
    expect(trainPanel()).toHaveClass('panel--shake')
    expect(timesPlayed(tones, 'rejection')).toBe(2)
  })

  it('does not shake with reduced motion, still playing the cue', () => {
    prefersReducedMotion(true)
    const tones = fakeWebAudio()
    const reject = trainAWarrior()

    reject()

    expect(trainPanel()).not.toHaveClass('panel--shake')
    expect(timesPlayed(tones, 'rejection')).toBe(1)
  })

  it('plays no cue with sound off, still shaking', () => {
    const tones = fakeWebAudio()
    const reject = trainAWarrior(turnSoundOff)

    reject()

    expect(trainPanel()).toHaveClass('panel--shake')
    expect(timesPlayed(tones, 'rejection')).toBe(0)
  })
})
