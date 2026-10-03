// Short retro tones synthesised with Web Audio, so the app needs no sound files.

/** The moments the interface marks with a sound. */
export type Cue = 'click' | 'endTurn' | 'newTurn' | 'completed' | 'battle' | 'rejection' | 'tick'

/** One tone of a cue: its pitch in Hz, when it starts and how long it lasts in seconds, and how loud it is. */
type Note = { frequency: number; at: number; length: number; wave: OscillatorType; loudness: number }

const note = (frequency: number, at: number, length: number, wave: OscillatorType = 'square', loudness = 0.5): Note => ({
  frequency,
  at,
  length,
  wave,
  loudness,
})

/** Each cue's tones, in the order they play. */
export const CUES: Record<Cue, Note[]> = {
  click: [note(660, 0, 0.04, 'square', 0.3)],
  endTurn: [note(523, 0, 0.08), note(784, 0.08, 0.14)],
  newTurn: [note(392, 0, 0.08, 'triangle', 0.7), note(523, 0.08, 0.08, 'triangle', 0.7), note(659, 0.16, 0.18, 'triangle', 0.7)],
  completed: [note(659, 0, 0.07), note(880, 0.07, 0.12)],
  battle: [note(110, 0, 0.18, 'sawtooth', 0.6), note(82, 0.16, 0.26, 'sawtooth', 0.6)],
  rejection: [note(220, 0, 0.09), note(165, 0.09, 0.16)],
  tick: [note(1200, 0, 0.02, 'triangle', 0.4)],
}

/** Whether `name` names a cue, such as a button's `data-cue`. */
export function isCue(name: string): name is Cue {
  return Object.hasOwn(CUES, name)
}

/** Plays `cue` through `context` at `volume`, from 0 (silent) to 1 (full). */
export function playCue(context: AudioContext, cue: Cue, volume: number) {
  if (volume <= 0) return
  const now = context.currentTime
  for (const { frequency, at, length, wave, loudness } of CUES[cue]) {
    const start = now + at
    const end = start + length
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = wave
    oscillator.frequency.setValueAtTime(frequency, start)
    // A quick attack and a decay to near silence, so tones don't click as they start and stop.
    gain.gain.setValueAtTime(0, start)
    gain.gain.linearRampToValueAtTime(loudness * volume, start + 0.005)
    gain.gain.exponentialRampToValueAtTime(0.0001, end)
    oscillator.connect(gain).connect(context.destination)
    oscillator.start(start)
    oscillator.stop(end + 0.02)
  }
}
