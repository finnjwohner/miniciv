import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame } from '../test/fixtures'

/** The choice buttons of the fixture's Flooded river Dilemma. */
function choiceButtons() {
  return within(screen.getByRole('list', { name: 'Choices for Flooded river' })).getAllByRole('button')
}

describe('DecisionsPanel', () => {
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

  it('highlights the choice made and disables every choice', () => {
    const nation = nationView()
    renderGame({ nation: { ...nation, dilemmas: [{ ...nation.dilemmas[0], chosen: 'Build levees' }] } })

    const [levees, flood] = choiceButtons()
    expect(levees).toHaveClass('button--chosen')
    expect(flood).not.toHaveClass('button--chosen')
    for (const button of choiceButtons()) expect(button).toBeDisabled()
  })
})
