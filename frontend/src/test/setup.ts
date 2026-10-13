import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  // jsdom does not implement scrolling.
  Element.prototype.scrollIntoView = () => undefined
  window.scrollTo = () => undefined
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  window.sessionStorage.clear()
})

afterAll(() => server.close())
