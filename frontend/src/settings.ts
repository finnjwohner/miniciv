import { createContext, useContext } from 'react'
import type { Cue } from './sound'

/** The Player's settings for this page; they live in memory and reset on reload. */
export type Settings = { sound: boolean; volume: number; reducedMotion: boolean }

type SettingsContextValue = {
  settings: Settings
  changeSettings: (change: Partial<Settings>) => void
  play: (cue: Cue, after?: number) => void
}

/** What a SettingsProvider gives the screens below it. */
export const SettingsContext = createContext<SettingsContextValue | null>(null)

function useSettingsContext() {
  const context = useContext(SettingsContext)
  if (!context) throw new Error('useSettings and useCue need a SettingsProvider above them')
  return context
}

/** The settings and a way to change some of them. */
export function useSettings() {
  const { settings, changeSettings } = useSettingsContext()
  return { settings, changeSettings }
}

/** Plays a cue, if sound is on, at the chosen volume, now or a number of seconds after. */
export function useCue() {
  return useSettingsContext().play
}
