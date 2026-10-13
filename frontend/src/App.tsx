import { useMemo } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { routes } from './routes'

/** Created only after sign-in so the router sees the post-login URL. */
export function App() {
  const router = useMemo(() => createBrowserRouter(routes), [])
  return <RouterProvider router={router} />
}
