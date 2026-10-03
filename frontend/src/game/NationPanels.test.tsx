import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Warparty } from '../api'
import { nationView, renderGame } from '../test/fixtures'

/** Whether `a` comes before `b` in the document. */
function precedes(a: Element, b: Element) {
  return (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
}

/** The names of the buttons in the panel named `name`, in document order. */
function buttonsIn(name: string) {
  return within(screen.getByRole('region', { name }))
    .getAllByRole('button')
    .map((b) => b.getAttribute('aria-label') ?? b.textContent)
}

describe('action panels', () => {
  it('keeps the Build buttons where they are, before the state rows, as construction starts', () => {
    const { rerenderGame } = renderGame({ nation: nationView({ construction: [] }) })
    const before = buttonsIn('Build').filter((b) => b?.startsWith('Build '))

    rerenderGame({ nation: nationView({ version: 2, construction: [{ building: 'Farm', turnsLeft: 2 }] }) })

    const build = screen.getByRole('region', { name: 'Build' })
    expect(buttonsIn('Build').slice(0, before.length)).toEqual(before)
    const lastBuild = within(build).getByRole('button', { name: 'Build Palisade' })
    expect(precedes(lastBuild, within(build).getByText('constructing'))).toBe(true)
    expect(precedes(lastBuild, within(build).getByRole('button', { name: 'Sell Farm' }))).toBe(true)
  })

  it('keeps the Train buttons where they are, before the state rows, as training starts', () => {
    const { rerenderGame } = renderGame({ nation: nationView({ training: [] }) })
    fireEvent.click(screen.getByRole('tab', { name: /^Train/ }))
    const before = buttonsIn('Train')

    rerenderGame({ nation: nationView({ version: 2, training: [{ unit: 'Warrior', count: 1, readyOnTurn: 4 }] }) })

    const train = screen.getByRole('region', { name: 'Train' })
    expect(buttonsIn('Train')).toEqual(before)
    expect(precedes(within(train).getByRole('button', { name: 'Train Scout' }), within(train).getByText('Warrior ×1'))).toBe(
      true,
    )
  })

  it('puts the send-Warparty form before the Army at home and the Warparties abroad', () => {
    const warparty: Warparty = {
      id: 1,
      target: 'Egypt',
      units: { Warrior: 2 },
      strength: 6,
      homeward: false,
      turnsLeft: 1,
      turnsTravelled: 1,
      plunder: { gold: 0, research: 0 },
      strengthSent: 6,
    }
    renderGame({ nation: nationView({ warparties: [warparty] }) })
    fireEvent.click(screen.getByRole('tab', { name: /^Army/ }))

    const army = screen.getByRole('region', { name: 'Army & Warparties' })
    const send = within(army).getByRole('button', { name: 'Send Warparty' })
    expect(precedes(send, within(army).getByText('Warrior ×3'))).toBe(true)
    expect(precedes(send, within(army).getByRole('button', { name: /^Recall/ }))).toBe(true)
  })

  it('puts the send-Spy control before the Spies abroad', () => {
    renderGame({
      nation: nationView({
        army: { Spy: 1 },
        availableUnits: [{ name: 'Spy', gold: 30, population: 1, turns: 2, upkeep: 1, strength: 0, counters: [], scout: false, spy: true }],
        spies: [{ id: 2, unit: 'Spy', target: 'Egypt', homeward: false, turnsLeft: 0, turnsTravelled: 2 }],
      }),
    })
    fireEvent.click(screen.getByRole('tab', { name: /^Spies/ }))

    const spies = screen.getByRole('region', { name: 'Spies' })
    expect(precedes(within(spies).getByRole('button', { name: 'Send Spy' }), within(spies).getByText(/in place at/))).toBe(true)
  })
})
