import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { amount, nationView, renderGame, reportLine } from '../test/fixtures'

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
              lines: [reportLine('Sickness broke out in your Nation; choose what to do before this Turn ends.')],
            },
            { kind: 'WARFARE', lines: [] },
            { kind: 'COMPLETED', lines: [reportLine('Your Farm has been built.')] },
            {
              kind: 'RESOURCES',
              lines: [
                reportLine("Your Units' upkeep cost ", amount('GOLD', 3, 'LOSS'), '.'),
                reportLine('Your Buildings added ', amount('GOLD', 5, 'GAIN'), '.'),
              ],
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

  it('marks a resource amount with its icon in the loss colour, leaving the rest of the line plain', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 2,
          netChange: { gold: 0, population: 0, research: 0 },
          sections: [
            {
              kind: 'EVENTS',
              lines: [reportLine('You paid ', amount('GOLD', 20, 'LOSS'), ' to Repair your Walls.')],
            },
          ],
        },
      }),
    })

    const line = within(screen.getByRole('list', { name: 'Events' })).getByRole('listitem')
    expect(line).toHaveTextContent('You paid 20 Gold to Repair your Walls.')
    const paid = within(line).getByText('20 Gold')
    expect(paid).toHaveClass('report__loss')
    expect(paid.querySelector('[data-icon="coins"]')).toBeInTheDocument()
    expect([...line.querySelectorAll('.report__gain, .report__loss')]).toEqual([paid])
  })

  it('shows gains in green, and amounts about another Nation with their icon but no colour', () => {
    renderGame({
      nation: nationView({
        turnReport: {
          turn: 2,
          netChange: { gold: 0, population: 0, research: 0 },
          sections: [
            {
              kind: 'WARFARE',
              lines: [
                reportLine(
                  'Your Warparty took ',
                  amount('GOLD', 18, 'GAIN'),
                  ' and ',
                  amount('RESEARCH', 6, 'GAIN'),
                  ' from Carthage and is heading home.',
                ),
              ],
            },
            {
              kind: 'SPIES_AND_SCOUTS',
              lines: [reportLine('Your Spy in Carthage reported that Carthage has ', amount('POPULATION', 14, 'NEUTRAL'), '.')],
            },
          ],
        },
      }),
    })

    const warfare = screen.getByRole('list', { name: 'Warfare' })
    expect(within(warfare).getByText('18 Gold')).toHaveClass('report__gain')
    expect(within(warfare).getByText('6 Research')).toHaveClass('report__gain')
    expect(within(warfare).getByText('6 Research').querySelector('[data-icon="lightbulb"]')).toBeInTheDocument()
    const reported = within(screen.getByRole('list', { name: 'Spies & Scouts' })).getByText('14 Population')
    expect(reported).not.toHaveClass('report__gain')
    expect(reported).not.toHaveClass('report__loss')
    expect(reported.querySelector('[data-icon="human"]')).toBeInTheDocument()
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
          sections: [
            { kind: 'RESOURCES', lines: [reportLine("Your Units' upkeep cost ", amount('GOLD', 3, 'LOSS'), '.')] },
          ],
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
          sections: [{ kind: 'COMPLETED', lines: [reportLine('Your Farm has been built.')] }],
        },
      }),
    })

    expect(netChangeLine()).not.toBeInTheDocument()
  })
})
