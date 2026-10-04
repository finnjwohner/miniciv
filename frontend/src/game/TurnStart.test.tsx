import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NationView, TurnReportSection } from '../api'
import { fakeWebAudio, gesture, timesPlayed, type Tone } from '../test/audio'
import { passes } from '../test/clock'
import { nationView, renderGame, reportLine, roomView, tab, TURN } from '../test/fixtures'
import { prefersReducedMotion } from '../test/motion'

/** The Room and Rome on the next Turn, Rome adjusted by `nation`. */
function nextTurn(nation: Partial<NationView> = {}) {
  const turn = TURN + 1
  return {
    room: roomView({ game: { ...roomView().game!, turn } }),
    nation: nationView({ version: 2, turn, ...nation }),
  }
}

/** The Turn Report of the Turn just resolved, with `netChange` and `sections`. */
function report(
  netChange: Partial<NonNullable<NationView['turnReport']>['netChange']>,
  sections: TurnReportSection[] = [],
) {
  return {
    turn: TURN,
    netChange: { gold: 0, population: 0, research: 0, ...netChange },
    sections,
  }
}

/** The resource labelled `label` in the Resources panel. */
function stat(label: string) {
  return screen.getByText(label, { selector: '.stat__label' }).closest('.stat') as HTMLElement
}

/** The value the resource labelled `label` shows. */
function statValue(label: string) {
  return stat(label).querySelector('.stat__value')?.textContent
}

/**
 * The row whose name reads `name` in the panel titled `panel`, in an open tab or not; the last one, as
 * the Player's own rows sit below what they can construct or train.
 */
function row(panel: string, name: string) {
  const region = screen.getByRole('region', { name: panel, hidden: true })
  return within(region).getAllByText(name, { selector: '.row__name' }).at(-1)!.closest('li') as HTMLElement
}

describe('resources at the start of a Turn', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('counts each resource from before the Turn resolved to its new value, with a floating gain', () => {
    const { rerenderGame } = renderGame()

    rerenderGame(nextTurn({ gold: 134, turnReport: report({ gold: 14 }) }))

    expect(statValue('Gold')).toBe('120')
    expect(within(stat('Gold')).getByText('+14')).toHaveClass('stat__float--good')
    passes(400)
    expect(Number(statValue('Gold'))).toBeGreaterThan(120)
    expect(Number(statValue('Gold'))).toBeLessThan(134)
    passes(1_000)
    expect(statValue('Gold')).toBe('134')
  })

  it('counts down a loss, floating it in the bad tone', () => {
    const { rerenderGame } = renderGame()

    rerenderGame(nextTurn({ research: 7, turnReport: report({ research: -5 }) }))

    expect(statValue('Research')).toBe('12')
    expect(within(stat('Research')).getByText('−5')).toHaveClass('stat__float--bad')
    passes(1_400)
    expect(statValue('Research')).toBe('7')
  })

  it('floats nothing for a resource that did not change', () => {
    const { rerenderGame } = renderGame()

    rerenderGame(nextTurn({ gold: 134, turnReport: report({ gold: 14 }) }))

    expect(stat('Population').querySelector('.stat__float')).toBeNull()
    expect(statValue('Population')).toBe('14')
  })

  it('shows the values as they are when the Game screen first opens', () => {
    renderGame({ nation: nationView({ turnReport: report({ gold: 14 }) }) })

    expect(statValue('Gold')).toBe('120')
    expect(screen.queryByText('+14')).toBeNull()
  })

  it('changes a value at once when spent during the Turn', () => {
    const { rerenderGame } = renderGame()

    rerenderGame({ nation: nationView({ version: 2, gold: 90 }) })

    expect(statValue('Gold')).toBe('90')
    expect(document.querySelector('.stat__float')).toBeNull()
  })

  it('shows the new values at once with reduced motion, with nothing floating', () => {
    prefersReducedMotion(true)
    const { rerenderGame } = renderGame()

    rerenderGame(nextTurn({ gold: 134, turnReport: report({ gold: 14 }) }))

    expect(statValue('Gold')).toBe('134')
    expect(document.querySelector('.stat__float')).toBeNull()
  })
})

describe('rows new this Turn', () => {
  /** Rome on the next Turn with a Library and two Horsemen new, its Archers still in training. */
  function newRows(overrides: Partial<NationView> = {}) {
    return nextTurn({
      buildings: { Farm: 2, Market: 1, Palisade: 1, Library: 1 },
      construction: [{ building: 'Farm', turnsLeft: 2 }],
      army: { Warrior: 3, Horseman: 2 },
      ...overrides,
    })
  }

  it('flashes the rows that appear, not those already there', () => {
    const { rerenderGame } = renderGame()

    rerenderGame(newRows())

    expect(row('Build', 'Library')).toHaveClass('row--fresh')
    expect(row('Build', 'Farm ×1')).toHaveClass('row--fresh')
    expect(row('Army & Warparties', 'Horseman ×2')).toHaveClass('row--fresh')
    expect(row('Build', 'Farm')).not.toHaveClass('row--fresh')
    expect(row('Army & Warparties', 'Warrior ×3')).not.toHaveClass('row--fresh')
    expect(row('Train', 'Archer ×2')).not.toHaveClass('row--fresh')
  })

  it('keeps a row in progress still as its Turns left count down', () => {
    const { rerenderGame } = renderGame()

    rerenderGame(nextTurn({ construction: [{ building: 'Library', turnsLeft: 1 }] }))

    expect(row('Build', 'Library ×1')).not.toHaveClass('row--fresh')
  })

  it('flashes a new row only in the Nation view it first appears in', () => {
    const { rerenderGame } = renderGame()
    rerenderGame(newRows())

    rerenderGame({ ...newRows({ version: 3, gold: 100 }) })

    expect(row('Build', 'Library')).not.toHaveClass('row--fresh')
    expect(row('Army & Warparties', 'Horseman ×2')).not.toHaveClass('row--fresh')
  })

  it('keeps flashing through a re-render of the same Nation view', () => {
    const { rerenderGame } = renderGame()
    const view = newRows()
    rerenderGame(view)

    fireEvent.click(tab('Army'))
    rerenderGame({ ...view, rejection: null })

    expect(row('Army & Warparties', 'Horseman ×2')).toHaveClass('row--fresh')
  })

  it('flashes nothing when the Game screen first opens', () => {
    renderGame()

    expect(document.querySelector('.row--fresh')).toBeNull()
  })

  it('flashes nothing with reduced motion', () => {
    prefersReducedMotion(true)
    const { rerenderGame } = renderGame()

    rerenderGame(newRows())

    expect(document.querySelector('.row--fresh')).toBeNull()
  })
})

