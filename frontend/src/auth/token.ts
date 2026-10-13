import { User } from 'oidc-client-ts'
import { config } from '../config'

/** Reads the current access token from the oidc-client-ts session store (updated on silent renew). */
export function currentAccessToken(): string | undefined {
  const raw = window.sessionStorage.getItem(`oidc.user:${config.oidcAuthority}:${config.oidcClientId}`)
  if (!raw) return undefined
  const user = User.fromStorageString(raw)
  return user.expired ? undefined : user.access_token
}
