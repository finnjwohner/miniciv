import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

/** A titled box: the building block of every screen. `actions` sit at the right of the title bar. */
export function Panel({
  title,
  icon,
  actions,
  children,
}: {
  title: string
  icon?: IconName
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="panel">
      <header className="panel__bar">
        <h2 className="panel__title">
          {icon && <Icon name={icon} size={12} />}
          {title}
        </h2>
        {actions && <div className="panel__actions">{actions}</div>}
      </header>
      <div className="panel__body">{children}</div>
    </section>
  )
}
