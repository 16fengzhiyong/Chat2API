import type { ApiKey, ApiResponse, Account, Provider, RequestLog, SessionRecord, SystemPrompt, AuthUser, ReporterRegistrationCode, CreatedReporterRegistrationCode } from './types'

export interface AdminConfig {
  token: string
  username?: string
  mustChangePassword?: boolean
}

const defaultConfig: AdminConfig = {
  token: localStorage.getItem('chat2api.token') || '',
  username: localStorage.getItem('chat2api.username') || '',
  mustChangePassword: localStorage.getItem('chat2api.mustChangePassword') === 'true',
}

export function getConfig(): AdminConfig {
  return { ...defaultConfig }
}

export function saveConfig(config: AdminConfig) {
  defaultConfig.token = config.token
  defaultConfig.username = config.username
  defaultConfig.mustChangePassword = Boolean(config.mustChangePassword)
  if (config.token) {
    localStorage.setItem('chat2api.token', config.token)
  } else {
    localStorage.removeItem('chat2api.token')
  }
  if (config.username) {
    localStorage.setItem('chat2api.username', config.username)
  } else {
    localStorage.removeItem('chat2api.username')
  }
  if (config.mustChangePassword) {
    localStorage.setItem('chat2api.mustChangePassword', 'true')
  } else {
    localStorage.removeItem('chat2api.mustChangePassword')
  }
}

export function clearSession() {
  defaultConfig.token = ''
  defaultConfig.username = ''
  defaultConfig.mustChangePassword = false
  localStorage.removeItem('chat2api.token')
  localStorage.removeItem('chat2api.username')
  localStorage.removeItem('chat2api.mustChangePassword')
}

export function isAuthenticated() {
  return Boolean(defaultConfig.token)
}

export function mustChangePassword() {
  return Boolean(defaultConfig.token && defaultConfig.mustChangePassword)
}

export async function login(username: string, password: string) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const payload = (await response.json()) as ApiResponse<{ token: string; user?: AuthUser }>
  if (!response.ok || !payload.success || !payload.data?.token) {
    throw new Error(payload.error?.message || 'Login failed')
  }
  saveConfig({ token: payload.data.token, username: payload.data.user?.username || username, mustChangePassword: Boolean(payload.data.user?.mustChangePassword) })
  return payload.data
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(defaultConfig.token ? { Authorization: `Bearer ${defaultConfig.token}` } : {}),
      ...(options.headers || {}),
    },
  })
  if (response.status === 401) {
    clearSession()
    window.dispatchEvent(new Event('chat2api:unauthorized'))
  }
  if (!response.ok) {
    throw new Error(await response.text())
  }
  const payload = (await response.json()) as ApiResponse<T> | T
  if (typeof payload === 'object' && payload !== null && 'success' in payload) {
    const wrapped = payload as ApiResponse<T>
    if (!wrapped.success) {
      throw new Error(wrapped.error?.message || 'Request failed')
    }
    return wrapped.data as T
  }
  return payload as T
}

export async function refreshCurrentUser() {
  const user = await request<AuthUser>('/api/auth/me')
  saveConfig({ ...defaultConfig, username: user.username, mustChangePassword: user.mustChangePassword })
  return user
}

