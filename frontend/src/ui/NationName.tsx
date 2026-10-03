import { Emblem } from './Icon'

/** A Nation's emblem drawn in its colour. */
export function NationEmblem({ colour, emblem, size = 24 }: { colour: string; emblem: string; size?: number }) {
  return (
    <span className="nation-emblem" style={{ color: colour }}>
      <Emblem emblem={emblem} size={size} />
    </span>
  )
}

/** A Nation's name after its emblem in its colour; just the name if neither is known. */
export function NationName({ name, colour, emblem }: { name: string; colour?: string; emblem?: string }) {
  return (
    <span className="nation-name">
      {colour && emblem && <NationEmblem colour={colour} emblem={emblem} size={12} />}
      {name}
    </span>
  )
}
