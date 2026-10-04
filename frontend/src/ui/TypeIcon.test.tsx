/// <reference types="node" />
import { render } from '@testing-library/react'
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { TypeIcon, type TypeKind } from './TypeIcon'
import { typeArt } from './typeArt'

/** The names of the types of one kind in the server's shipped ruleset, from its file such as "units". Tests run in frontend/. */
function shipped(file: string): string[] {
  const json = readFileSync(`../backend/src/main/resources/ruleset/${file}.json`, 'utf8')
  return (JSON.parse(json) as { name: string }[]).map((t) => t.name)
}

function renderIcon(name: string, kind?: TypeKind) {
  const { container } = render(<TypeIcon name={name} kind={kind} />)
  return container.querySelector('svg')!
}

describe('TypeIcon', () => {
  const ruleset: [TypeKind, string][] = [
    ...shipped('units').map((name): [TypeKind, string] => ['unit', name]),
    ...shipped('buildings').map((name): [TypeKind, string] => ['building', name]),
    ...shipped('technologies').map((name): [TypeKind, string] => ['technology', name]),
  ]

  it.each(ruleset)('draws the shipped %s %s with an icon of its own', (kind, name) => {
    expect(renderIcon(name, kind)).toHaveAttribute('data-icon', name)
  })

  it.each(Object.entries(typeArt))('draws %s on a 12×12 grid', (_, art) => {
    const rows = art.trim().split('\n').map((row) => row.trim())
    expect(rows).toHaveLength(12)
    for (const row of rows) expect(row).toMatch(/^[.#]{12}$/)
  })

  it('draws a type with no icon of its own as the generic icon for its kind', () => {
    expect(renderIcon('Trebuchet', 'unit')).toHaveAttribute('data-icon', 'sword')
    expect(renderIcon('Temple', 'building')).toHaveAttribute('data-icon', 'building')
    expect(renderIcon('Theology', 'technology')).toHaveAttribute('data-icon', 'lightbulb')
    expect(renderIcon('Temple')).toHaveAttribute('data-icon', 'key')
  })

  it('hides itself from screen readers, as decoration beside the name', () => {
    expect(renderIcon('Warrior', 'unit')).toHaveAttribute('aria-hidden', 'true')
    expect(renderIcon('Trebuchet', 'unit')).toHaveAttribute('aria-hidden', 'true')
  })
})
