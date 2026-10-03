// Where things fall in the document, for tests checking that controls stay put.
import { screen, within } from '@testing-library/react'

/** Whether `a` comes before `b` in the document. */
export function precedes(a: Element, b: Element) {
  return (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
}

/** The accessible names of the buttons in the panel named `name`, in document order. */
export function buttonsIn(name: string) {
  return within(screen.getByRole('region', { name }))
    .getAllByRole('button')
    .map((b) => b.getAttribute('aria-label') ?? b.textContent)
}
