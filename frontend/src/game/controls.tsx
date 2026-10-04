// Controls shared by the Game screen's panels.

/**
 * A slider snapping to whole numbers from `min` to `max`, such as how many Units to train, with its range
 * and current value beside it. With nothing to choose (`max` below `min`) it is disabled and shows no numbers.
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
  return (
    <span className="count-slider">
      <span className="count-slider__bound">{min}</span>
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
      <span className="count-slider__bound">{max}</span>
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
