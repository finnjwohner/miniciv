import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import type { Message, NationView, RoomView, TaskKind } from './api'

/** A Room view and when this browser received it, to count down the Turn from. */
export type ReceivedRoom = RoomView & { receivedAt: number }

/** Adds Messages to those already received, each once, in the order they were sent. */
function withMessages(current: Message[], received: Message[]): Message[] {
  const byId = new Map(current.map((m) => [m.id, m]))
  received.forEach((m) => byId.set(m.id, m))
  return [...byId.values()].sort((a, b) => a.id - b.id)
}

/**
 * Follows a Room live over STOMP as the Player holding the token: the Room's public state,
 * and once the Game starts, this Player's own Nation and the Messages they sent and received,
 * which only they receive.
 */
export function useRoom(code: string, token: string, playerId: string) {
  const [room, setRoom] = useState<ReceivedRoom | null>(null)
  const [nation, setNation] = useState<NationView | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [rejection, setRejection] = useState<string | null>(null)
  const [unreachable, setUnreachable] = useState(false)
  const client = useRef<Client | null>(null)

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    // A dropped connection pauses the Game for everyone and counts against this Player, so retry quickly.
    const stomp = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      connectHeaders: { token },
      reconnectDelay: 1000,
    })
    // A socket closing before STOMP connected means the server refused it, such as when it has as many
    // connections as it allows; browsers don't say why. Retrying carries on meanwhile.
    let connected = false
    stomp.onWebSocketClose = () => {
      if (!connected) setUnreachable(true)
      connected = false
    }
    stomp.onConnect = () => {
      connected = true
      setUnreachable(false)
      // Current-state replies and later updates can arrive in either order; keep the newest.
      const updateRoom = (message: { body: string }) => {
        const next: RoomView = JSON.parse(message.body)
        setRoom((current) => {
          if (current && current.version >= next.version) return current
          // Back in the Room's pre-game screen, so the ended Game's Nation and Messages are done with.
          if (!next.game && next.players.some((p) => p.id === playerId)) {
            setNation(null)
            setMessages([])
          }
          return { ...next, receivedAt: Date.now() }
        })
      }
      stomp.subscribe(`/topic/rooms/${code}`, updateRoom)
      stomp.subscribe(`/app/rooms/${code}`, updateRoom)
      const updateNation = (message: { body: string }) => {
        const next: NationView = JSON.parse(message.body)
        setNation((current) => (current && current.version >= next.version ? current : next))
      }
      stomp.subscribe('/user/queue/game', updateNation)
      stomp.subscribe(`/app/rooms/${code}/nation`, updateNation)
      // The history and new Messages can arrive in either order, and the history again on reconnecting.
      stomp.subscribe('/user/queue/messages', (message) => setMessages((current) => withMessages(current, [JSON.parse(message.body)])))
      stomp.subscribe(`/app/rooms/${code}/messages`, (message) =>
        setMessages((current) => withMessages(current, JSON.parse(message.body).messages)),
      )
      stomp.subscribe('/user/queue/rejections', (message) => setRejection(JSON.parse(message.body).reason))
    }
    stomp.activate()
    client.current = stomp
    return () => {
      client.current = null
      void stomp.deactivate()
    }
  }, [code, token, playerId])

  /** Marks the Player ready or not; a rejection's reason arrives in `rejection`. */
  const setReady = (ready: boolean) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/ready`,
      body: JSON.stringify({ token, ready }),
    })
  }

  /** Names the Nation's Ruler and sets its colour and emblem; a rejection's reason arrives in `rejection`. */
  const setIdentity = (ruler: string, colour: string, emblem: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/identity`,
      body: JSON.stringify({ token, ruler, colour, emblem }),
    })
  }

  /** Starts constructing a Building; a rejection's reason arrives in `rejection`. */
  const startBuilding = (building: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/buildings`,
      body: JSON.stringify({ token, building }),
    })
  }

  /** Starts repairing a defensive Building type's damaged Building; a rejection's reason arrives in `rejection`. */
  const repairBuilding = (building: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/repairs`,
      body: JSON.stringify({ token, building }),
    })
  }

  /** Starts rebuilding one destroyed defensive Building; a rejection's reason arrives in `rejection`. */
  const rebuildBuilding = (building: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/rebuilds`,
      body: JSON.stringify({ token, building }),
    })
  }

  /** Sells one standing Building of a type for Gold at once; a rejection's reason arrives in `rejection`. */
  const sellBuilding = (building: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/sales`,
      body: JSON.stringify({ token, building }),
    })
  }

  /** Starts training Units of one type; a rejection's reason arrives in `rejection`. */
  const trainUnits = (unit: string, count: number) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/units`,
      body: JSON.stringify({ token, unit, count }),
    })
  }

  /** Sends Units from the Army to another Nation; a rejection's reason arrives in `rejection`. */
  const sendWarparty = (target: string, units: Record<string, number>) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/warparties`,
      body: JSON.stringify({ token, target, units }),
    })
  }

  /** Sends a Spy from the Army to another Nation; a rejection's reason arrives in `rejection`. */
  const sendSpy = (target: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/spies`,
      body: JSON.stringify({ token, target }),
    })
  }

  /** Sets one of the Nation's Spies in place to a Mission, from scratch; a rejection's reason arrives in `rejection`. */
  const setMission = (spy: number, mission: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/spies/missions`,
      body: JSON.stringify({ token, spy, mission }),
    })
  }

  /** Sets some of the Nation's Idle Scouts to a Task; a rejection's reason arrives in `rejection`. */
  const setTask = (kind: TaskKind, target: string | null, scouts: number) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/tasks`,
      body: JSON.stringify({ token, kind, target, scouts }),
    })
  }

  /** Ends one of the Nation's Tasks, its Scouts going Idle; a rejection's reason arrives in `rejection`. */
  const cancelTask = (task: number) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/tasks/cancellations`,
      body: JSON.stringify({ token, task }),
    })
  }

  /** Recalls one of the Nation's Warparties, turning it back home; a rejection's reason arrives in `rejection`. */
  const recallWarparty = (warparty: number) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/recalls`,
      body: JSON.stringify({ token, warparty }),
    })
  }

  /** Makes a choice for one of the Nation's Dilemmas, applying as the Turn ends; a rejection's reason arrives in `rejection`. */
  const answerDilemma = (dilemma: string, choice: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/dilemmas`,
      body: JSON.stringify({ token, dilemma, choice }),
    })
  }

  /**
   * Chooses whether to keep a Haul seized from a caught Spy or send it back, applying as the Turn ends; a
   * rejection's reason arrives in `rejection`.
   */
  const answerHaul = (haul: number, keep: boolean) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/hauls`,
      body: JSON.stringify({ token, haul, keep }),
    })
  }

  /** Unlocks a Technology, paying its Research at once; a rejection's reason arrives in `rejection`. */
  const unlockTechnology = (technology: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/technologies`,
      body: JSON.stringify({ token, technology }),
    })
  }

  /**
   * Offers Tribute against another Nation's Warparty that arrived at this one; the offer reaches that
   * Nation's Player at once. A rejection's reason arrives in `rejection`.
   */
  const offerTribute = (nation: string, warparty: number, gold: number, population: number) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/tributes`,
      body: JSON.stringify({ token, nation, warparty, gold, population }),
    })
  }

  /**
   * Accepts or refuses the Tribute offered for one of the Nation's Warparties; accepting takes it at once
   * and Recalls the Warparty. A rejection's reason arrives in `rejection`.
   */
  const answerTribute = (warparty: number, accept: boolean) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/tributes/answers`,
      body: JSON.stringify({ token, warparty, accept }),
    })
  }

  /**
   * Chooses whether one of the Nation's Warparties fights the other Nation's Warparty it met; the other
   * Player sees the choice at once. A rejection's reason arrives in `rejection`.
   */
  const answerEncounter = (warparty: number, fight: boolean) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/encounters/answers`,
      body: JSON.stringify({ token, warparty, fight }),
    })
  }

  /** Sends a private Message to another Nation's Player; a rejection's reason arrives in `rejection`. */
  const sendMessage = (to: string, text: string) => {
    setRejection(null)
    client.current?.publish({
      destination: `/app/rooms/${code}/messages`,
      body: JSON.stringify({ token, to, text }),
    })
  }

  /** Returns to the Room after the Game ended, keeping the Nation, not ready. */
  const playAgain = () =>
    client.current?.publish({
      destination: `/app/rooms/${code}/play-again`,
      body: JSON.stringify({ token }),
    })

  /** Leaves the Game: the Player's Nation is removed at once, if it is still in it. */
  const leaveGame = () =>
    client.current?.publish({
      destination: `/app/rooms/${code}/leave`,
      body: JSON.stringify({ token }),
    })

  /** Marks the Player done with the given Turn; ignored if that Turn has already ended. */
  const markDone = (turn: number) =>
    client.current?.publish({
      destination: `/app/rooms/${code}/done`,
      body: JSON.stringify({ token, turn }),
    })

  return {
    room,
    nation,
    messages,
    rejection,
    /** Whether the last attempt to connect to the server failed; it keeps retrying. */
    unreachable,
    setReady,
    setIdentity,
    startBuilding,
    repairBuilding,
    rebuildBuilding,
    sellBuilding,
    trainUnits,
    sendWarparty,
    recallWarparty,
    sendSpy,
    setMission,
    setTask,
    cancelTask,
    answerDilemma,
    answerHaul,
    unlockTechnology,
    offerTribute,
    answerTribute,
    answerEncounter,
    sendMessage,
    markDone,
    leaveGame,
    playAgain,
  }
}
