import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'
import { fakeWebAudio } from './test/audio'
import { prefersReducedMotion } from './test/motion'

/** The gear at the top right of every screen. */
function gear() {
  return screen.getByRole('button', { name: 'Settings' })
}

function volumeSlider() {
  return screen.getByRole('slider', { name: 'Volume' })
}

describe('Settings pop-up', () => {
  it('opens from the gear and closes from it again', () => {
    render(<App />)

    expect(screen.queryByRole('dialog', { name: 'Settings' })).not.toBeInTheDocument()
    fireEvent.click(gear())
    expect(screen.getByRole('dialog', { name: 'Settings' })).toBeInTheDocument()
    fireEvent.click(gear())
    expect(screen.queryByRole('dialog', { name: 'Settings' })).not.toBeInTheDocument()
  })

  it('closes on Escape and on a click outside it', () => {
    render(<App />)

    fireEvent.click(gear())
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog', { name: 'Settings' })).not.toBeInTheDocument()

    fireEvent.click(gear())
    fireEvent.pointerDown(screen.getByRole('dialog', { name: 'Settings' }))
    expect(screen.getByRole('dialog', { name: 'Settings' })).toBeInTheDocument()
    fireEvent.pointerDown(document.body)
    expect(screen.queryByRole('dialog', { name: 'Settings' })).not.toBeInTheDocument()
  })
})

describe('Click feedback', () => {
  it('plays the click cue when a button is clicked with sound on', () => {
    const tones = fakeWebAudio()
    render(<App />)

    fireEvent.click(gear())

    expect(tones.length).toBeGreaterThan(0)
  })

  it('plays nothing with sound off', () => {
    const tones = fakeWebAudio()
    render(<App />)
    fireEvent.click(gear())
    fireEvent.click(screen.getByRole('checkbox', { name: 'Sound' }))
    tones.length = 0

    fireEvent.click(gear())
    fireEvent.click(gear())

    expect(tones).toEqual([])
  })

  it('plays at the volume chosen on the slider', () => {
    const tones = fakeWebAudio()
    render(<App />)
    fireEvent.click(gear())

    fireEvent.change(volumeSlider(), { target: { value: '100' } })
    tones.length = 0
    fireEvent.click(gear())
    const loud = tones[0].peak
    fireEvent.click(gear())
    fireEvent.change(volumeSlider(), { target: { value: '25' } })
    tones.length = 0
    fireEvent.click(gear())

    expect(tones[0].peak).toBeCloseTo(loud / 4)
  })
})

describe('Reduced motion', () => {
  it('starts on when the operating system asks for reduced motion, and the screen has no animation', () => {
    prefersReducedMotion(true)
    render(<App />)
    fireEvent.click(gear())

    expect(screen.getByRole('checkbox', { name: 'Reduce motion' })).toBeChecked()
    expect(document.documentElement).not.toHaveAttribute('data-motion')
  })

  it('starts off otherwise, and turning it on removes the animation', () => {
    prefersReducedMotion(false)
    render(<App />)
    fireEvent.click(gear())

    expect(screen.getByRole('checkbox', { name: 'Reduce motion' })).not.toBeChecked()
    expect(document.documentElement).toHaveAttribute('data-motion')
    fireEvent.click(screen.getByRole('checkbox', { name: 'Reduce motion' }))
    expect(document.documentElement).not.toHaveAttribute('data-motion')
  })
})
