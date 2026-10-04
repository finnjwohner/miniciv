// Controls shared by the Game screen's panels.

import { graduations } from './graduations'

/**
 * A slider snapping to whole numbers from `min` to `max`, such as how many Units to train, drawn with
 * graduations beneath it like a measuring rule and the current value beside it. With nothing to choose it shows no
 * slider: just the one value as text when `min` and `max` meet, or disabled with no numbers when `max` is
 * below `min`.
 */
export function CountSlider({
  value,
  min,
  max,
  label,
  onChange,
}: {
  value: number
  min: number
  max: number
  label: string
  onChange: (value: number) => void
}) {
  if (max < min) {
    return (
      <span className="count-slider">
        <input type="range" className="count-slider__input" min={0} max={0} value={0} disabled aria-label={label} />
      </span>
    )
  }
  if (max === min) {
    return (
      <span className="count-slider">
        <output className="count-slider__value" aria-label={label}>
          {min}
        </output>
      </span>
    )
  }
  return (
    <span className="count-slider">
      <span className="count-slider__scale">
        <input
          type="range"
          className="count-slider__input"
          min={min}
          max={max}
          step={1}
          value={value}
          onChange={(e) => onChange(Math.round(Number(e.target.value)))}
          aria-label={label}
        />
        <span className="count-slider__ticks" aria-hidden="true">
          {graduations(min, max).map((mark) => (
            <span
              key={mark.value}
              className={mark.major ? 'count-slider__tick count-slider__tick--major' : 'count-slider__tick'}
              style={{ left: `${((mark.value - min) / (max - min)) * 100}%` }}
            >
              {mark.labelled && <span className="count-slider__tick-label">{mark.value}</span>}
            </span>
          ))}
        </span>
      </span>
      <output className="count-slider__value">{value}</output>
    </span>
  )
}

/** Picks another Nation still in the Game to send something to. */
export function TargetSelect({
  targets,
  value,
  label,
  onChange,
}: {
  targets: string[]
  value: string
  label: string
  onChange: (target: string) => void
}) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        {targets.map((n) => (
          <option key={n} value={n}>
            {n}
          </option>
        ))}
      </select>
    </label>
  )
}
