// The graduations drawn beneath a count slider, like the marks along a measuring rule.

/** The most graduations a slider draws between its ends, so they never crowd. */
const MOST_GRADUATIONS = 20

/** How many minor intervals lie between major graduations, at the least. */
const MINOR_PER_MAJOR = 4

/** How close to an end, as a share of the whole range, a label may sit before it runs into the end's. */
const LABEL_CLEARANCE = 0.2

/** The round intervals graduations may fall at: 1, 5, 10, 25, 50, 100, 250, 500, 1000... */
function* roundIntervals() {
  yield 1
  for (let scale = 1; ; scale *= 10) {
    yield 5 * scale
    yield 10 * scale
    yield 25 * scale
  }
}

/** The first round interval at least `least`. */
function roundIntervalFrom(least: number) {
  for (const interval of roundIntervals()) if (interval >= least) return interval
  throw new Error('unreachable')
}

/** One mark along a slider: its value, whether it is a longer major mark, and whether its value is shown. */
export type Graduation = { value: number; major: boolean; labelled: boolean }

/**
 * The graduations of a slider from `min` to `max` (above `min`): one per value when the range spans no
 * more than `MOST_GRADUATIONS` values, otherwise one at each multiple of the smallest round interval that
 * keeps within that many, leaving out any so close to an end it would crowd the end's mark. Major marks
 * fall at the ends and at a coarser round interval (every 5 for one per value); the ends are always
 * labelled, and the other major marks are too unless their labels would run into an end's.
 */
export function graduations(min: number, max: number): Graduation[] {
  const span = max - min
  const minor = roundIntervalFrom(span / MOST_GRADUATIONS)
  const major = roundIntervalFrom(minor * MINOR_PER_MAJOR)
  const marks: Graduation[] = [{ value: min, major: true, labelled: true }]
  for (let value = (Math.floor(min / minor) + 1) * minor; value < max; value += minor) {
    const fromEnd = Math.min(value - min, max - value)
    if (fromEnd < minor / 2) continue
    const isMajor = value % major === 0
    marks.push({ value, major: isMajor, labelled: isMajor && fromEnd >= span * LABEL_CLEARANCE })
  }
  marks.push({ value: max, major: true, labelled: true })
  return marks
}
