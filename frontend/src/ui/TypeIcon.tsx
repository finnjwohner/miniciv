import { Fragment } from 'react'
import { Icon, type IconName } from './Icon'
import { typeArt } from './typeArt'

export type TypeKind = 'unit' | 'building' | 'technology'

/** The SVG path filling the '#' cells of a 12×12 pixel-art grid, 2px to a cell, one rectangle per run of cells. */
function toPath(art: string) {
  return art
    .trim()
    .split('\n')
    .flatMap((line, y) =>
      [...line.trim().matchAll(/#+/g)].map((run) => `M${run.index * 2} ${y * 2}h${run[0].length * 2}v2h-${run[0].length * 2}z`),
    )
    .join('')
}

const artPaths = new Map(Object.entries(typeArt).map(([name, art]) => [name, toPath(art)]))

/** The icon for a type of each kind that has none of its own, matching its panel's; a key for an unknown kind. */
const fallbacks: Record<TypeKind, IconName> = { unit: 'sword', building: 'building', technology: 'lightbulb' }

/**
 * The pixel-art icon of the Unit type, Building or Technology `name`, in the current text colour, decorative
 * beside its name. A type with none of its own gets the generic icon for its `kind`; with no `kind`, as for what a
 * Technology unlocks, which may be a Unit or a Building, a key.
 */
export function TypeIcon({ name, kind, size = 24 }: { name: string; kind?: TypeKind; size?: number }) {
  const path = artPaths.get(name)
  if (!path) return <Icon name={kind ? fallbacks[kind] : 'key'} size={size} />
  return (
    <svg
      className="icon"
      data-icon={name}
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="currentColor"
      aria-hidden="true"
    >
      <path d={path} />
    </svg>
  )
}

/** A type's name with its icon before it: 24px at the head of a row, 12px, one pixel to a cell, within text. */
export function TypeName({ name, kind, size = 12 }: { name: string; kind?: TypeKind; size?: number }) {
  return (
    <span className="type-name">
      <TypeIcon name={name} kind={kind} size={size} />
      {name}
    </span>
  )
}

/** Names, each with its icon and any count after it, such as "Warrior ×2", separated by commas. */
function NameList({ entries, kind, size }: { entries: [string, number?][]; kind?: TypeKind; size?: number }) {
  return entries.map(([name, count], i) => (
    <Fragment key={name}>
      {i > 0 && ', '}
      <TypeName name={name} kind={kind} size={size} />
      {count !== undefined && ` ×${count}`}
    </Fragment>
  ))
}

/** Types' names, each with its icon, separated by commas. */
export function TypeNames({ names, kind }: { names: string[]; kind?: TypeKind }) {
  return <NameList entries={names.map((name) => [name])} kind={kind} />
}

/** Things counted by type, each with its icon, such as "Warrior ×2, Archer ×1". */
export function TypeCounts({ counts, kind, size }: { counts: Record<string, number>; kind: TypeKind; size?: number }) {
  return <NameList entries={Object.entries(counts)} kind={kind} size={size} />
}
