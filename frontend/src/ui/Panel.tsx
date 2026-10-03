import { useId, type ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

/**
 * A titled box, named by its title: the building block of every screen. `actions` sit at the right of
 * the title bar; `className` places or highlights the box; `role` makes it something more, such as a dialog.
 */
export function Panel({
  title,
  icon,
  actions,
  className,
  role,
  children,
}: {
  title: string
  icon?: IconName
  actions?: ReactNode
  className?: string
  role?: string
  children: ReactNode
}) {
  const titleId = useId()
  return (
    <section className={className ? `panel ${className}` : 'panel'} role={role} aria-labelledby={titleId}>
      <header className="panel__bar">
        <h2 className="panel__title" id={titleId}>
          {icon && <Icon name={icon} size={12} />}
          {title}
        </h2>
        {actions && <div className="panel__actions">{actions}</div>}
      </header>
      <div className="panel__body">{children}</div>
    </section>
  )
}
