import { useMe } from '../api/hooks'
import type { Role } from '../api/types'

export function useCurrentUser() {
  const me = useMe()
  const roles = me.data?.roles ?? []
  return {
    user: me.data,
    isLoading: me.isLoading,
    error: me.error,
    hasRole: (role: Role) => roles.includes(role),
    hasAnyRole: (...wanted: Role[]) => wanted.some((r) => roles.includes(r)),
  }
}
