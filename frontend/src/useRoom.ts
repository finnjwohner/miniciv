import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import type { NationView, RoomView } from './api'

/** A Room view and when this browser received it, to count down the Turn from. */
export type ReceivedRoom = RoomView & { receivedAt: number }

/**
 * Follows a Room live over STOMP as the Player holding the token: the Room's public state,
 * and once the Game starts, this Player's own Nation, which only they receive.
 */
export function useRoom(code: string, token: string) {
  const [room, setRoom] = useState<ReceivedRoom | null>(null)
  const [nation, setNation] = useState<NationView | null>(null)
  const [rejection, setRejection] = useState<string | null>(null)
  const client = useRef<Client | null>(null)

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const stomp = new Client({ brokerURL: `${protocol}://${window.location.host}/ws`, connectHeaders: { token } })
    stomp.onConnect = () => {
      // Current-state replies and later updates can arrive in either order; keep the newest.
      const updateRoom = (message: { body: string }) => {
        const next: RoomView = JSON.parse(message.body)
        setRoom((current) => (current && current.version >= next.version ? current : { ...next, receivedAt: Date.now() }))
      }
      stomp.subscribe(`/topic/rooms/${code}`, updateRoom)
      stomp.subscribe(`/app/rooms/${code}`, updateRoom)
      const updateNation = (message: { body: string }) => {
        const next: NationView = JSON.parse(message.body)
        setNation((current) => (current && current.version >= next.version ? current : next))
      }
      stomp.subscribe('/user/queue/game', updateNation)
      stomp.subscribe(`/app/rooms/${code}/nation`, updateNation)
      stomp.subscribe('/user/queue/rejections', (message) => setRejection(JSON.parse(message.body).reason))
    }
    stomp.activate()
    client.current = stomp
    return () => {
      client.current = null
      void stomp.deactivate()
    }
  }, [code, token])

  const setReady = (ready: boolean) =>
    client.current?.publish({
      destination: `/app/rooms/${code}/ready`,
      body: JSON.stringify({ token, ready }),
    })

  /** Starts constructing a Building; a rejection's reason arrives in `rejection`. */
  const startBuilding = (building: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/buildings`,
      body: JSON.stringify({ token, building }),
    })
  }

  /** Marks the Player done with the given Turn; ignored if that Turn has already ended. */
  const markDone = (turn: number) =>
    client.current?.publish({
      destination: `/app/rooms/${code}/done`,
      body: JSON.stringify({ token, turn }),
    })

  return { room, nation, rejection, setReady, startBuilding, markDone }
}
