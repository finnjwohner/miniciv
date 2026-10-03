import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame, tab } from '../test/fixtures'
import { buttonsIn, precedes } from '../test/order'

describe('BuildPanel', () => {
  it('keeps the Build buttons where they are, before the state rows, as construction starts', () => {
    const { rerenderGame } = renderGame({ nation: nationView({ construction: [] }) })
    const before = buttonsIn('Build')

    rerenderGame({ nation: nationView({ version: 2, construction: [{ building: 'Farm', turnsLeft: 2 }] }) })

    const build = screen.getByRole('region', { name: 'Build' })
    expect(buttonsIn('Build')).toEqual(before)
    const lastBuild = within(build).getAllByRole('button', { name: /^Build / }).at(-1)!
    expect(precedes(lastBuild, within(build).getByText('constructing'))).toBe(true)
    expect(precedes(lastBuild, within(build).getAllByRole('button', { name: /^Sell / })[0])).toBe(true)
  })
})

describe('TrainPanel', () => {
  it('keeps the Train buttons where they are, before the state rows, as training starts', () => {
    const { rerenderGame } = renderGame({ nation: nationView({ training: [] }) })
    fireEvent.click(tab('Train'))
    const before = buttonsIn('Train')

    rerenderGame({ nation: nationView({ version: 2, training: [{ unit: 'Warrior', count: 1, readyOnTurn: 4 }] }) })

    const train = screen.getByRole('region', { name: 'Train' })
    expect(buttonsIn('Train')).toEqual(before)
    const lastTrain = within(train).getAllByRole('button', { name: /^Train / }).at(-1)!
    expect(precedes(lastTrain, within(train).getByText('Warrior ×1'))).toBe(true)
  })
})
