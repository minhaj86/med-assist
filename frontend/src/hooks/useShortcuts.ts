import { useEffect } from 'react'

const isTyping = (target: EventTarget | null) => {
  const el = target as HTMLElement | null
  return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable)
}

export interface Shortcut {
  /** e.g. "alt+r", "?", "/", "g d" (sequence) */
  keys: string
  handler: () => void
  allowInInputs?: boolean
}

/** Minimal keyboard shortcut support (single keys, modifier combos and two-key "g x" sequences). */
export function useShortcuts(shortcuts: Shortcut[]) {
  useEffect(() => {
    let pending: string | null = null
    let timer: number | undefined
    const onKeyDown = (event: KeyboardEvent) => {
      const key = event.key.toLowerCase()
      const combo = [event.ctrlKey && 'ctrl', event.metaKey && 'meta', event.altKey && 'alt', key].filter(Boolean).join('+')
      for (const s of shortcuts) {
        if (!s.allowInInputs && isTyping(event.target)) continue
        const sequence = s.keys.split(' ')
        if (sequence.length === 2) {
          if (pending === sequence[0] && key === sequence[1]) {
            event.preventDefault()
            pending = null
            s.handler()
            return
          }
        } else if (s.keys === combo || (s.keys === event.key && !event.ctrlKey && !event.metaKey && !event.altKey)) {
          event.preventDefault()
          s.handler()
          return
        }
      }
      if (!isTyping(event.target) && key === 'g' && !event.ctrlKey && !event.metaKey && !event.altKey) {
        pending = 'g'
        window.clearTimeout(timer)
        timer = window.setTimeout(() => { pending = null }, 1000)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      window.clearTimeout(timer)
    }
  }, [shortcuts])
}
