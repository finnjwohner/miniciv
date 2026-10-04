import { describe, expect, it } from 'vitest'
import { graduations } from './graduations'

/** The values of every graduation from `min` to `max`. */
function values(min: number, max: number) {
  return graduations(min, max).map((g) => g.value)
}

/** The values of the labelled graduations from `min` to `max`. */
function labels(min: number, max: number) {
  return graduations(min, max)
    .filter((g) => g.labelled)
    .map((g) => g.value)
}

describe('graduations', () => {
  it('marks every value for a range of 20 values or fewer, labelling the ends and every 5', () => {
    expect(values(0, 20)).toEqual([...Array(21).keys()])
    expect(labels(0, 20)).toEqual([0, 5, 10, 15, 20])
    expect(values(1, 3)).toEqual([1, 2, 3])
    expect(labels(1, 3)).toEqual([1, 3])
  })

  it('leaves a major graduation unlabelled when it sits too close to an end to read', () => {
    const fifteen = graduations(0, 16).find((g) => g.value === 15)!
    expect(fifteen).toMatchObject({ major: true, labelled: false })
  })

  it('marks larger ranges at a round interval, never more than 20 between the ends', () => {
    expect(values(0, 75)).toEqual([0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75])
    expect(labels(0, 75)).toEqual([0, 25, 50, 75])
    expect(values(0, 450).slice(0, 4)).toEqual([0, 25, 50, 75])
    expect(values(3, 137)).toEqual([3, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 137])
    for (const max of [21, 99, 101, 500, 999, 4321]) expect(values(0, max).length - 2).toBeLessThanOrEqual(20)
  })

  it('leaves out a graduation that would crowd an end', () => {
    expect(values(0, 21)).toEqual([0, 5, 10, 15, 21])
    expect(values(9, 40)).toEqual([9, 15, 20, 25, 30, 35, 40])
  })
})
