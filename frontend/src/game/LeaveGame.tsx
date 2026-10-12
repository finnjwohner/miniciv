import { useEffect, useRef, useState } from 'react'
import { Icon } from '../ui/Icon'
import { Panel } from '../ui/Panel'
import { TopbarSlot } from '../ui/TopbarSlot'

/**
 * The Leave Game button in the top bar. It always asks first: leaving removes the Player's Nation from
 * the Game at once, if it is still in it, and returns them to the menu.
 */
export function LeaveGame({ onLeave }: { onLeave: () => void }) {
  const [asking, setAsking] = useState(false)
  const toggle = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!asking) return
    const onKeyDown = (e: globalThis.KeyboardEvent) => {
      if (e.key !== 'Escape') return
      setAsking(false)
      toggle.current?.focus()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [asking])

  return (
    <>
      <TopbarSlot>
        <button ref={toggle} className="leave-toggle" aria-haspopup="dialog" onClick={() => setAsking(true)}>
          <Icon name="logout" size={24} />
          <span>Leave Game</span>
        </button>
      </TopbarSlot>
      {asking && (
        <Panel title="Leave the Game?" icon="logout" className="leave-confirmation" role="dialog">
          <p>Your Nation will be removed from the Game at once, and you will return to the menu.</p>
          <div className="button-row">
            <button onClick={() => setAsking(false)}>Cancel</button>
            <button className="button--primary" onClick={onLeave}>
              Leave
            </button>
          </div>
        </Panel>
      )}
    </>
  )
}
