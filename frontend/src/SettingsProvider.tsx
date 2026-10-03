import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { SettingsContext, type Settings } from './settings'
import { isCue, playCue, type Cue } from './sound'

/** Sound on at a modest volume, and reduced motion if the operating system asks for it. */
function initialSettings(): Settings {
  return {
    sound: true,
    volume: 0.4,
    reducedMotion: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false,
  }
}

/**
 * Holds the settings and gives every screen below it click feedback: each button plays the click cue, or
 * the cue its `data-cue` names. Browsers only let a page make sound after a user gesture, so the audio
 * context is created on the first one. While motion is on, the page carries `data-motion` for the
 * styles to animate by.
 */
export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState(initialSettings)
  const audio = useRef<AudioContext | null>(null)

  useEffect(() => {
    const unlock = () => {
      if (typeof AudioContext === 'undefined') return
      audio.current ??= new AudioContext()
      if (audio.current.state === 'suspended') void audio.current.resume()
    }
    const gestures = ['pointerdown', 'keydown', 'click'] as const
    gestures.forEach((gesture) => document.addEventListener(gesture, unlock, { capture: true }))
    return () => gestures.forEach((gesture) => document.removeEventListener(gesture, unlock, { capture: true }))
  }, [])

  const play = useCallback(
    (cue: Cue, after = 0) => {
      if (settings.sound && audio.current) playCue(audio.current, cue, settings.volume, after)
    },
    [settings.sound, settings.volume],
  )

  // Heard while capturing, before React handles the click, which may disable the button.
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      const button = e.target instanceof Element ? e.target.closest('button') : null
      if (!button || button.disabled) return
      const cue = button.dataset.cue
      play(cue && isCue(cue) ? cue : 'click')
    }
    document.addEventListener('click', onClick, { capture: true })
    return () => document.removeEventListener('click', onClick, { capture: true })
  }, [play])

  useEffect(() => {
    const root = document.documentElement
    if (settings.reducedMotion) delete root.dataset.motion
    else root.dataset.motion = ''
    return () => {
      delete root.dataset.motion
    }
  }, [settings.reducedMotion])

  const value = useMemo(
    () => ({ settings, changeSettings: (change: Partial<Settings>) => setSettings((s) => ({ ...s, ...change })), play }),
    [settings, play],
  )
  return <SettingsContext value={value}>{children}</SettingsContext>
}
