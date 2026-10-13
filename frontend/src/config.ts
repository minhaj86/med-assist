export interface RuntimeConfig {
  apiBaseUrl: string
  oidcAuthority: string
  oidcClientId: string
  keycloakAdminUrl?: string
  environmentLabel?: string
}

declare global {
  interface Window {
    __MEDASSIST_CONFIG__?: Partial<RuntimeConfig>
  }
}

const defaults: RuntimeConfig = {
  apiBaseUrl: '/api',
  oidcAuthority: 'http://localhost:8180/realms/medassist',
  oidcClientId: 'medassist-web',
}

export const config: RuntimeConfig = { ...defaults, ...(window.__MEDASSIST_CONFIG__ ?? {}) }
