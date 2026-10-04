// Small helpers shared by the Game screen's panels.

export function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

/** Such as "1:05"; never negative. */
export function formatCountdown(millis: number) {
  const seconds = Math.ceil(Math.max(0, millis) / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** Units or Buildings counted by type, such as "Warrior ×2, Archer ×1". */
export function listCounts(counts: Record<string, number>) {
  return Object.entries(counts)
    .map(([name, count]) => `${name} ×${count}`)
    .join(', ')
}

/** A gain or loss, such as "+14" or "−2", with a true minus sign. */
export function formatChange(amount: number) {
  return `${amount > 0 ? '+' : '−'}${Math.abs(amount)}`
}

/** A number with at most one decimal place, such as Strength 12.3. */
export function formatAmount(amount: number) {
  return String(Math.round(amount * 10) / 10)
}

/** The chosen target, or the first one still in the Game if it has left. */
export function currentTarget(targets: string[], chosen: string) {
  return targets.includes(chosen) ? chosen : (targets[0] ?? '')
}

/**
 * The rows that are the same by `sameBy`, each shown once with its `count` summed, in the order first
 * seen; the key doubles as the row's React key.
 */
export function mergeAlike<T>(items: T[], sameBy: (item: T) => (string | number | boolean)[], count: (item: T) => number = () => 1) {
  const rows = new Map<string, { item: T; count: number }>()
  for (const item of items) {
    const key = JSON.stringify(sameBy(item))
    const row = rows.get(key)
    if (row) row.count += count(item)
    else rows.set(key, { item, count: count(item) })
  }
  return [...rows].map(([key, row]) => ({ key, ...row }))
}