export const api = {
  health: () => fetch('/health').then((res) => res.json()),
  me: () => refreshCurrentUser(),
  changePassword: (currentPassword: string, newPassword: string) => request<Record<string, unknown>>('/api/auth/change-password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) }).then((result) => {
    saveConfig({ ...defaultConfig, mustChangePassword: false })
    return result
  }),
  logout: () => request<Record<string, unknown>>('/api/auth/logout', { method: 'POST' }).finally(clearSession),
  providers: () => request<Provider[]>('/api/providers'),
  updateProvider: (id: string, provider: Record<string, unknown>) => request<Provider>(`/api/providers/${id}`, { method: 'PUT', body: JSON.stringify(provider) }),
  deleteProvider: (id: string) => request<Record<string, unknown>>(`/api/providers/${id}`, { method: 'DELETE' }),
  checkProvider: (id: string) => request(`/api/providers/${id}/status`, { method: 'POST' }),
  refreshProviderModels: (id: string) => request<Provider>(`/api/providers/${id}/models/refresh`, { method: 'POST' }),
  clearProviderChats: (id: string) => request<Record<string, unknown>>(`/api/providers/${id}/clear-chats`, { method: 'POST' }),
  providerCredits: (id: string) => request<Record<string, unknown>>(`/api/providers/${id}/credits`),
  accounts: () => request<Account[]>('/api/accounts'),
  updateAccount: (id: string, account: Record<string, unknown>) => request<Account>(`/api/accounts/${id}`, { method: 'PUT', body: JSON.stringify(account) }),
  deleteAccount: (id: string) => request<Record<string, unknown>>(`/api/accounts/${id}`, { method: 'DELETE' }),
  validateAccount: (id: string) => request<Record<string, unknown>>(`/api/accounts/${id}/validate`, { method: 'POST' }),
  apiKeys: () => request<ApiKey[]>('/api/api-keys'),
  createApiKey: (name: string, description?: string, allowedModels?: string[]) => request<ApiKey>('/api/api-keys', { method: 'POST', body: JSON.stringify({ name, description, allowedModels }) }),
  updateApiKey: (id: string, data: Record<string, unknown>) => request<ApiKey>(`/api/api-keys/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteApiKey: (id: string) => request<Record<string, unknown>>(`/api/api-keys/${id}`, { method: 'DELETE' }),
  reporterRegistrationCodes: () => request<ReporterRegistrationCode[]>('/api/reporter-registration-codes'),
  createReporterRegistrationCode: (data: Record<string, unknown>) => request<CreatedReporterRegistrationCode>('/api/reporter-registration-codes', { method: 'POST', body: JSON.stringify(data) }),
  updateReporterRegistrationCode: (id: string, data: Record<string, unknown>) => request<ReporterRegistrationCode>(`/api/reporter-registration-codes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteReporterRegistrationCode: (id: string) => request<Record<string, unknown>>(`/api/reporter-registration-codes/${id}`, { method: 'DELETE' }),
  modelMappings: () => request<Record<string, unknown>[]>('/api/model-mappings'),
  saveModelMapping: (mapping: Record<string, unknown>) => request('/api/model-mappings', { method: 'POST', body: JSON.stringify(mapping) }),
  deleteModelMapping: (model: string) => request<Record<string, unknown>>(`/api/model-mappings/${encodeURIComponent(model)}`, { method: 'DELETE' }),
  logs: () => request<RequestLog[]>('/api/logs/requests'),
  statistics: () => request<Record<string, unknown>>('/api/logs/statistics'),
  dailyStatistics: () => request<Record<string, unknown>[]>('/api/logs/statistics/daily'),
  sessions: () => request<SessionRecord[]>('/api/sessions'),
  deleteSession: (id: string) => request<Record<string, unknown>>(`/api/sessions/${id}`, { method: 'DELETE' }),
  clearSessions: () => request<Record<string, unknown>>('/api/sessions', { method: 'DELETE' }),
  systemPrompts: () => request<SystemPrompt[]>('/api/system-prompts'),
  saveSystemPrompt: (prompt: Record<string, unknown>) => request<SystemPrompt>('/api/system-prompts', { method: 'POST', body: JSON.stringify(prompt) }),
  deleteSystemPrompt: (id: string) => request<Record<string, unknown>>(`/api/system-prompts/${id}`, { method: 'DELETE' }),
  contextManagement: () => request<Record<string, unknown>>('/api/context-management'),
  saveContextManagement: (value: string) => request<Record<string, unknown>>('/api/context-management', { method: 'POST', body: JSON.stringify({ value }) }),
  toolCalling: () => request<Record<string, unknown>>('/api/tool-calling'),
  saveToolCalling: (value: string) => request<Record<string, unknown>>('/api/tool-calling', { method: 'POST', body: JSON.stringify({ value }) }),
  exportData: () => request<Record<string, unknown>>('/api/data/export'),
  importData: (payload: Record<string, unknown>) => request<Record<string, unknown>>('/api/data/import', { method: 'POST', body: JSON.stringify(payload) }),
  loadBalanceConfig: () => request<Record<string, unknown>>('/api/load-balance/config'),
  saveLoadBalanceConfig: (value: Record<string, unknown>) => request<Record<string, unknown>>('/api/load-balance/config', { method: 'POST', body: JSON.stringify({ value: JSON.stringify(value) }) }),
  proxyConfig: () => request<Record<string, unknown>>('/api/proxy/config'),
  saveProxyConfig: (value: Record<string, unknown>) => request<Record<string, unknown>>('/api/proxy/config', { method: 'POST', body: JSON.stringify({ value: JSON.stringify(value) }) }),
  getConfig: (key: string) => request<Record<string, unknown>>(`/api/config/${key}`),
  saveConfig: (key: string, value: string) => request<Record<string, unknown>>('/api/config', { method: 'POST', body: JSON.stringify({ key, value }) }),
  sessionConfig: () => request<Record<string, unknown>>('/api/sessions/config'),
  saveSessionConfig: (value: string) => request<Record<string, unknown>>('/api/sessions/config', { method: 'POST', body: JSON.stringify({ value }) }),
  accountCredentials: (id: string) => request<Record<string, unknown>>(`/api/accounts/${id}/credentials`),
}
