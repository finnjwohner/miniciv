import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { message, renderGame, roomView } from '../test/fixtures'

/** The header button that toggles the Messages drawer. */
const toggle = () => screen.getByRole('button', { name: /^Messages/ })
const drawer = () => screen.getByRole('dialog', { name: 'Messages' })
/** The button opening the conversation with a Nation, in the open drawer. */
const conversation = (nation: string) => within(drawer()).getByRole('button', { name: new RegExp(`^${nation}`) })

describe('Messages drawer', () => {
  afterEach(() => localStorage.clear())

  it('raises the unread badge for an incoming Message, and clears it once the conversation is opened', () => {
    const { rerenderGame } = renderGame()
    expect(toggle()).toHaveAccessibleName('Messages')

    rerenderGame({ messages: [message(1, 'Truce?')] })

    expect(toggle()).toHaveAccessibleName('Messages, 1 unread')
    fireEvent.click(toggle())
    expect(conversation('Egypt')).toHaveAccessibleName('Egypt, 1 unread')

    fireEvent.click(conversation('Egypt'))

    expect(within(drawer()).getByText('Truce?')).toBeInTheDocument()
    expect(toggle()).toHaveAccessibleName('Messages')
    expect(conversation('Egypt')).toHaveAccessibleName('Egypt')
  })

  it('does not count the Player’s own Messages, or ones arriving in the open conversation, as unread', () => {
    const { rerenderGame } = renderGame()
    fireEvent.click(toggle())
    fireEvent.click(conversation('Egypt'))

    rerenderGame({ messages: [message(1, 'Truce?', { from: 'Rome', to: 'Egypt' }), message(2, 'Agreed.')] })

    expect(within(drawer()).getByText('Agreed.')).toBeInTheDocument()
    expect(toggle()).toHaveAccessibleName('Messages')
  })

  it('has one conversation per other Player, labelled with their Nation', () => {
    renderGame()
    fireEvent.click(toggle())

    const conversations = within(drawer()).getAllByRole('button', { name: /^(Rome|Egypt)/ })
    expect(conversations.map((c) => c.textContent)).toEqual(['Egypt'])
  })

  it('sends a Message to the open conversation’s Nation', () => {
    const onSendMessage = vi.fn()
    renderGame({ onSendMessage })
    fireEvent.click(toggle())
    fireEvent.click(conversation('Egypt'))

    fireEvent.change(within(drawer()).getByRole('textbox', { name: 'Message to Egypt' }), {
      target: { value: 'Truce?' },
    })
    fireEvent.click(within(drawer()).getByRole('button', { name: 'Send' }))

    expect(onSendMessage).toHaveBeenCalledWith('Egypt', 'Truce?')
    expect(within(drawer()).getByRole('textbox', { name: 'Message to Egypt' })).toHaveValue('')
  })

  it('cannot send to a Removed Nation', () => {
    renderGame({ room: roomView({ game: { ...roomView().game!, removed: ['Egypt'] } }) })
    fireEvent.click(toggle())
    fireEvent.click(conversation('Egypt'))

    expect(within(drawer()).queryByRole('textbox')).not.toBeInTheDocument()
    expect(within(drawer()).getByText(/Egypt was removed from the Game/)).toBeInTheDocument()
  })

  it('closes from the header toggle', () => {
    renderGame()
    fireEvent.click(toggle())
    expect(toggle()).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(toggle())

    expect(screen.queryByRole('dialog', { name: 'Messages' })).not.toBeInTheDocument()
  })
})
