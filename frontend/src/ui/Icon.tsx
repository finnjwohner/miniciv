import type { SVGProps } from 'react'
import {
  BookOpen,
  Building,
  Castle,
  Check,
  Coins,
  Eye,
  Flag,
  Hourglass,
  Human,
  Lightbulb,
  Link,
  Login,
  Map as MapIcon,
  Pause,
  Play,
  Plus,
  Shield,
  Skull,
  Sword,
  Trophy,
  Users,
  WarningDiamond,
} from 'pixelarticons/react'

// The pixelarticons (MIT) the app uses, by name; add one here to make it available.
const icons = {
  book: BookOpen,
  building: Building,
  castle: Castle,
  check: Check,
  coins: Coins,
  eye: Eye,
  flag: Flag,
  hourglass: Hourglass,
  human: Human,
  lightbulb: Lightbulb,
  link: Link,
  login: Login,
  map: MapIcon,
  pause: Pause,
  play: Play,
  plus: Plus,
  shield: Shield,
  skull: Skull,
  sword: Sword,
  trophy: Trophy,
  users: Users,
  warning: WarningDiamond,
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
