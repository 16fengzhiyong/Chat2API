export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: { code: string; message: string }
}

export interface Provider {
  id: string
  name: string
  type: string
  vendor: string
  authType: string
  apiEndpoint: string
  chatPath: string
  headers: Record<string, unknown>
  enabled: boolean
  description?: string
  supportedModels: string[]
  modelMappings: Record<string, unknown>
  status?: string
}

export interface Account {
  id: string
  providerId: string
  name: string
  email?: string
  status: string
  errorMessage?: string
  requestCount: number
  todayUsed: number
  dailyLimit?: number
  lastUsed?: string
}

export interface ApiKey {
  id: string
  name: string
  keyValue: string
  enabled: boolean
  usageCount: number
  description?: string
  allowedModels?: string[]
  createdAt: string
  lastUsedAt?: string
}

export interface RequestLog {
  id: string
  timestamp: string
  status: string
  statusCode: number
  method: string
  url: string
  model?: string
  actualModel?: string
  providerId?: string
  accountId?: string
  latency: number
  streamRequest: boolean
  errorMessage?: string
}

export interface SessionRecord {
  id: string
  providerId?: string
  accountId?: string
  model?: string
  status: string
  createdAt: string
  updatedAt: string
  expiresAt?: string
}

export interface SystemPrompt {
  id: string
  name: string
  category?: string
  builtin: boolean
  enabled: boolean
  content: string
  createdAt: string
  updatedAt: string
}
