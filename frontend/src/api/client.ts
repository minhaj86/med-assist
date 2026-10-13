import { config } from '../config'

export interface FieldError {
  field: string
  message: string
}

/** RFC 7807 problem details returned by the MedAssist API. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  correlationId?: string
  errors?: FieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`)
    this.status = status
    this.problem = problem
  }
}

let tokenProvider: () => string | undefined = () => undefined

/** Registered by the auth layer; the access token is kept in memory/session storage by oidc-client-ts. */
export function setAccessTokenProvider(provider: () => string | undefined) {
  tokenProvider = provider
}

type Body = object | FormData | string | undefined

async function request<T>(method: string, path: string, body?: Body, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  const token = tokenProvider()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (typeof body === 'string') {
    payload = body
  } else if (body !== undefined) {
    headers.set('Content-Type', 'application/json')
    payload = JSON.stringify(body)
  }
  if (!headers.has('Accept')) headers.set('Accept', 'application/json, application/problem+json')
  const response = await fetch(`${config.apiBaseUrl}${path}`, { ...init, method, headers, body: payload })
  if (!response.ok) {
    let problem: ProblemDetail = { status: response.status, title: response.statusText }
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      // non-JSON error body
    }
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('Content-Type') ?? ''
  if (contentType.includes('json')) return (await response.json()) as T
  return (await response.text()) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: Body, init?: RequestInit) => request<T>('POST', path, body, init),
  put: <T>(path: string, body?: Body) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: Body) => request<T>('PATCH', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
}

export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') search.set(k, String(v))
  })
  const s = search.toString()
  return s ? `?${s}` : ''
}
