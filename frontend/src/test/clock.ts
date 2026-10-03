// Letting time pass on Vitest's fake clock as a browser would.
import { act } from '@testing-library/react'
import { vi } from 'vitest'

/** Lets `millis` pass a quarter of a second at a time, letting the screen catch up after each, as a browser would. */
export function passes(millis: number) {
  for (let passed = 0; passed < millis; passed += 250) {
    act(() => {
      vi.advanceTimersByTime(Math.min(250, millis - passed))
    })
  }
}
