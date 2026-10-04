import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { nationView, onMap, renderGame, spy, tab, unit, warparty } from '../test/fixtures'
import { precedes, rowNamed } from '../test/order'

/** Rome's view with Carthage and Persia on the Map too, having Located only the given Nations. */
function locating(located: string[], overrides: Parameters<typeof nationView>[0] = {}) {
  const view = nationView(overrides)
  return { ...view, map: [...view.map, onMap('Carthage'), onMap('Persia')], located }
}

const SPY_TYPE = unit({ name: 'Spy', gold: 30, population: 1, turns: 2, upkeep: 1, strength: 0, spy: true })

/** The names of the options a select offers. */
function options(select: HTMLElement) {
  return within(select)
    .getAllByRole('option')
    .map((o) => o.textContent)
}

describe('ArmyPanel', () => {
  it('offers only Located Nations as Warparty targets', () => {
    renderGame({ nation: locating(['Persia', 'Egypt']) })
    fireEvent.click(tab('Army'))

    const army = screen.getByRole('region', { name: 'Army & Warparties' })
    expect(options(within(army).getByRole('combobox', { name: 'To' }))).toEqual(['Egypt', 'Persia'])
  })

  it('says to Locate a Nation first when none is Located', () => {
    renderGame({ nation: locating([]) })
    fireEvent.click(tab('Army'))

    const army = screen.getByRole('region', { name: 'Army & Warparties' })
    expect(within(army).getByText(/You have not Located any Nation yet/)).toBeInTheDocument()
    expect(within(army).queryByRole('button', { name: 'Send Warparty' })).not.toBeInTheDocument()
  })

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
  it('offers only Located Nations as Spy targets', () => {
    renderGame({ nation: locating(['Carthage'], { army: { Spy: 1 }, availableUnits: [SPY_TYPE] }) })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(options(within(spies).getByRole('combobox', { name: 'Send a Spy to' }))).toEqual(['Carthage'])
  })

  it('says to Locate a Nation before sending a Spy when none is Located', () => {
    renderGame({ nation: locating([], { army: { Spy: 1 }, availableUnits: [SPY_TYPE] }) })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(within(spies).getByText(/You have not Located any Nation yet/)).toBeInTheDocument()
    expect(within(spies).queryByRole('button', { name: 'Send Spy' })).not.toBeInTheDocument()
  })

  it('shows the Idle Scouts and each Task with its Scouts', () => {
    renderGame({
      nation: locating(['Egypt'], {
        scouts: 5,
        idleScouts: 2,
        tasks: [{ id: 4, kind: 'LOCATE', target: 'Carthage', scouts: 3 }],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(within(spies).getByText('Idle Scouts ×2')).toBeInTheDocument()
    expect(rowNamed(spies, 'Locating Carthage').closest('li')).toHaveTextContent('3 Scouts')
  })

  it('cancels a Task', () => {
    const onCancelTask = vi.fn()
    renderGame({
      nation: locating(['Egypt'], {
        scouts: 3,
        idleScouts: 0,
        tasks: [{ id: 4, kind: 'LOCATE', target: 'Carthage', scouts: 3 }],
      }),
      onCancelTask,
    })
    fireEvent.click(tab('Spies & Scouts'))

    fireEvent.click(screen.getByRole('button', { name: 'Cancel Locating Carthage' }))

    expect(onCancelTask).toHaveBeenCalledWith(4)
  })

  it('sets Idle Scouts to Locate a Nation not yet Located, nor being Located, still in the Game', () => {
    const onSetTask = vi.fn()
    const view = locating(['Egypt'], {
      scouts: 4,
      idleScouts: 3,
      tasks: [{ id: 1, kind: 'LOCATE', target: 'Persia', scouts: 1 }],
    })
    renderGame({ nation: { ...view, map: [...view.map, onMap('Gaul', 'CONQUERED'), onMap('Sparta')] }, onSetTask })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    const target = within(spies).getByRole('combobox', { name: 'Locate' })
    expect(options(target)).toEqual(['Carthage', 'Sparta'])
    fireEvent.change(target, { target: { value: 'Sparta' } })
    const count = within(spies).getByRole('slider', { name: 'How many Scouts to set to the Task' })
    expect(count).toHaveAttribute('min', '1')
    expect(count).toHaveAttribute('max', '3')
    fireEvent.change(count, { target: { value: '2' } })
    fireEvent.click(within(spies).getByRole('button', { name: 'Set Task' }))

    expect(onSetTask).toHaveBeenCalledWith('LOCATE', 'Sparta', 2)
  })

  it('offers no Task without Idle Scouts', () => {
    renderGame({
      nation: locating([], { scouts: 1, idleScouts: 0, tasks: [{ id: 1, kind: 'LOCATE', target: 'Persia', scouts: 1 }] }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(within(spies).queryByRole('button', { name: 'Set Task' })).not.toBeInTheDocument()
  })

  it('shows Scouts roaming the wilds under Spies & Scouts, not under the Army', () => {
    renderGame({ nation: nationView({ scouts: 3, idleScouts: 3 }) })

    fireEvent.click(tab('Spies & Scouts'))
    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(within(spies).getByText('Idle Scouts ×3')).toBeInTheDocument()
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

describe('MapPanel', () => {
  it('tags the Nations the Player has Located', () => {
    renderGame({ nation: locating(['Carthage']) })

    const map = screen.getByRole('region', { name: 'Map' })
    expect(within(map).getByRole('heading', { name: /Carthage/ })).toHaveTextContent('located')
    expect(within(map).getByRole('heading', { name: /Persia/ })).not.toHaveTextContent('located')
  })

  /** The Facts shown on the Map for the Nation `name`, label to value. */
  function factsOf(name: string) {
    const card = within(screen.getByRole('region', { name: 'Map' }))
      .getAllByRole('listitem')
      .find((item) => within(item).queryByRole('heading', { name: new RegExp(name) }))!
    const terms = within(card).getAllByRole('term')
    return new Map(terms.map((t) => [t.textContent, t.nextElementSibling!.textContent]))
  }

  it('shows which of another Nation’s Buildings a Spy reported Damaged', () => {
    const view = nationView()
    renderGame({
      nation: {
        ...view,
        map: view.map.map((other) =>
          other.name === 'Egypt'
            ? {
                ...other,
                facts: {
                  ...other.facts,
                  buildings: { value: { Farm: 2, Market: 1 }, turn: 3 },
                  defense: { value: 0, turn: 3 },
                  damaged: { value: { Farm: 1 }, turn: 3 },
                },
              }
            : other,
        ),
      },
    })

    expect(factsOf('Egypt').get('Buildings')).toBe('Farm ×2, Market ×1 · Turn 3')
    expect(factsOf('Egypt').get('Damaged')).toBe('Farm ×1 · Turn 3')
  })

  it('shows its own Damaged Buildings, counting those being Repaired', () => {
    renderGame({
      nation: nationView({
        damage: [
          {
            building: 'Farm',
            damaged: 1,
            defenseLost: null,
            repairGold: 15,
            repairTurns: 1,
            destroyed: null,
            rebuildGold: null,
            rebuildTurns: null,
          },
        ],
        repairs: [{ building: 'Farm', rebuild: false, defense: 0, turnsLeft: 1 }],
      }),
    })

    expect(factsOf('Rome').get('Damaged')).toBe('Farm ×2')
  })
})
