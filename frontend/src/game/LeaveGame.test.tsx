import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderGame } from '../test/fixtures'

const leaveButton = () => screen.getByRole('button', { name: 'Leave Game' })
const confirmation = () => screen.getByRole('dialog', { name: 'Leave the Game?' })

describe('Leave Game', () => {
  it('shows a Leave Game button in the header during a Game', () => {
    renderGame()
    expect(leaveButton()).toBeInTheDocument()
  })

  it('asks for confirmation, and cancelling leaves nothing', () => {
    const onLeave = vi.fn()
    renderGame({ onLeave })

    fireEvent.click(leaveButton())
    expect(confirmation()).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog', { name: 'Leave the Game?' })).not.toBeInTheDocument()
    expect(onLeave).not.toHaveBeenCalled()
  })

  it('leaves the Game once confirmed', () => {
    const onLeave = vi.fn()
    renderGame({ onLeave })

    fireEvent.click(leaveButton())
    fireEvent.click(screen.getByRole('button', { name: 'Leave' }))

    expect(onLeave).toHaveBeenCalledOnce()
  })

  it('asks again each time, and Escape cancels', () => {
    const onLeave = vi.fn()
    renderGame({ onLeave })

    fireEvent.click(leaveButton())
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog', { name: 'Leave the Game?' })).not.toBeInTheDocument()

    fireEvent.click(leaveButton())
    expect(confirmation()).toBeInTheDocument()
  })
})
