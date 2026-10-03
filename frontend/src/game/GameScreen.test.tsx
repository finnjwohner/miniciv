import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame, roomView } from '../test/fixtures'

describe('GameScreen', () => {
  it('shows the Turn and the End Turn button', () => {
    renderGame({ room: roomView(), nation: nationView() })

    expect(screen.getByRole('heading', { name: 'Turn 3' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'End Turn' })).toBeEnabled()
  })
})
