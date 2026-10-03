import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Spy, Warparty } from '../api'
import { MY_PLAYER_ID, nationView, renderGame, roomView, TURN } from '../test/fixtures'

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

const spy: Spy = { id: 2, unit: 'Spy', target: 'Egypt', homeward: false, turnsLeft: 0, turnsTravelled: 2 }

/** The tab named `name`, whatever its badge says. */
function tab(name: string) {
  return screen.getByRole('tab', { name: new RegExp(`^${name}`) })
}

describe('ActionTabs', () => {
  it('badges each tab with what is going on in it', () => {
    renderGame({
      nation: nationView({
        research: 30,
        repairs: [{ building: 'Palisade', rebuild: false, defense: 2, turnsLeft: 1 }],
        training: [
          { unit: 'Archer', count: 2, readyOnTurn: 5 },
          { unit: 'Warrior', count: 1, readyOnTurn: 4 },
        ],
        warparties: [warparty, { ...warparty, id: 3 }],
        spies: [spy],
      }),
    })

    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Build2', 'Train3', 'Tech1', 'Army2', 'Spies1'])
  })

  it('shows no badge on a tab with nothing going on', () => {
    renderGame({ nation: nationView({ construction: [], training: [] }) })

    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Build', 'Train', 'Tech', 'Army', 'Spies'])
  })

  it('turns the Army badge bad while an enemy Warparty has arrived', () => {
    renderGame({ nation: nationView({ arrivals: [{ nation: 'Egypt', warparty: 7 }] }) })

    const badge = within(tab('Army')).getByText('!')
    expect(badge).toHaveClass('tabs__badge--bad')
  })

  it('opens Build by default, shows the tab chosen, and keeps it open as a new Turn arrives', () => {
    const { rerenderGame } = renderGame()

    expect(tab('Build')).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('region', { name: 'Build' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Train' })).not.toBeInTheDocument()

    fireEvent.click(tab('Train'))

    expect(tab('Train')).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('region', { name: 'Train' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Build' })).not.toBeInTheDocument()

    rerenderGame({
      room: roomView({ game: { ...roomView().game!, turn: TURN + 1 } }),
      nation: nationView({ version: 2, turn: TURN + 1, training: [] }),
    })

    expect(tab('Train')).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('region', { name: 'Train' })).toBeInTheDocument()
  })

  it('shows a rejection in the tab where the action was taken, without switching tabs', () => {
    const { rerenderGame } = renderGame()
    fireEvent.click(tab('Train'))
    fireEvent.click(screen.getByRole('button', { name: 'Train Warrior' }))

    rerenderGame({ rejection: 'Not enough Population.' })

    expect(tab('Train')).toHaveAttribute('aria-selected', 'true')
    expect(within(screen.getByRole('region', { name: 'Train' })).getByRole('alert')).toHaveTextContent(
      'Not enough Population.',
    )
  })

  it.each([
    ['knocked out', { conquered: ['Rome'] }],
    ['Removed', { removed: ['Rome'] }],
    ['watching the end of the Game', { winner: 'Egypt' }],
  ])('leaves out the tabs for a Player %s', (_, game) => {
    renderGame({ room: roomView({ game: { ...roomView().game!, ...game } }), playerId: MY_PLAYER_ID })

    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Map' })).toBeInTheDocument()
  })
})
