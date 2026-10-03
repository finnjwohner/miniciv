import type { MapNation } from '../api'
import { NationName } from '../ui/NationName'

/** A Nation's name with its colour and emblem, as the Map shows it. */
export function MapName({ name, map }: { name: string; map: MapNation[] }) {
  const known = map.find((n) => n.name === name)
  return <NationName name={name} colour={known?.colour} emblem={known?.emblem} />
}
