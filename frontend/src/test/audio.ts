// A stand-in for the browser's Web Audio, which jsdom lacks, recording every tone played.
import { fireEvent } from '@testing-library/react'
import { vi } from 'vitest'
import { CUES, type Cue } from '../sound'

/** A tone a cue played: its pitch in Hz and its loudest volume, from 0 to 1. */
export type Tone = { frequency: number; peak: number }

/**
 * Replaces `AudioContext` with a fake for the current test and returns the tones played through it, in
 * the order they were started.
 */
export function fakeWebAudio(): Tone[] {
  const tones: Tone[] = []
  class FakeParam {
    value = 0
    peak = 0
    setValueAtTime(value: number) {
      this.track(value)
    }
    linearRampToValueAtTime(value: number) {
      this.track(value)
    }
    exponentialRampToValueAtTime(value: number) {
      this.track(value)
    }
    private track(value: number) {
      this.value = value
      this.peak = Math.max(this.peak, value)
    }
  }
  class FakeNode {
    connect<T>(destination: T) {
      return destination
    }
  }
  class FakeGain extends FakeNode {
    gain = new FakeParam()
  }
  class FakeOscillator extends FakeNode {
    type = 'sine'
    frequency = new FakeParam()
    private output: FakeGain | null = null
    connect<T>(destination: T) {
      if (destination instanceof FakeGain) this.output = destination
      return destination
    }
    start() {
      tones.push({ frequency: this.frequency.value, peak: this.output?.gain.peak ?? 1 })
    }
    stop() {}
  }
  class FakeAudioContext {
    currentTime = 0
    state = 'running'
    destination = new FakeNode()
    createOscillator() {
      return new FakeOscillator()
    }
    createGain() {
      return new FakeGain()
    }
    resume() {
      return Promise.resolve()
    }
  }
  vi.stubGlobal('AudioContext', FakeAudioContext)
  return tones
}

/** How many times `cue` played among `tones`, by the pitches of its notes in turn. */
export function timesPlayed(tones: Tone[], cue: Cue): number {
  const pitches = CUES[cue].map((n) => n.frequency)
  let times = 0
  for (let i = 0; i + pitches.length <= tones.length; i++) {
    if (pitches.every((pitch, j) => tones[i + j].frequency === pitch)) times++
  }
  return times
}

/** Browsers only let a page make sound after a gesture, so tests make one before listening. */
export function gesture() {
  fireEvent.pointerDown(document.body)
}
