import type { SVGProps } from 'react'
import {
  Anchor,
  Bell,
  BookOpen,
  Building,
  Castle,
  Check,
  Chess,
  Coins,
  Compass,
  Crown,
  DiamondGem,
  Dog,
  Eye,
  Feather,
  Fire,
  Fish,
  Flag,
  Gear,
  Heart,
  Hourglass,
  Human,
  Key,
  Lightbulb,
  Link,
  Login,
  Map as MapIcon,
  Message,
  Moon,
  Pause,
  Play,
  Plus,
  Shield,
  Ship,
  Skull,
  Snake,
  Snowflake,
  Star,
  Sun,
  Sword,
  Logout,
  Target,
  Tree,
  Trophy,
  Users,
  WarningDiamond,
  Zap,
} from 'pixelarticons/react'

// The pixelarticons (MIT) the app uses, by name; add one here to make it available. Nation emblems are
// named by the server, so every emblem it offers needs an icon here.
const icons = {
  anchor: Anchor,
  bell: Bell,
  book: BookOpen,
  building: Building,
  castle: Castle,
  check: Check,
  chess: Chess,
  coins: Coins,
  compass: Compass,
  crown: Crown,
  dog: Dog,
  eye: Eye,
  feather: Feather,
  fire: Fire,
  fish: Fish,
  flag: Flag,
  gear: Gear,
  gem: DiamondGem,
  heart: Heart,
  hourglass: Hourglass,
  human: Human,
  key: Key,
  lightbulb: Lightbulb,
  link: Link,
  login: Login,
  map: MapIcon,
  message: Message,
  moon: Moon,
  pause: Pause,
  play: Play,
  plus: Plus,
  shield: Shield,
  ship: Ship,
  skull: Skull,
  snake: Snake,
  snowflake: Snowflake,
  star: Star,
  sun: Sun,
  sword: Sword,
  target: Target,
  tree: Tree,
  trophy: Trophy,
  users: Users,
  logout: Logout,
  warning: WarningDiamond,
  zap: Zap,
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
      data-icon={name}
      width={size}
      height={size}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    />
  )
}

/** A Nation's emblem, as the server names it, or a flag if there is no icon by that name. */
export function Emblem({ emblem, size = 24 }: { emblem: string; size?: number }) {
  return <Icon name={emblem in icons ? (emblem as IconName) : 'flag'} size={size} />
}
