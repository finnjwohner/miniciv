import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderGame, roomView } from '../test/fixtures'

const playAgainButton = () => screen.getByRole('button', { name: 'Play again' })

describe('Play again', () => {
  it('is not offered while the Game goes on', () => {
    renderGame()
    expect(screen.queryByRole('button', { name: 'Play again' })).not.toBeInTheDocument()
  })

  it('is offered once the Game has a winner, and pressing it asks to play again', () => {
    const onPlayAgain = vi.fn()
    const room = roomView()
    renderGame({ room: { ...room, game: { ...room.game!, winner: 'Egypt' } }, onPlayAgain })

    fireEvent.click(playAgainButton())

    expect(onPlayAgain).toHaveBeenCalledOnce()
  })

  it('shows why it was rejected next to the button', () => {
    const room = roomView()
    const { rerenderGame } = renderGame({ room: { ...room, game: { ...room.game!, winner: 'Egypt' } } })

    fireEvent.click(playAgainButton())
    rerenderGame({
      room: { ...room, game: { ...room.game!, winner: 'Egypt' } },
      rejection: 'This Room already has 4 Players.',
    })

    expect(screen.getByText('This Room already has 4 Players.')).toBeInTheDocument()
  })
})
