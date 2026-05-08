import type { ApiKey, ApiResponse, Account, Provider, RequestLog, SessionRecord, SystemPrompt } from './types'

export interface AdminConfig {
  baseUrl: string
  username: string
  password: string
}

const defaultConfig: AdminConfig = {
  baseUrl: localStorage.getItem('chat2api.baseUrl') || 'http://localhost:8080',
  username: localStorage.getItem('chat2api.username') || 'admin',
  password: localStorage.getItem('chat2api.password') || 'admin123456',
}

export function getConfig(): AdminConfig {
  return { ...defaultConfig }
}

export function saveConfig(config: AdminConfig) {
  defaultConfig.baseUrl = config.baseUrl
  defaultConfig.username = config.username
  defaultConfig.password = config.password
  localStorage.setItem('chat2api.baseUrl', config.baseUrl)
  localStorage.setItem('chat2api.username', config.username)
  localStorage.setItem('chat2api.password', config.password)
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const auth = btoa(`${defaultConfig.username}:${defaultConfig.password}`)
  const response = await fetch(`${defaultConfig.baseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Basic ${auth}`,
      ...(options.headers || {}),
    },
  })
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

export const api = {
  health: () => fetch(`${defaultConfig.baseUrl}/health`).then((res) => res.json()),
  providers: () => request<Provider[]>('/api/providers'),
  saveProvider: (provider: Record<string, unknown>) => request<Provider>('/api/providers', { method: 'POST', body: JSON.stringify(provider) }),
  checkProvider: (id: string) => request(`/api/providers/${id}/status`, { method: 'POST' }),
  refreshProviderModels: (id: string) => request<Provider>(`/api/providers/${id}/models/refresh`, { method: 'POST' }),
  clearProviderChats: (id: string) => request<Record<string, unknown>>(`/api/providers/${id}/clear-chats`, { method: 'POST' }),
  providerCredits: (id: string) => request<Record<string, unknown>>(`/api/providers/${id}/credits`),
  accounts: () => request<Account[]>('/api/accounts'),
  saveAccount: (account: Record<string, unknown>) => request<Account>('/api/accounts', { method: 'POST', body: JSON.stringify(account) }),
  validateAccount: (id: string) => request<Record<string, unknown>>(`/api/accounts/${id}/validate`, { method: 'POST' }),
  apiKeys: () => request<ApiKey[]>('/api/api-keys'),
  createApiKey: (name: string) => request<ApiKey>('/api/api-keys', { method: 'POST', body: JSON.stringify({ name }) }),
  modelMappings: () => request<Record<string, unknown>[]>('/api/model-mappings'),
  saveModelMapping: (mapping: Record<string, unknown>) => request('/api/model-mappings', { method: 'POST', body: JSON.stringify(mapping) }),
  logs: () => request<RequestLog[]>('/api/logs/requests'),
  statistics: () => request<Record<string, unknown>>('/api/logs/statistics'),
  sessions: () => request<SessionRecord[]>('/api/sessions'),
  systemPrompts: () => request<SystemPrompt[]>('/api/system-prompts'),
  contextManagement: () => request<Record<string, unknown>>('/api/context-management'),
  toolCalling: () => request<Record<string, unknown>>('/api/tool-calling'),
}
