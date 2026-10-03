import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Without Vitest globals, Testing Library cannot unmount by itself between tests.
afterEach(cleanup)

// Tests stand in for browser APIs jsdom lacks, such as Web Audio, with vi.stubGlobal.
afterEach(() => {
  vi.unstubAllGlobals()
})
