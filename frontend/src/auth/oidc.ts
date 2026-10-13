import { WebStorageStateStore, type User } from 'oidc-client-ts'
import type { AuthProviderProps } from 'react-oidc-context'
import { config } from '../config'

/** Only same-origin relative paths are accepted as post-login targets (prevents open redirects). */
export function safeReturnPath(state: unknown): string {
  const returnTo = (state as { returnTo?: unknown } | undefined)?.returnTo
  return typeof returnTo === 'string' && returnTo.startsWith('/') && !returnTo.startsWith('//') ? returnTo : '/'
}

export const oidcConfig: AuthProviderProps = {
  authority: config.oidcAuthority,
  client_id: config.oidcClientId,
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  scope: 'openid profile email',
  // Tokens stay in sessionStorage (cleared with the tab), never in localStorage.
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true,
  onSigninCallback: (user: User | void) => {
    window.history.replaceState({}, document.title, safeReturnPath(user?.state))
  },
}
