import { useEffect, useId, useRef, useState } from 'react'
import { useSettings } from '../settings'
import { Icon } from './Icon'

/**
 * The gear at the top right of every screen and the Settings pop-up it opens. Escape, the gear again or
 * a click anywhere outside the pop-up closes it.
 */
export function SettingsMenu() {
  const { settings, changeSettings } = useSettings()
  const [open, setOpen] = useState(false)
  const menu = useRef<HTMLDivElement>(null)
  const gear = useRef<HTMLButtonElement>(null)
  const titleId = useId()

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      setOpen(false)
      gear.current?.focus()
    }
    const onPointerDown = (e: PointerEvent) => {
      if (!menu.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    document.addEventListener('pointerdown', onPointerDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.removeEventListener('pointerdown', onPointerDown)
    }
  }, [open])

  return (
    <div className="settings" ref={menu}>
      <button
        ref={gear}
        className="settings__gear"
        aria-label="Settings"
        title="Settings"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <Icon name="gear" size={24} />
      </button>
      {open && (
        <div className="settings__popup panel" role="dialog" aria-labelledby={titleId}>
          <header className="panel__bar">
            <h2 className="panel__title" id={titleId}>
              Settings
            </h2>
          </header>
          <div className="panel__body stack">
            <label className="toggle">
              <input
                type="checkbox"
                checked={settings.sound}
                onChange={(e) => changeSettings({ sound: e.target.checked })}
              />
              Sound
            </label>
            <label className="field">
              <span className="field__label">Volume</span>
              <input
                type="range"
                min={0}
                max={100}
                value={Math.round(settings.volume * 100)}
                disabled={!settings.sound}
                onChange={(e) => changeSettings({ volume: Number(e.target.value) / 100 })}
              />
            </label>
            <label className="toggle">
              <input
                type="checkbox"
                checked={settings.reducedMotion}
                onChange={(e) => changeSettings({ reducedMotion: e.target.checked })}
              />
              Reduce motion
            </label>
          </div>
        </div>
      )}
    </div>
  )
}
