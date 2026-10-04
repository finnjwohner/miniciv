import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nationView, renderGame } from '../test/fixtures'

/** The Turn Report panel. */
function turnReport() {
  return within(screen.getByRole('region', { name: 'Turn Report' }))
}

/** The Turn Report's net-change line: the paragraph naming a resource, if there is one. */
function netChangeLine() {
  return turnReport().queryByText(
    (_, element) => element?.tagName === 'P' && /Gold|Population|Research/.test(element.textContent ?? ''),
  )
}

/** The Turn Report's section headings, in the order shown. */
function sectionHeadings() {
  return turnReport()
    .getAllByRole('heading', { level: 4 })
    .map((heading) => heading.textContent)
}

describe('TurnReportPanel', () => {
  it('shows each section of the Turn Report under its heading, in order, hiding empty ones', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 2,
          netChange: { gold: 0, population: 0, research: 0 },
          sections: [
            {
              kind: 'DECISIONS_AND_ALERTS',
              lines: ['Sickness broke out in your Nation; choose what to do before this Turn ends.'],
            },
            { kind: 'WARFARE', lines: [] },
            { kind: 'COMPLETED', lines: ['Your Farm has been built.'] },
            {
              kind: 'RESOURCES',
              lines: ["Your Units' upkeep cost 3 Gold.", 'Your Buildings added 5 Gold.'],
            },
          ],
        },
      }),
    })

    expect(sectionHeadings()).toEqual(['Decisions & alerts', 'Completed', 'Resources'])
    const resources = screen.getByRole('list', { name: 'Resources' })
    expect(
      within(resources)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(["Your Units' upkeep cost 3 Gold.", 'Your Buildings added 5 Gold.'])
    expect(within(screen.getByRole('list', { name: 'Completed' })).getByRole('listitem')).toHaveTextContent(
      'Your Farm has been built.',
    )
  })

  it('says nothing has happened before the first Turn Report', () => {
    renderGame({ nation: nationView({ turnReport: null }) })

    expect(screen.getByText('Nothing has happened yet.')).toBeInTheDocument()
  })
})

describe('net change', () => {
  it('heads the Turn Report with how the resources moved, leaving out those that did not change', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 2,
          netChange: { gold: 14, population: 3, research: -2 },
          sections: [{ kind: 'RESOURCES', lines: ["Your Units' upkeep cost 3 Gold."] }],
        },
      }),
    })

    const line = netChangeLine()
    expect(line).toHaveTextContent('+14 Gold · +3 Population · −2 Research', {
      normalizeWhitespace: false,
    })
    expect(line?.compareDocumentPosition(turnReport().getByRole('heading', { name: 'Resources' }))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
  })

  it('leaves out every resource but the one that changed', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 3,
          netChange: { gold: -20, population: 0, research: 0 },
          sections: [],
        },
      }),
    })

    expect(netChangeLine()?.textContent).toBe('−20 Gold')
  })

  it('shows gains in green and losses in red', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 2,
          netChange: { gold: 14, population: 0, research: -2 },
          sections: [],
        },
      }),
    })

    expect(turnReport().getByText('+14 Gold')).toHaveClass('report__gain')
    expect(turnReport().getByText('−2 Research')).toHaveClass('report__loss')
  })

  it('is hidden when nothing changed', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 2,
          netChange: { gold: 0, population: 0, research: 0 },
          sections: [{ kind: 'COMPLETED', lines: ['Your Farm has been built.'] }],
        },
      }),
    })

    expect(netChangeLine()).not.toBeInTheDocument()
  })
})
