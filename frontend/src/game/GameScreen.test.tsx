import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderGame, TURN } from '../test/fixtures'

describe('GameScreen', () => {
  it('shows the Turn and the End Turn button', () => {
    renderGame()

    expect(screen.getByRole('heading', { name: `Turn ${TURN}` })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'End Turn' })).toBeEnabled()
  })
})