describe('sounds at the start of a Turn', () => {
  let tones: Tone[]
  beforeEach(() => {
    tones = fakeWebAudio()
  })

  /** Renders the Game, makes a gesture, and moves to the next Turn with a Turn Report of `sections`. */
  function arriveWith(sections: TurnReportSection[], first?: () => void) {
    const { rerenderGame } = renderGame()
    gesture()
    first?.()
    const view = nextTurn({ turnReport: report({}, sections) })
    rerenderGame(view)
    return () => rerenderGame({ ...view, nation: { ...view.nation, version: 3 } })
  }

  it('plays the new-Turn cue alone for a Turn with nothing completed and no Battle', () => {
    arriveWith([{ kind: 'EVENTS', lines: [reportLine('A good harvest.')] }])

    expect(timesPlayed(tones, 'newTurn')).toBe(1)
    expect(timesPlayed(tones, 'completed')).toBe(0)
    expect(timesPlayed(tones, 'battle')).toBe(0)
  })

  it('plays the completed and Battle cues after the new-Turn cue when the report has those sections', () => {
    arriveWith([
      { kind: 'WARFARE', lines: [reportLine('Your Warparty fought at Egypt.')] },
      { kind: 'COMPLETED', lines: [reportLine('Your Farm has been built.')] },
    ])

    expect(timesPlayed(tones, 'newTurn')).toBe(1)
    expect(timesPlayed(tones, 'completed')).toBe(1)
    expect(timesPlayed(tones, 'battle')).toBe(1)
  })

  it('plays nothing again for a later Nation view of the same Turn', () => {
    const sameTurnAgain = arriveWith([{ kind: 'COMPLETED', lines: [reportLine('Your Farm has been built.')] }])

    sameTurnAgain()

    expect(timesPlayed(tones, 'newTurn')).toBe(1)
    expect(timesPlayed(tones, 'completed')).toBe(1)
  })

  it('plays nothing when the Game screen first opens', () => {
    renderGame()
    gesture()

    expect(tones).toEqual([])
  })

  it('plays nothing with sound off', () => {
    arriveWith(
      [
        { kind: 'WARFARE', lines: [reportLine('Your Warparty fought at Egypt.')] },
        { kind: 'COMPLETED', lines: [reportLine('Your Farm has been built.')] },
      ],
      () => {
        fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
        fireEvent.click(screen.getByRole('checkbox', { name: 'Sound' }))
        tones.length = 0
      },
    )

    expect(tones).toEqual([])
  })
})

describe('the Turn Report at the start of a Turn', () => {
  const sections: TurnReportSection[] = [
    { kind: 'COMPLETED', lines: [reportLine('Your Farm has been built.'), reportLine('Your Market has been built.')] },
    { kind: 'EVENTS', lines: [reportLine('A good harvest.')] },
  ]

  /** What of the Turn Report animates in, in the order shown, each with its delay. */
  function entering() {
    const region = screen.getByRole('region', { name: 'Turn Report' })
    return [...region.querySelectorAll<HTMLElement>('.report__enter')].map((element) => [
      element.textContent,
      element.style.animationDelay,
    ])
  }

  it('brings in the net change, headings and lines one after another as a new Turn arrives', () => {
    const { rerenderGame } = renderGame()

    rerenderGame(nextTurn({ turnReport: report({ gold: 14 }, sections) }))

    expect(entering()).toEqual([
      ['+14 Gold', '0ms'],
      ['Completed', '60ms'],
      ['Your Farm has been built.', '120ms'],
      ['Your Market has been built.', '180ms'],
      ['Events', '240ms'],
      ['A good harvest.', '300ms'],
    ])
  })

  it('brings in nothing with reduced motion', () => {
    prefersReducedMotion(true)
    const { rerenderGame } = renderGame()

    rerenderGame(nextTurn({ turnReport: report({ gold: 14 }, sections) }))

    expect(entering()).toEqual([])
  })

  it('brings in nothing on turning reduced motion off after a Turn has arrived', () => {
    prefersReducedMotion(true)
    const { rerenderGame } = renderGame()
    rerenderGame(nextTurn({ turnReport: report({ gold: 14 }, sections) }))

    fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Reduce motion' }))

    expect(entering()).toEqual([])
  })

  it('brings in nothing when the Game screen first opens, as on joining or reconnecting', () => {
    renderGame({ nation: nationView({ turnReport: report({ gold: 14 }, sections) }) })

    expect(entering()).toEqual([])
  })
})
