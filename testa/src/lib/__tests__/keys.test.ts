import { describe, expect, it } from 'vitest'
import { keyLabel, matches } from '../keys'

const press = (key: string, mods: Partial<Record<'ctrlKey' | 'metaKey' | 'altKey' | 'shiftKey', boolean>> = {}) => ({
  key,
  ctrlKey: false,
  metaKey: false,
  altKey: false,
  shiftKey: false,
  ...mods,
})

describe('matches', () => {
  it('treats Ctrl and Command as mod', () => {
    expect(matches('mod+s', press('s', { ctrlKey: true }))).toBe(true)
    expect(matches('mod+s', press('s', { metaKey: true }))).toBe(true)
    expect(matches('mod+s', press('s'))).toBe(false)
  })

  it('does not take a modified key as a plain one', () => {
    expect(matches('n', press('n'))).toBe(true)
    expect(matches('n', press('n', { ctrlKey: true }))).toBe(false)
    expect(matches('n', press('N', { shiftKey: true }))).toBe(false)
    expect(matches('a', press('a', { altKey: true }))).toBe(false)
  })

  it('allows Shift where it is part of the symbol', () => {
    expect(matches('?', press('?', { shiftKey: true }))).toBe(true)
    expect(matches('alt+arrowup', press('ArrowUp', { altKey: true }))).toBe(true)
    expect(matches('space', press(' '))).toBe(true)
    expect(matches('escape', press('Escape'))).toBe(true)
  })
})

describe('keyLabel', () => {
  it('shows keys the way each system writes them', () => {
    expect(keyLabel('mod+d', false)).toBe('Ctrl D')
    expect(keyLabel('mod+d', true)).toBe('⌘ D')
    expect(keyLabel('alt+arrowup', false)).toBe('Alt ↑')
    expect(keyLabel('escape')).toBe('Esc')
  })
})
