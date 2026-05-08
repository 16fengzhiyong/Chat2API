import { Activity, Database, KeyRound, Layers, RefreshCw, Server, Settings, UploadCloud } from 'lucide-react'
import { FormEvent, useEffect, useMemo, useState } from 'react'
import { api, getConfig, saveConfig, type AdminConfig } from './api'
import type { Account, ApiKey, Provider, RequestLog, SessionRecord, SystemPrompt } from './types'

type Tab = 'dashboard' | 'providers' | 'accounts' | 'keys' | 'models' | 'sessions' | 'prompts' | 'logs' | 'settings'

const tabs: Array<{ id: Tab; label: string; icon: typeof Activity }> = [
  { id: 'dashboard', label: '仪表盘', icon: Activity },
  { id: 'providers', label: 'Provider', icon: Layers },
  { id: 'accounts', label: '账号', icon: UploadCloud },
  { id: 'keys', label: 'API Keys', icon: KeyRound },
  { id: 'models', label: '模型映射', icon: Database },
  { id: 'sessions', label: '会话', icon: Server },
  { id: 'prompts', label: '提示/工具', icon: Settings },
  { id: 'logs', label: '日志统计', icon: Server },
  { id: 'settings', label: '设置', icon: Settings },
]

function App() {
  const [tab, setTab] = useState<Tab>('dashboard')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [health, setHealth] = useState<Record<string, unknown> | null>(null)
  const [providers, setProviders] = useState<Provider[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [apiKeys, setApiKeys] = useState<ApiKey[]>([])
  const [logs, setLogs] = useState<RequestLog[]>([])
  const [statistics, setStatistics] = useState<Record<string, unknown>>({})
  const [mappings, setMappings] = useState<Record<string, unknown>[]>([])
  const [sessions, setSessions] = useState<SessionRecord[]>([])
  const [prompts, setPrompts] = useState<SystemPrompt[]>([])
  const [toolCalling, setToolCalling] = useState<Record<string, unknown>>({})
  const [contextManagement, setContextManagement] = useState<Record<string, unknown>>({})
  const [config, setConfig] = useState<AdminConfig>(getConfig())

  const providerMap = useMemo(() => new Map(providers.map((provider) => [provider.id, provider])), [providers])

  async function refresh() {
    setLoading(true)
    setMessage('')
    try {
      const [healthData, providerData, accountData, keyData, logData, statData, mappingData, sessionData, promptData, toolCallingData, contextManagementData] = await Promise.all([
        api.health(),
        api.providers(),
        api.accounts(),
        api.apiKeys(),
        api.logs(),
        api.statistics(),
        api.modelMappings(),
        api.sessions(),
        api.systemPrompts(),
        api.toolCalling(),
        api.contextManagement(),
      ])
      setHealth(healthData)
      setProviders(providerData)
      setAccounts(accountData)
      setApiKeys(keyData)
      setLogs(logData)
      setStatistics(statData)
      setMappings(mappingData)
      setSessions(sessionData)
      setPrompts(promptData)
      setToolCalling(toolCallingData)
      setContextManagement(contextManagementData)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  async function createKey() {
    await api.createApiKey(`Key ${apiKeys.length + 1}`)
    await refresh()
  }

  async function checkProvider(id: string) {
    await api.checkProvider(id)
    await refresh()
  }

  async function refreshProviderModels(id: string) {
    await api.refreshProviderModels(id)
    setMessage('模型刷新已触发')
    await refresh()
  }

  async function clearProviderChats(id: string) {
    const result = await api.clearProviderChats(id)
    setMessage(JSON.stringify(result))
    await refresh()
  }

  async function showProviderCredits(id: string) {
    const result = await api.providerCredits(id)
    setMessage(JSON.stringify(result))
  }

  async function validateAccount(id: string) {
    const result = await api.validateAccount(id)
    setMessage(JSON.stringify(result))
    await refresh()
  }

  function accountBadgeClass(status: string) {
    const normalized = status.toLowerCase()
    if (normalized === 'active') {
      return 'badge ok'
    }
    if (normalized === 'error' || normalized === 'expired') {
      return 'badge danger'
    }
    return 'badge warn'
  }

  function updateSettings(event: FormEvent) {
    event.preventDefault()
    saveConfig(config)
    setMessage('配置已保存')
    refresh()
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">C2A</div>
          <div>
            <h1>Chat2API</h1>
            <p>三端分离管理后台</p>
          </div>
        </div>
        <nav>
          {tabs.map((item) => {
            const Icon = item.icon
            return (
              <button key={item.id} className={tab === item.id ? 'active' : ''} onClick={() => setTab(item.id)}>
                <Icon size={18} />
                {item.label}
              </button>
            )
          })}
        </nav>
      </aside>
      <main className="content">
        <header className="topbar">
          <div>
            <h2>{tabs.find((item) => item.id === tab)?.label}</h2>
            <p>Backend: {config.baseUrl}</p>
          </div>
          <button className="primary" onClick={refresh} disabled={loading}>
            <RefreshCw size={16} />
            {loading ? '刷新中' : '刷新'}
          </button>
        </header>
        {message && <div className="notice">{message}</div>}
        {tab === 'dashboard' && (
          <section className="grid cards">
            <Card title="服务状态" value={String(health?.status || 'unknown')} />
            <Card title="Provider" value={providers.length} />
            <Card title="账号" value={accounts.length} />
            <Card title="请求总数" value={String(statistics.totalRequests || 0)} />
            <Card title="成功请求" value={String(statistics.successRequests || 0)} />
            <Card title="失败请求" value={String(statistics.failedRequests || 0)} />
          </section>
        )}
        {tab === 'providers' && (
          <section className="panel-grid">
            {providers.map((provider) => (
              <article className="panel" key={provider.id}>
                <div className="row between">
                  <div>
                    <h3>{provider.name}</h3>
                    <p>{provider.id} / {provider.vendor}</p>
                  </div>
                  <span className={provider.enabled ? 'badge ok' : 'badge'}>{provider.enabled ? 'enabled' : 'disabled'}</span>
                </div>
                <p>{provider.apiEndpoint}{provider.chatPath}</p>
                <p>模型：{provider.supportedModels?.join(', ') || '-'}</p>
                <div className="actions">
                  <button onClick={() => checkProvider(provider.id)}>检查状态</button>
                  <button onClick={() => refreshProviderModels(provider.id)}>刷新模型</button>
                  <button onClick={() => showProviderCredits(provider.id)}>额度</button>
                  <button onClick={() => clearProviderChats(provider.id)}>清空聊天</button>
                </div>
              </article>
            ))}
          </section>
        )}
        {tab === 'accounts' && (
          <section className="table-panel">
            <table>
              <thead><tr><th>名称</th><th>Provider</th><th>状态</th><th>今日使用</th><th>请求数</th><th>操作</th></tr></thead>
              <tbody>
                {accounts.map((account) => (
                  <tr key={account.id}>
                    <td>{account.name}<br /><small>{account.email || account.id}</small></td>
                    <td>{providerMap.get(account.providerId)?.name || account.providerId}</td>
                    <td><span className={accountBadgeClass(account.status)} title={account.errorMessage || ''}>{account.status}</span></td>
                    <td>{account.todayUsed}/{account.dailyLimit || '∞'}</td>
                    <td>{account.requestCount}</td>
                    <td><button onClick={() => validateAccount(account.id)}>校验</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}
        {tab === 'keys' && (
          <section className="table-panel">
            <button className="primary" onClick={createKey}>创建 API Key</button>
            <table>
              <thead><tr><th>名称</th><th>Key</th><th>状态</th><th>使用次数</th></tr></thead>
              <tbody>{apiKeys.map((key) => <tr key={key.id}><td>{key.name}</td><td><code>{key.keyValue}</code></td><td>{key.enabled ? 'enabled' : 'disabled'}</td><td>{key.usageCount}</td></tr>)}</tbody>
            </table>
          </section>
        )}
        {tab === 'models' && (
          <section className="table-panel">
            <table>
              <thead><tr><th>请求模型</th><th>实际模型</th><th>Provider</th><th>账号</th></tr></thead>
              <tbody>{mappings.map((mapping) => <tr key={String(mapping.requestModel)}><td>{String(mapping.requestModel)}</td><td>{String(mapping.actualModel)}</td><td>{String(mapping.preferredProviderId || '-')}</td><td>{String(mapping.preferredAccountId || '-')}</td></tr>)}</tbody>
            </table>
          </section>
        )}
        {tab === 'sessions' && (
          <section className="table-panel">
            <table>
              <thead><tr><th>ID</th><th>Provider</th><th>账号</th><th>模型</th><th>状态</th><th>更新时间</th></tr></thead>
              <tbody>{sessions.map((session) => <tr key={session.id}><td>{session.id}</td><td>{session.providerId || '-'}</td><td>{session.accountId || '-'}</td><td>{session.model || '-'}</td><td>{session.status}</td><td>{session.updatedAt}</td></tr>)}</tbody>
            </table>
          </section>
        )}
        {tab === 'prompts' && (
          <section className="table-panel">
            <h3>Tool Calling</h3>
            <pre className="code-block">{JSON.stringify(toolCalling, null, 2)}</pre>
            <h3>Context Management</h3>
            <pre className="code-block">{JSON.stringify(contextManagement, null, 2)}</pre>
            <table>
              <thead><tr><th>名称</th><th>分类</th><th>启用</th><th>内置</th><th>内容</th></tr></thead>
              <tbody>{prompts.map((prompt) => <tr key={prompt.id}><td>{prompt.name}</td><td>{prompt.category || '-'}</td><td>{prompt.enabled ? '是' : '否'}</td><td>{prompt.builtin ? '是' : '否'}</td><td>{prompt.content}</td></tr>)}</tbody>
            </table>
          </section>
        )}
        {tab === 'logs' && (
          <section className="table-panel">
            <table>
              <thead><tr><th>时间</th><th>状态</th><th>模型</th><th>Provider</th><th>账号</th><th>耗时</th><th>错误</th></tr></thead>
              <tbody>{logs.slice(-100).reverse().map((log) => <tr key={log.id}><td>{log.timestamp}</td><td>{log.statusCode} {log.status}</td><td>{log.model}</td><td>{log.providerId}</td><td>{log.accountId}</td><td>{log.latency}ms</td><td>{log.errorMessage || '-'}</td></tr>)}</tbody>
            </table>
          </section>
        )}
        {tab === 'settings' && (
          <form className="settings" onSubmit={updateSettings}>
            <label>后端地址<input value={config.baseUrl} onChange={(event) => setConfig({ ...config, baseUrl: event.target.value })} /></label>
            <label>管理员用户名<input value={config.username} onChange={(event) => setConfig({ ...config, username: event.target.value })} /></label>
            <label>管理员密码<input type="password" value={config.password} onChange={(event) => setConfig({ ...config, password: event.target.value })} /></label>
            <button className="primary" type="submit">保存设置</button>
          </form>
        )}
      </main>
    </div>
  )
}

function Card({ title, value }: { title: string; value: string | number }) {
  return <article className="card"><p>{title}</p><strong>{value}</strong></article>
}

export default App
