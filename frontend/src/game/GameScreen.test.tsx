import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeWebAudio } from '../test/audio'
import { renderGame, tab, TURN } from '../test/fixtures'

describe('GameScreen', () => {
  it('shows the Turn and the End Turn button', () => {
    renderGame()

    expect(screen.getByRole('heading', { name: `Turn ${TURN}` })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'End Turn' })).toBeEnabled()
  })

  it('plays its own cue for End Turn, not the click', () => {
    const tones = fakeWebAudio()
    renderGame()
    fireEvent.click(tab('Train'))
    const click = tones.map((tone) => tone.frequency)
    tones.length = 0

    fireEvent.click(screen.getByRole('button', { name: 'End Turn' }))

    expect(tones.length).toBeGreaterThan(0)
    expect(tones.map((tone) => tone.frequency)).not.toEqual(click)
  })
})
