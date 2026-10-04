import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Encounter } from '../api'
import { nationView, renderGame } from '../test/fixtures'

/** The choice buttons of the fixture's Flooded river Dilemma. */
function choiceButtons() {
  return within(screen.getByRole('list', { name: 'Choices for Flooded river' })).getAllByRole('button')
}

describe('DecisionsPanel', () => {
  it('runs the Tribute sliders from 0 to the Gold and Population held', () => {
    renderGame({ nation: nationView({ gold: 75, population: 9, arrivals: [{ nation: 'Egypt', warparty: 1 }] }) })

    const gold = screen.getByRole('slider', { name: 'Gold to offer Egypt' })
    expect(gold).toHaveAttribute('min', '0')
    expect(gold).toHaveAttribute('max', '75')
    const population = screen.getByRole('slider', { name: 'Population to offer Egypt' })
    expect(population).toHaveAttribute('min', '0')
    expect(population).toHaveAttribute('max', '9')
  })

  it('keeps the Tribute sliders when only 1 Gold and 1 Population are held, so 0 stays selectable', () => {
    renderGame({ nation: nationView({ gold: 1, population: 1, arrivals: [{ nation: 'Egypt', warparty: 1 }] }) })

    for (const name of ['Gold to offer Egypt', 'Population to offer Egypt']) {
      const slider = screen.getByRole('slider', { name })
      expect(slider).toHaveAttribute('min', '0')
      expect(slider).toHaveAttribute('max', '1')
      expect(slider).toHaveValue('0')
    }
  })

  it('shows a button per Dilemma choice, tagging only the worst', () => {
    renderGame()

    const buttons = choiceButtons()
    expect(buttons).toHaveLength(2)
    const [levees, flood] = buttons
    expect(levees).toHaveTextContent('Build levees')
    expect(within(levees).queryByText("if you don't choose")).not.toBeInTheDocument()
    expect(levees).toBeEnabled()
    expect(flood).toHaveTextContent('Let it flood')
    expect(within(flood).getByText("if you don't choose")).toBeInTheDocument()
  })

  it('highlights the choice made, keeps every choice clickable and tags the card chosen', () => {
    const nation = nationView()
    const onAnswerDilemma = vi.fn()
    renderGame({
      nation: {
        ...nation,
        dilemmas: [{ ...nation.dilemmas[0], chosen: 'Build levees' }],
      },
      onAnswerDilemma,
    })

    const [levees, flood] = choiceButtons()
    expect(levees).toHaveClass('button--chosen')
    expect(flood).not.toHaveClass('button--chosen')
    expect(screen.getByText(/You chose Build levees; you can change it until the Turn ends/)).toBeInTheDocument()
    const card = screen.getByRole('heading', { name: /Dilemma/ })
    expect(within(card).getByText('chosen')).toHaveClass('tag--muted')
    expect(within(card).queryByText('needs you')).not.toBeInTheDocument()

    fireEvent.click(flood)

    expect(onAnswerDilemma).toHaveBeenCalledWith('Flooded river', 'Let it flood')
  })

  it("keeps an Encounter's Fight and Let it pass after choosing, highlighting the choice", () => {
    const encounter: Encounter = {
      first: {
        nation: 'Rome',
        warparty: 1,
        units: { Warrior: 3 },
        choice: 'FIGHT',
      },
      second: {
        nation: 'Egypt',
        warparty: 1,
        units: { Warrior: 1 },
        choice: 'NONE',
      },
    }
    const onAnswerEncounter = vi.fn()
    renderGame({
      nation: nationView({ dilemmas: [], encounters: [encounter] }),
      onAnswerEncounter,
    })

    const fight = screen.getByRole('button', { name: 'Fight' })
    const pass = screen.getByRole('button', { name: 'Let it pass' })
    expect(fight).toHaveClass('button--chosen')
    expect(pass).not.toHaveClass('button--chosen')
    expect(screen.getByText(/You chose to fight; you can change it until the Turn ends/)).toBeInTheDocument()
    expect(within(screen.getByRole('heading', { name: /Encounter/ })).getByText('chosen')).toBeInTheDocument()

    fireEvent.click(pass)

    expect(onAnswerEncounter).toHaveBeenCalledWith(1, false)
  })
})
