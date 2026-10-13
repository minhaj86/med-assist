import type { SystemHealth } from '../../api/types'

export const healthColor = (s: SystemHealth['overall']) => (s === 'UP' ? 'success' : s === 'DOWN' ? 'error' : s === 'DEGRADED' ? 'warning' : 'default')
