// Controls shared by the Game screen's panels.

/** A small whole-number input, such as how many Units to train. */
export function CountInput({
  value,
  min,
  max,
  label,
  onChange,
}: {
  value: number
  min: number
  max?: number
  label: string
  onChange: (value: number) => void
}) {
  return (
    <input
      type="number"
      className="count-input"
      min={min}
      max={max}
      value={value}
      onChange={(e) => onChange(Math.floor(Number(e.target.value)))}
      aria-label={label}
    />
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
