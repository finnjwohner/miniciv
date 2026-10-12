import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('Joining by room code', () => {
  let fetched: string[]

  beforeEach(() => {
    fetched = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        fetched.push(url)
        return new Response(JSON.stringify({ playerId: 'p1', token: 't1', roomCode: 'brave-otter-lamp' }))
      }),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    window.history.pushState(null, '', '/')
    localStorage.clear()
  })

  it('sends the code as typed and moves to the Room under its hyphenated code', async () => {
    render(<App />)

    fireEvent.change(screen.getByLabelText('Nation name'), { target: { value: 'Rome' } })
    fireEvent.change(screen.getByLabelText('Room code'), { target: { value: ' Brave Otter LAMP ' } })
    fireEvent.click(screen.getByRole('button', { name: /join/i }))

    await waitFor(() => expect(window.location.pathname).toBe('/rooms/brave-otter-lamp'))
    expect(fetched[0]).toBe('/api/rooms/Brave%20Otter%20LAMP/players')
  })

  it('reads an invite link in any case, with spaces as hyphens', () => {
    window.history.pushState(null, '', '/rooms/Calm%20Fox-Kite')
    render(<App />)

    expect(screen.getByText(/invited to Room calm-fox-kite/)).toBeInTheDocument()
  })
})
