import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import type { NationView } from '../api'

/** The action panels, one per tab, in the order the tabs are shown. */
export type ActionTab = 'build' | 'train' | 'tech' | 'army' | 'spies'

const TABS: { tab: ActionTab; label: string }[] = [
  { tab: 'build', label: 'Build' },
  { tab: 'train', label: 'Train' },
  { tab: 'tech', label: 'Tech' },
  { tab: 'army', label: 'Army' },
  { tab: 'spies', label: 'Spies' },
]

/** What a tab's badge says, if anything, and whether it warns the Player. */
type Badge = { text: string; bad: boolean } | null

/**
 * Each tab's badge: Buildings under construction or repair, Units in training, Technologies the Nation can
 * afford to unlock, Warparties abroad and Spies abroad. The Army's turns bad while an enemy Warparty has
 * arrived, showing "!" if the Nation has none abroad.
 */
function badges(nation: NationView): Record<ActionTab, Badge> {
  const count = (n: number): Badge => (n > 0 ? { text: String(n), bad: false } : null)
  const affordable = nation.techTree.filter((t) => t.standing === 'AVAILABLE' && t.research <= nation.research)
  const warparties = nation.warparties.length
  return {
    build: count(nation.construction.length + nation.repairs.length),
    train: count(nation.training.reduce((total, t) => total + t.count, 0)),
    tech: count(affordable.length),
    army: nation.arrivals.length > 0 ? { text: warparties > 0 ? String(warparties) : '!', bad: true } : count(warparties),
    spies: count(nation.spies.length),
  }
}

/**
 * The action panels as tabs, one shown at a time, each tab badged with what is going on in it. Build is
 * open at first; the open tab changes only when the Player picks another, and stays as new Turns arrive.
 * Every panel stays mounted, so what the Player has typed in one survives switching away.
 */
export function ActionTabs({ nation, panels }: { nation: NationView; panels: Record<ActionTab, ReactNode> }) {
  const [open, setOpen] = useState<ActionTab>('build')
  const id = useId()
  const tabRefs = useRef<Partial<Record<ActionTab, HTMLButtonElement | null>>>({})
  const badgeOf = badges(nation)

  /** Moves between tabs with the arrow, Home and End keys, as a tab list does. */
  const onKeyDown = (e: KeyboardEvent) => {
    const at = TABS.findIndex((t) => t.tab === open)
    const to = { ArrowLeft: at - 1, ArrowRight: at + 1, Home: 0, End: TABS.length - 1 }[e.key]
    if (to === undefined) return
    e.preventDefault()
    const next = TABS[(to + TABS.length) % TABS.length].tab
    setOpen(next)
    tabRefs.current[next]?.focus()
  }

  return (
    <div className="tabs dash__tabs">
      <div className="tabs__list" role="tablist" aria-label="Actions" onKeyDown={onKeyDown}>
        {TABS.map(({ tab, label }) => {
          const badge = badgeOf[tab]
          return (
            <button
              key={tab}
              ref={(button) => {
                tabRefs.current[tab] = button
              }}
              role="tab"
              id={`${id}-${tab}-tab`}
              aria-controls={`${id}-${tab}`}
              aria-selected={open === tab}
              tabIndex={open === tab ? 0 : -1}
              className="tabs__tab"
              onClick={() => setOpen(tab)}
            >
              {label}
              {badge && (
                <span className={badge.bad ? 'tabs__badge tabs__badge--bad' : 'tabs__badge'}>{badge.text}</span>
              )}
            </button>
          )
        })}
      </div>
      {TABS.map(({ tab }) => (
        <div
          key={tab}
          role="tabpanel"
          id={`${id}-${tab}`}
          aria-labelledby={`${id}-${tab}-tab`}
          className="tabs__panel"
          hidden={open !== tab}
        >
          {panels[tab]}
        </div>
      ))}
    </div>
  )
}
