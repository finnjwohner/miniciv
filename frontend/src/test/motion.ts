// A stand-in for the operating system's motion preference, which jsdom lacks.
import { vi } from 'vitest'

/** Pretends the operating system does, or doesn't, ask for reduced motion. */
export function prefersReducedMotion(reduce: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: reduce && query === '(prefers-reduced-motion: reduce)',
    media: query,
    addEventListener() {},
    removeEventListener() {},
  }))
}
