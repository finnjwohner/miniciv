import type { Named } from '../api'
import { NationName } from '../ui/NationName'

/** A Nation's or Barbarian Camp's name with its colour and emblem, as the Map shows it. */
export function MapName({ name, map }: { name: string; map: Named[] }) {
  const known = map.find((n) => n.name === name)
  return <NationName name={name} colour={known?.colour} emblem={known?.emblem} />
}
