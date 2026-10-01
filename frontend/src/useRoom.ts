import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import type { RoomView } from './api'

/** Follows a Room live over STOMP and lets the Player mark themselves ready or unready. */
export function useRoom(code: string, token: string) {
  const [room, setRoom] = useState<RoomView | null>(null)
  const client = useRef<Client | null>(null)

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const stomp = new Client({ brokerURL: `${protocol}://${window.location.host}/ws` })
    stomp.onConnect = () => {
      // The current-state reply and topic updates can arrive in either order; keep the newest.
      const update = (message: { body: string }) => {
        const next: RoomView = JSON.parse(message.body)
        setRoom((current) => (current && current.version >= next.version ? current : next))
      }
      stomp.subscribe(`/topic/rooms/${code}`, update)
      stomp.subscribe(`/app/rooms/${code}`, update)
    }
    stomp.activate()
    client.current = stomp
    return () => {
      client.current = null
      void stomp.deactivate()
    }
  }, [code])

  const setReady = (ready: boolean) =>
    client.current?.publish({
      destination: `/app/rooms/${code}/ready`,
      body: JSON.stringify({ token, ready }),
    })

  return { room, setReady }
}
