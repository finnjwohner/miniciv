import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame, tab, TURN } from '../test/fixtures'
import { buttonsIn, precedes } from '../test/order'

describe('BuildPanel', () => {
  it('keeps the Build buttons where they are, before the state rows, as construction starts', () => {
    const { rerenderGame } = renderGame({
      nation: nationView({ construction: [] }),
    })
    const before = buttonsIn('Build')

    rerenderGame({
      nation: nationView({
        version: 2,
        construction: [{ building: 'Farm', turnsLeft: 2 }],
      }),
    })

    const build = screen.getByRole('region', { name: 'Build' })
    expect(buttonsIn('Build')).toEqual(before)
    const lastBuild = within(build)
      .getAllByRole('button', { name: /^Build / })
      .at(-1)!
    expect(precedes(lastBuild, within(build).getByText('constructing'))).toBe(true)
    expect(precedes(lastBuild, within(build).getAllByRole('button', { name: /^Sell / })[0])).toBe(true)
  })
})

describe('TrainPanel', () => {
  it('describes the Scout as roaming the wilds, not staying home', () => {
    renderGame()
    fireEvent.click(tab('Train'))

    const train = screen.getByRole('region', { name: 'Train' })
    expect(within(train).getByText(/roams the wilds/)).toBeInTheDocument()
    expect(within(train).queryByText(/stays home/)).not.toBeInTheDocument()
  })

  it('keeps the Train buttons where they are, before the state rows, as training starts', () => {
    const { rerenderGame } = renderGame({
      nation: nationView({ training: [] }),
    })
    fireEvent.click(tab('Train'))
    const before = buttonsIn('Train')

    rerenderGame({
      nation: nationView({
        version: 2,
        training: [{ unit: 'Warrior', count: 1, readyOnTurn: 4 }],
      }),
    })

    const train = screen.getByRole('region', { name: 'Train' })
    expect(buttonsIn('Train')).toEqual(before)
    const lastTrain = within(train)
      .getAllByRole('button', { name: /^Train / })
      .at(-1)!
    expect(precedes(lastTrain, within(train).getByText('Warrior ×1'))).toBe(true)
  })
})

describe('rows in progress', () => {
  /** The text of each row in the region `name` that mentions "left". */
  function rowsLeft(name: string) {
    return within(screen.getByRole('region', { name }))
      .getAllByRole('listitem')
      .map((row) => row.textContent)
      .filter((text) => text!.includes('left'))
  }

  it('merges training orders of the same Unit ready on the same Turn into one row with Turns left', () => {
    renderGame({
      nation: nationView({
        training: [
          { unit: 'Warrior', count: 1, readyOnTurn: TURN + 1 },
          { unit: 'Warrior', count: 1, readyOnTurn: TURN + 1 },
        ],
      }),
    })
    fireEvent.click(tab('Train'))

    expect(rowsLeft('Train')).toEqual(['Warrior ×21 Turn left'])
  })

  it('keeps training orders of the same Unit ready on different Turns apart', () => {
    renderGame({
      nation: nationView({
        training: [
          { unit: 'Warrior', count: 1, readyOnTurn: TURN + 1 },
          { unit: 'Warrior', count: 3, readyOnTurn: TURN + 2 },
        ],
      }),
    })
    fireEvent.click(tab('Train'))

    expect(rowsLeft('Train')).toEqual(['Warrior ×11 Turn left', 'Warrior ×32 Turns left'])
  })

  it('merges Constructions of the same Building with the same Turns left into one row', () => {
    renderGame({
      nation: nationView({
        construction: [
          { building: 'Farm', turnsLeft: 2 },
          { building: 'Farm', turnsLeft: 2 },
          { building: 'Farm', turnsLeft: 1 },
        ],
      }),
    })

    expect(rowsLeft('Build')).toEqual(['Farm ×2constructing2 Turns left', 'Farm ×1constructing1 Turn left'])
  })

  it('keeps a Repair and a Rebuild of the same Building apart, and merges each with its own kind', () => {
    renderGame({
      nation: nationView({
        construction: [{ building: 'Palisade', turnsLeft: 1 }],
        repairs: [
          { building: 'Palisade', rebuild: false, defense: 2, turnsLeft: 1 },
          { building: 'Palisade', rebuild: true, defense: 6, turnsLeft: 1 },
          { building: 'Palisade', rebuild: true, defense: 6, turnsLeft: 1 },
        ],
      }),
    })

    expect(rowsLeft('Build')).toEqual([
      'Palisade ×1constructing1 Turn left',
      'Palisade ×1being Repaired1 Turn left',
      'Palisade ×2being Rebuilt1 Turn left',
    ])
  })

  it('shows the Turns left for Units in training, never the Turn they are ready on', () => {
    renderGame()
    fireEvent.click(tab('Train'))

    expect(screen.queryByText(/ready on Turn/)).not.toBeInTheDocument()
    expect(rowsLeft('Train')).toEqual(['Archer ×22 Turns left'])
  })
})

describe('ResourcesPanel', () => {
  it('forecasts next Turn’s Gold under upkeep, with income and upkeep broken out', () => {
    renderGame({ nation: nationView({ upkeep: 18, goldForecast: { income: 60, upkeep: 18 } }) })

    const resources = screen.getByRole('region', { name: 'Rome' })
    const forecast = within(resources).getByText('Next Turn: +42 Gold (income 60 − upkeep 18)')
    expect(precedes(within(resources).getByText(/^Upkeep, at home and abroad/), forecast)).toBe(true)
    expect(forecast).not.toHaveClass('report__loss')
  })

  it('shows a forecast loss of Gold in the loss colour', () => {
    renderGame({ nation: nationView({ upkeep: 25, goldForecast: { income: 10, upkeep: 25 } }) })

    expect(screen.getByText('Next Turn: −15 Gold (income 10 − upkeep 25)')).toHaveClass('report__loss')
  })
})
