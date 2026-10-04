// What marks the start of a Turn: resources counting to their new values, new rows flashing, and cues.
import { useEffect, useEffectEvent, useRef, useState, type CSSProperties } from 'react'
import type { NationView, TurnReportKind } from '../api'
import { useCue, useSettings } from '../settings'
import type { Cue } from '../sound'

/** How long a resource takes to count to its new value. */
const COUNT_MILLIS = 800

/**
 * The value to show of a resource that has just changed by `change` to `value`, and whether it is
 * `counting`: from its old value to its new one as the component mounts, unless motion is reduced. Keyed by Turn, the component
 * counts again as each Turn arrives; a later change to `value` during the count is shown at once.
 */
export function useCountUp(value: number, change: number) {
  const { settings } = useSettings()
  const counting = change !== 0 && !settings.reducedMotion
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    if (!counting) return
    const start = performance.now()
    let frame = 0
    const step = () => {
      const next = Math.min(1, (performance.now() - start) / COUNT_MILLIS)
      setProgress(next)
      if (next < 1) frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [counting])

  if (!counting) return { shown: value, counting }
  // Fast at first, settling onto the new value.
  const eased = 1 - (1 - progress) ** 3
  return { shown: Math.round(value - change * (1 - eased)), counting }
}

/**
 * Which of a list's rows, by `keys`, flash: those not in the Nation view before this one, `version`.
 * Rows flash only in the Nation view they first appear in, and none do as the list first shows or
 * while motion is reduced.
 */
export function useFreshRows(version: number, keys: string[]) {
  const { settings } = useSettings()
  const [seen, setSeen] = useState({ version, keys, fresh: new Set<string>() })
  if (seen.version !== version) {
    const before = new Set(seen.keys)
    setSeen({ version, keys, fresh: new Set(keys.filter((key) => !before.has(key))) })
  }
  return (key: string) => !settings.reducedMotion && seen.fresh.has(key)
}

/** A list row's classes, flashing if it is `fresh`. */
export function rowClass(fresh: boolean) {
  return fresh ? 'row row--fresh' : 'row'
}

/** How long to leave between one part of the Turn Report starting to come in and the next. */
const ENTRANCE_STAGGER_MILLIS = 60

/** An element's classes and style, bringing it in at its step or leaving it still. */
export type Entrance = (step: number, className?: string) => { className?: string; style?: CSSProperties }

/**
 * How the parts of the Turn Report of `turn` come in: each step a little after the one before, as a
 * new Turn arrives. Nothing comes in for the Turn the screen opens on, as on joining or reconnecting,
 * nor for a Turn that arrived while motion was reduced, even once motion is back on.
 */
export function useReportEntrance(turn: number | undefined): Entrance {
  const { settings } = useSettings()
  const [arrival, setArrival] = useState({ turn, entering: false })
  if (arrival.turn !== turn) setArrival({ turn, entering: !settings.reducedMotion })
  return (step, className) =>
    arrival.entering
      ? {
          className: className ? `${className} report__enter` : 'report__enter',
          style: { animationDelay: `${step * ENTRANCE_STAGGER_MILLIS}ms` },
        }
      : { className }
}

/** How long to leave between one Turn-start cue and the next. */
const CUE_GAP_SECONDS = 0.4

/**
 * Plays the new-Turn cue as each new Turn arrives, then the completed cue if the Turn Report has
 * something Completed and the Battle cue if it has Warfare. Nothing plays for the Turn the screen opens on.
 */
export function useTurnStartCues(nation: NationView | null) {
  const play = useCue()
  const turn = nation?.turn
  const previous = useRef(turn)
  const announce = useEffectEvent(() => {
    const has = (kind: TurnReportKind) => nation?.turnReport?.sections.some((s) => s.kind === kind && s.lines.length > 0)
    const cues: Cue[] = ['newTurn']
    if (has('COMPLETED')) cues.push('completed')
    if (has('WARFARE')) cues.push('battle')
    cues.forEach((cue, i) => play(cue, i * CUE_GAP_SECONDS))
  })

  useEffect(() => {
    if (turn !== undefined && previous.current !== undefined && turn !== previous.current) announce()
    previous.current = turn
  }, [turn])
}
