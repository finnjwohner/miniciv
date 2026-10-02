import type { SVGProps } from 'react'
import { Castle, Check, Flag, Link, Login, Play, Plus, Users } from 'pixelarticons/react'

// The pixelarticons (MIT) the app uses, by name; add one here to make it available.
const icons = {
  castle: Castle,
  check: Check,
  flag: Flag,
  link: Link,
  login: Login,
  play: Play,
  plus: Plus,
  users: Users,
} satisfies Record<string, (props: SVGProps<SVGSVGElement>) => unknown>

export type IconName = keyof typeof icons

/**
 * A pixel-art icon in the current text colour. Icons are drawn on a 24px grid, so sizes that are
 * multiples of 12 stay crisp. Decorative unless given a `label`.
 */
export function Icon({ name, size = 24, label }: { name: IconName; size?: number; label?: string }) {
  const Svg = icons[name]
  return (
    <Svg
      className="icon"
      width={size}
      height={size}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    />
  )
}
