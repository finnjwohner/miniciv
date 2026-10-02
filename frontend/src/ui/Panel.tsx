import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

/**
 * A titled box: the building block of every screen. `actions` sit at the right of the title bar;
 * `className` places or highlights the box.
 */
export function Panel({
  title,
  icon,
  actions,
  className,
  children,
}: {
  title: string
  icon?: IconName
  actions?: ReactNode
  className?: string
  children: ReactNode
}) {
  return (
    <section className={className ? `panel ${className}` : 'panel'}>
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
