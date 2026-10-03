// Suggested names for a Nation's Ruler, offered in the Room; each at most 20 characters, the server's limit.

const suggestions = [
  'Queen Aster',
  'King Bram',
  'Empress Calla',
  'Lord Dorran',
  'High Chief Eda',
  'Prince Faolan',
  'Duchess Greer',
  'Khan Harun',
  'Queen Isolde',
  'King Jory',
  'Pharaoh Kesi',
  'Tsar Lev',
  'Empress Mirela',
  'Warlord Nox',
  'Queen Oriel',
  'Shah Parviz',
  'Regent Quill',
  'King Rhodri',
  'Sultana Sabah',
  'Emperor Taro',
]

/** A random suggested Ruler name, never the one given, so a reroll always changes it. */
export function suggestRulerName(current = ''): string {
  const others = suggestions.filter((name) => name !== current)
  return others[Math.floor(Math.random() * others.length)]
}
