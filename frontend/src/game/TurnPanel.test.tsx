import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame } from '../test/fixtures'

/** The Turn Report's section headings, in the order shown. */
function sectionHeadings() {
  return within(screen.getByRole('region', { name: 'Turn Report' }))
    .getAllByRole('heading', { level: 4 })
    .map((heading) => heading.textContent)
}

describe('TurnPanel', () => {
  it('shows each section of the Turn Report under its heading, in order, hiding empty ones', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 2,
          sections: [
            { kind: 'DECISIONS_AND_ALERTS', lines: ['Sickness broke out in your Nation; choose what to do before this Turn ends.'] },
            { kind: 'WARFARE', lines: [] },
            { kind: 'COMPLETED', lines: ['Your Farm has been built.'] },
            { kind: 'RESOURCES', lines: ['Your Units\' upkeep cost 3 Gold.', 'Your Buildings added 5 Gold.'] },
          ],
        },
      }),
    })

    expect(sectionHeadings()).toEqual(['Decisions & alerts', 'Completed', 'Resources'])
    const resources = screen.getByRole('list', { name: 'Resources' })
    expect(within(resources).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      "Your Units' upkeep cost 3 Gold.",
      'Your Buildings added 5 Gold.',
    ])
    expect(within(screen.getByRole('list', { name: 'Completed' })).getByRole('listitem')).toHaveTextContent(
      'Your Farm has been built.',
    )
  })

  it('says nothing has happened before the first Turn Report', () => {
    renderGame({ nation: nationView({ turnReport: null }) })

    expect(screen.getByText('Nothing has happened yet.')).toBeInTheDocument()
  })
})
