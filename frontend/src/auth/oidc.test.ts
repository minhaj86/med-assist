import { describe, expect, it } from 'vitest'
import { safeReturnPath } from './oidc'

describe('safeReturnPath', () => {
  it('accepts same-origin relative paths', () => {
    expect(safeReturnPath({ returnTo: '/encounters/abc?tab=1' })).toBe('/encounters/abc?tab=1')
  })

  it.each([
    [undefined], [{}], [{ returnTo: 'https://evil.example/' }], [{ returnTo: '//evil.example/x' }], [{ returnTo: 42 }],
  ])('falls back to the root for unsafe state %j', (state) => {
    expect(safeReturnPath(state)).toBe('/')
  })
})
