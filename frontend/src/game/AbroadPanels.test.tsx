import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame, spy, tab, unit, warparty } from '../test/fixtures'
import { precedes, rowNamed } from '../test/order'

describe('ArmyPanel', () => {
  it('puts the send-Warparty form before the Army at home and the Warparties abroad', () => {
    renderGame({ nation: nationView({ warparties: [warparty()] }) })
    fireEvent.click(tab('Army'))

    const army = screen.getByRole('region', { name: 'Army & Warparties' })
    const send = within(army).getByRole('button', { name: 'Send Warparty' })
    expect(precedes(send, rowNamed(army, 'Warrior ×3'))).toBe(true)
    expect(precedes(send, within(army).getByRole('button', { name: /^Recall/ }))).toBe(true)
  })

  it('runs a Warparty slider per Unit type from 0 to the number owned', () => {
    renderGame({ nation: nationView({ army: { Warrior: 3, Archer: 2 } }) })
    fireEvent.click(tab('Army'))

    const warriors = screen.getByRole('slider', { name: 'How many Warrior to send' })
    expect(warriors).toHaveAttribute('min', '0')
    expect(warriors).toHaveAttribute('max', '3')
    expect(warriors).toHaveValue('0')
    expect(screen.getByRole('slider', { name: 'How many Archer to send' })).toHaveAttribute('max', '2')
  })

  it('keeps the Warparty slider for a single Unit, so 0 stays selectable', () => {
    renderGame({ nation: nationView({ army: { Warrior: 1 } }) })
    fireEvent.click(tab('Army'))

    const warriors = screen.getByRole('slider', { name: 'How many Warrior to send' })
    expect(warriors).toHaveAttribute('min', '0')
    expect(warriors).toHaveAttribute('max', '1')
    expect(warriors).toHaveValue('0')
  })

  it('shows no travel brackets, keeping the arrival and Recall Turns', () => {
    renderGame({ nation: nationView({ warparties: [warparty()] }) })
    fireEvent.click(tab('Army'))

    const army = screen.getByRole('region', { name: 'Army & Warparties' })
    expect(within(army).queryByText(/Turns to arrive by Strength/)).not.toBeInTheDocument()
    fireEvent.change(within(army).getByRole('slider', { name: 'How many Warrior to send' }), { target: { value: '1' } })
    expect(within(army).getByText(/arrives in 1 Turn/)).toBeInTheDocument()
    expect(within(army).getByRole('button', { name: /^Recall \(home in/ })).toBeInTheDocument()
  })
})

describe('SpiesPanel', () => {
  it('shows Scouts roaming the wilds under Spies & Scouts, not under the Army', () => {
    renderGame({ nation: nationView({ scouts: 3 }) })

    fireEvent.click(tab('Spies & Scouts'))
    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(within(spies).getByText('Scouts roaming the wilds ×3')).toBeInTheDocument()
    expect(within(spies).getByText(/discover Warparties abroad/)).toBeInTheDocument()

    fireEvent.click(tab('Army'))
    const army = screen.getByRole('region', { name: 'Army & Warparties' })
    expect(within(army).queryByText(/Scout/)).not.toBeInTheDocument()
    expect(within(army).getByText('Total Strength').nextSibling).toHaveTextContent('9')
  })

  it('puts the send-Spy control before the Spies abroad', () => {
    renderGame({
      nation: nationView({
        army: { Spy: 1 },
        availableUnits: [
          unit({
            name: 'Spy',
            gold: 30,
            population: 1,
            turns: 2,
            upkeep: 1,
            strength: 0,
            spy: true,
          }),
        ],
        spies: [spy()],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(
      precedes(within(spies).getByRole('button', { name: 'Send Spy' }), within(spies).getByText(/in place at/)),
    ).toBe(true)
  })

  it('shows Spies in place at the same Nation as one row with a count', () => {
    renderGame({
      nation: nationView({
        spies: [spy({ id: 1 }), spy({ id: 2 }), spy({ id: 3 })],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    const rows = within(spies).getAllByRole('listitem')
    expect(rows).toHaveLength(1)
    expect(rows[0]).toHaveTextContent('Spy ×3')
    expect(rows[0]).toHaveTextContent('in place at Egypt')
  })

  it('keeps Spies to the same Nation with different Turns left as separate rows', () => {
    renderGame({
      nation: nationView({
        spies: [spy({ id: 1, turnsLeft: 1 }), spy({ id: 2, turnsLeft: 2 }), spy({ id: 3, turnsLeft: 2 })],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const rows = within(screen.getByRole('region', { name: 'Spies & Scouts' })).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Spy ×1')
    expect(rows[0]).toHaveTextContent('1 Turn left')
    expect(rows[1]).toHaveTextContent('Spy ×2')
    expect(rows[1]).toHaveTextContent('2 Turns left')
  })

  it('says Spies at home are running counter-intelligence', () => {
    renderGame({
      nation: nationView({
        army: { Spy: 2 },
        availableUnits: [
          unit({
            name: 'Spy',
            gold: 30,
            population: 1,
            turns: 2,
            upkeep: 1,
            strength: 0,
            spy: true,
          }),
        ],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(within(spies).getByText('2 on counter-intelligence')).toBeInTheDocument()
    expect(within(spies).getByText(/running counter-intelligence/)).toBeInTheDocument()
  })
})
