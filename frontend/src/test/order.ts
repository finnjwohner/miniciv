// Where things fall in the document, for tests checking that controls stay put.
import { screen, within } from '@testing-library/react'

/** Whether `a` comes before `b` in the document. */
export function precedes(a: Element, b: Element) {
  return (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
}

/** A text matcher for a row name reading `text` in full, such as "Warrior ×3", though an icon splits it up. */
export function rowNameReading(text: string) {
  return (_: string, el: Element | null) => !!el?.classList.contains('row__name') && el.textContent === text
}

/** The row name in `container` reading `text` in full. */
export function rowNamed(container: HTMLElement, text: string) {
  return within(container).getByText(rowNameReading(text))
}

/** The accessible names of the buttons in the panel named `name`, in document order. */
export function buttonsIn(name: string) {
  return within(screen.getByRole('region', { name }))
    .getAllByRole('button')
    .map((b) => b.getAttribute('aria-label') ?? b.textContent)
}
