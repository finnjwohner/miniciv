import { useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/** The id of the place in the top bar, left of the settings gear, where a screen can put its own controls. */
export const TOPBAR_SLOT = 'topbar-slot'

/**
 * Puts `children` in the top bar's slot, or where it is rendered if there is no top bar, as in tests. The
 * top bar must already be on the page when this first renders, as it is for any screen shown after the
 * Room has loaded.
 */
export function TopbarSlot({ children }: { children: ReactNode }) {
  const [slot] = useState(() => document.getElementById(TOPBAR_SLOT))
  return slot ? createPortal(children, slot) : children
}
