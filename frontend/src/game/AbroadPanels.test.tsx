import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame, spy, tab, unit, warparty } from '../test/fixtures'
import { precedes } from '../test/order'

describe('ArmyPanel', () => {
  it('puts the send-Warparty form before the Army at home and the Warparties abroad', () => {
    renderGame({ nation: nationView({ warparties: [warparty()] }) })
    fireEvent.click(tab('Army'))

    const army = screen.getByRole('region', { name: 'Army & Warparties' })
    const send = within(army).getByRole('button', { name: 'Send Warparty' })
    expect(precedes(send, within(army).getByText('Warrior ×3'))).toBe(true)
    expect(precedes(send, within(army).getByRole('button', { name: /^Recall/ }))).toBe(true)
  })
})

describe('SpiesPanel', () => {
  it('puts the send-Spy control before the Spies abroad', () => {
    renderGame({
      nation: nationView({
        army: { Spy: 1 },
        availableUnits: [unit({ name: 'Spy', gold: 30, population: 1, turns: 2, upkeep: 1, strength: 0, spy: true })],
        spies: [spy()],
      }),
    })
    fireEvent.click(tab('Spies'))

    const spies = screen.getByRole('region', { name: 'Spies' })
    expect(precedes(within(spies).getByRole('button', { name: 'Send Spy' }), within(spies).getByText(/in place at/))).toBe(true)
  })
})
