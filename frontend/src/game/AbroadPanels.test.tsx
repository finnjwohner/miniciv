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

  it('shows each Spy in place as its own row, Idle or with its Mission and the Turns left on it', () => {
    renderGame({
      nation: nationView({
        spies: [spy({ id: 1 }), spy({ id: 2, mission: 'Report Gold', missionTurnsLeft: 1 })],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    const rows = within(spies).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('in place at Egypt, Idle')
    expect(rows[1]).toHaveTextContent('in place at Egypt, on Report Gold, 1 Turn left')
  })

  it('offers a Mission picker per Spy in place, showing its current Mission', () => {
    renderGame({
      nation: nationView({
        spies: [spy({ id: 1 }), spy({ id: 2, mission: 'Report Army', missionTurnsLeft: 2 }), spy({ id: 3, turnsLeft: 1 })],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    const idle = within(spies).getByRole('combobox', { name: 'Mission for Spy 1 in Egypt' })
    expect(options(idle)).toEqual([
      'Choose a Mission',
      'Report Gold (2 Turns)',
      'Report Population (2 Turns)',
      'Report Research (2 Turns)',
      'Report Army (2 Turns)',
      'Report Buildings (2 Turns)',
    ])
    expect(idle).toHaveValue('')
    expect(within(spies).getByRole('combobox', { name: 'Mission for Spy 2 in Egypt' })).toHaveValue('Report Army')
    expect(within(spies).queryByRole('combobox', { name: 'Mission for Spy 3 in Egypt' })).not.toBeInTheDocument()
  })

  it('offers hostile Missions apart from Intel ones, with their chance of success', () => {
    renderGame({
      nation: nationView({
        spies: [spy({ id: 1 })],
        missions: [
          { name: 'Report Gold', turns: 2, reports: 'GOLD', chance: 0, harm: null, steals: null, text: null },
          {
            name: 'Sabotage Buildings',
            turns: 4,
            reports: null,
            chance: 0.6,
            harm: { damageDefense: 0, damageBuildings: 1, armyKilled: 0, populationLost: 0 },
            steals: null,
            text: 'Saboteurs struck in the night',
          },
          {
            name: 'Steal Gold',
            turns: 3,
            reports: null,
            chance: 0.6,
            harm: null,
            steals: 'GOLD',
            text: 'Thieves robbed your treasury',
          },
        ],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const picker = screen.getByRole('combobox', { name: 'Mission for Spy 1 in Egypt' })
    expect(options(within(picker).getByRole('group', { name: 'Intel' }))).toEqual(['Report Gold (2 Turns)'])
    expect(options(within(picker).getByRole('group', { name: 'Hostile' }))).toEqual([
      'Sabotage Buildings (4 Turns, 60% chance)',
      'Steal Gold (3 Turns, 60% chance)',
    ])
  })

  it('shows an Escaping Spy without a Mission picker', () => {
    renderGame({ nation: nationView({ spies: [spy({ id: 1, mission: 'Steal Gold', escaping: true })] }) })
    fireEvent.click(tab('Spies & Scouts'))

    const spies = screen.getByRole('region', { name: 'Spies & Scouts' })
    expect(within(spies).getByRole('listitem')).toHaveTextContent(
      'in place at Egypt, Escaping after Steal Gold; it takes its Haul as this Turn ends',
    )
    expect(within(spies).queryByRole('combobox', { name: 'Mission for Spy 1 in Egypt' })).not.toBeInTheDocument()
  })

  it('shows the Haul a Spy heading home carries', () => {
    renderGame({
      nation: nationView({
        spies: [
          spy({ id: 1, homeward: true, turnsLeft: 3, haul: { resource: 'GOLD', amount: 12 } }),
          spy({ id: 2, homeward: true, turnsLeft: 3, haul: { resource: 'RESEARCH', amount: 4 } }),
        ],
      }),
    })
    fireEvent.click(tab('Spies & Scouts'))

    const rows = within(screen.getByRole('region', { name: 'Spies & Scouts' })).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('heading home from Egypt carrying 12 Gold, 3 Turns left')
    expect(rows[1]).toHaveTextContent('heading home from Egypt carrying 4 Research, 3 Turns left')
  })

  it('sets a Spy’s Mission when one is picked', () => {
    const onSetMission = vi.fn()
    renderGame({ nation: nationView({ spies: [spy({ id: 1 }), spy({ id: 2, mission: 'Report Gold', missionTurnsLeft: 1 })] }), onSetMission })
    fireEvent.click(tab('Spies & Scouts'))

    fireEvent.change(screen.getByRole('combobox', { name: 'Mission for Spy 2 in Egypt' }), {
      target: { value: 'Report Research' },
    })

    expect(onSetMission).toHaveBeenCalledWith(2, 'Report Research')
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
