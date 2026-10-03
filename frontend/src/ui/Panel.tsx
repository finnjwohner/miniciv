import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useCue, useSettings } from '../settings'
import { Icon, type IconName } from './Icon'

/**
 * Whether the panel shakes for `rejection`, why the server refused the last action taken in it. Each new
 * one plays the rejection cue and, unless motion is reduced, shakes the panel once while it is shown;
 * one already there as the panel appears does not. Every action clears the last rejection, so the
 * same reason again is a new one.
 */
function useRejectionFeedback(rejection: string | null) {
  const play = useCue()
  const { settings } = useSettings()
  const [arrived, setArrived] = useState<string | null>(null)
  const previous = useRef(rejection)

  useEffect(() => {
    if (rejection && rejection !== previous.current) {
      play('rejection')
      setArrived(rejection)
    }
    previous.current = rejection
  }, [rejection, play])

  return rejection !== null && rejection === arrived && !settings.reducedMotion
}

/**
 * A titled box, named by its title: the building block of every screen. `actions` sit at the right of
 * the title bar; `className` places or highlights the box; `role` makes it something more, such as a dialog.
 * A panel the Player acts in shows its `rejection` first, above its actions, and shakes as one arrives.
 */
export function Panel({
  title,
  icon,
  actions,
  className,
  role,
  rejection = null,
  children,
}: {
  title: string
  icon?: IconName
  actions?: ReactNode
  className?: string
  role?: string
  rejection?: string | null
  children: ReactNode
}) {
  const titleId = useId()
  const shaking = useRejectionFeedback(rejection)
  const classes = ['panel', className, shaking && 'panel--shake'].filter(Boolean).join(' ')
  return (
    <section className={classes} role={role} aria-labelledby={titleId}>
      <header className="panel__bar">
        <h2 className="panel__title" id={titleId}>
          {icon && <Icon name={icon} size={12} />}
          {title}
        </h2>
        {actions && <div className="panel__actions">{actions}</div>}
      </header>
      <div className="panel__body">
        {rejection && (
          <p role="alert" className="alert">
            {rejection}
          </p>
        )}
        {children}
      </div>
    </section>
  )
}
