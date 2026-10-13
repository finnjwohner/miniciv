import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

/** The STOMP client the Room screen made, standing in for a connection to the server. */
const clients = vi.hoisted(
  () =>
    [] as {
      onWebSocketClose: () => void
      onConnect: () => void
      subscriptions: Map<string, (message: { body: string }) => void>
    }[],
)

vi.mock('@stomp/stompjs', () => ({
  TickerStrategy: { Worker: 'worker' },
  Client: class {
    onWebSocketClose = () => {}
    onConnect = () => {}
    subscriptions = new Map<string, (message: { body: string }) => void>()
    constructor() {
      clients.push(this)
    }
    activate() {}
    deactivate() {}
    subscribe(destination: string, callback: (message: { body: string }) => void) {
      this.subscriptions.set(destination, callback)
    }
    publish() {}
  },
}))

describe('Connecting to a Room', () => {
  afterEach(() => {
    clients.length = 0
    window.history.pushState(null, '', '/')
    localStorage.clear()
  })

  it("says it can't connect while the server refuses the connection, until it connects", () => {
    localStorage.setItem('miniciv.room.brave-otter-lamp', JSON.stringify({ playerId: 'p1', token: 't1' }))
    window.history.pushState(null, '', '/rooms/brave-otter-lamp')
    render(<App />)
    expect(screen.getByText(/Connecting to Room brave-otter-lamp/)).toBeInTheDocument()

    act(() => clients[0].onWebSocketClose())

    expect(screen.getByRole('alert')).toHaveTextContent("Can't connect to miniciv right now.")
    act(() => clients[0].onConnect())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it("says it can't connect when reconnecting to a Room already shown is refused", () => {
    localStorage.setItem('miniciv.room.brave-otter-lamp', JSON.stringify({ playerId: 'p1', token: 't1' }))
    window.history.pushState(null, '', '/rooms/brave-otter-lamp')
    render(<App />)
    const client = clients[0]
    act(() => client.onConnect())
    const room = {
      code: 'brave-otter-lamp',
      version: 1,
      colours: ['#5b8def'],
      emblems: ['sun'],
      players: [
        { id: 'p1', nationName: 'Rome', ruler: 'Caesar', colour: '#5b8def', emblem: 'sun', ready: false, done: false },
      ],
      game: null,
      previous: null,
    }
    act(() => client.subscriptions.get('/app/rooms/brave-otter-lamp')!({ body: JSON.stringify(room) }))

    // The connection dropping is not a refusal; failing to connect again is.
    act(() => client.onWebSocketClose())
    expect(screen.queryByText(/Can't connect/)).not.toBeInTheDocument()
    act(() => client.onWebSocketClose())

    expect(screen.getByText(/Can't connect to miniciv right now/)).toBeInTheDocument()
  })
})
