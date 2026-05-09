import { Activity, Database, KeyRound, Layers, RefreshCw, Server, Settings, UploadCloud, Trash2, Edit3, ShieldCheck } from 'lucide-react'
import { FormEvent, useEffect, useMemo, useState } from 'react'
import { api, getConfig, saveConfig, type AdminConfig } from './api'
import type { Account, ApiKey, Provider, RequestLog, SessionRecord, SystemPrompt } from './types'

type Tab = 'dashboard' | 'providers' | 'accounts' | 'keys' | 'models' | 'sessions' | 'prompts' | 'logs' | 'settings'

const tabs: Array<{ id: Tab; label: string; icon: typeof Activity; desc: string }> = [
  { id: 'dashboard', label: '仪表盘', icon: Activity, desc: '运行状态与核心指标' },
  { id: 'providers', label: 'Provider', icon: Layers, desc: '仅展示已迁移服务商' },
  { id: 'accounts', label: '账号', icon: UploadCloud, desc: '账号凭据与可用性' },
  { id: 'keys', label: 'API Keys', icon: KeyRound, desc: 'OpenAI 兼容访问密钥' },
  { id: 'models', label: '模型映射', icon: Database, desc: '请求模型到实际模型映射' },
  { id: 'sessions', label: '会话', icon: Server, desc: '多轮上下文会话管理' },
  { id: 'prompts', label: '提示/工具', icon: Settings, desc: 'Tool Calling 与上下文配置' },
  { id: 'logs', label: '日志统计', icon: Server, desc: '最近请求与失败原因' },
  { id: 'settings', label: '设置', icon: Settings, desc: '后台地址与管理凭据' },
]

const migratedProviders = new Set(['zai', 'qwen-ai'])

function App() {
  const [tab, setTab] = useState<Tab>('dashboard')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [messageType, setMessageType] = useState<'info' | 'error'>('info')
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

  const visibleProviders = useMemo(() => providers.filter((provider) => migratedProviders.has(provider.id)), [providers])
  const visibleProviderIds = useMemo(() => new Set(visibleProviders.map((provider) => provider.id)), [visibleProviders])
  const visibleAccounts = useMemo(() => accounts.filter((account) => visibleProviderIds.has(account.providerId)), [accounts, visibleProviderIds])
  const providerMap = useMemo(() => new Map(visibleProviders.map((provider) => [provider.id, provider])), [visibleProviders])
  const currentTab = tabs.find((item) => item.id === tab) || tabs[0]

  function showMessage(value: string, type: 'info' | 'error' = 'info') {
    setMessage(value)
    setMessageType(type)
    setTimeout(() => setMessage(''), 4200)
  }

  async function refresh() {
    setLoading(true)
    setMessage('')
    try {
      const [healthData, providerData, accountData, keyData, logData, statData, mappingData, sessionData, promptData, toolCallingData, contextManagementData] = await Promise.all([
        api.health(), api.providers(), api.accounts(), api.apiKeys(), api.logs(), api.statistics(), api.modelMappings(), api.sessions(), api.systemPrompts(), api.toolCalling(), api.contextManagement(),
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
      showMessage(error instanceof Error ? error.message : '加载失败', 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [])

  function jsonPrompt(title: string, value: Record<string, unknown> = {}) {
    const input = window.prompt(title, JSON.stringify(value, null, 2))
    if (!input) return null
    return JSON.parse(input) as Record<string, unknown>
  }

  async function createKey() {
    const name = window.prompt('API Key 名称', `Key ${apiKeys.length + 1}`)
    if (!name) return
    await api.createApiKey(name)
    showMessage('API Key 已创建')
    await refresh()
  }

  async function saveProviderJson(provider?: Provider) {
    const payload = jsonPrompt(provider ? '编辑 Provider JSON' : '创建 Provider JSON', provider ? { ...provider } : { name: 'Custom Provider', vendor: 'custom', authType: 'apiKey', apiEndpoint: 'https://api.example.com', chatPath: '/v1/chat/completions', enabled: true, supportedModels: [], modelMappings: {}, headers: {} })
    if (!payload) return
    if (provider) await api.updateProvider(provider.id, payload)
    else await api.saveProvider(payload)
    showMessage('Provider 已保存')
    await refresh()
  }

  async function deleteProvider(id: string) {
    if (!window.confirm('确认删除该 Provider？')) return
    await api.deleteProvider(id)
    showMessage('Provider 已删除')
    await refresh()
  }

  async function saveAccountJson(account?: Account) {
    const payload = jsonPrompt(account ? '编辑账号 JSON' : '创建账号 JSON', account ? { name: account.name, email: account.email, status: account.status, dailyLimit: account.dailyLimit } : { providerId: visibleProviders[0]?.id || 'zai', name: 'Account', email: '', credentials: {}, dailyLimit: null })
    if (!payload) return
    if (account) await api.updateAccount(account.id, payload)
    else await api.saveAccount(payload)
    showMessage('账号已保存')
    await refresh()
  }

  async function saveModelMappingJson(mapping?: Record<string, unknown>) {
    const payload = jsonPrompt(mapping ? '编辑模型映射 JSON' : '创建模型映射 JSON', mapping || { requestModel: '', actualModel: '', preferredProviderId: visibleProviders[0]?.id || 'zai', preferredAccountId: '' })
    if (!payload) return
    await api.saveModelMapping(payload)
    showMessage('模型映射已保存')
    await refresh()
  }

  async function saveSystemPromptJson(prompt?: SystemPrompt) {
    const payload = jsonPrompt(prompt ? '编辑系统提示 JSON' : '创建系统提示 JSON', prompt ? { ...prompt } : { name: 'Prompt', category: 'custom', enabled: true, builtin: false, content: '' })
    if (!payload) return
    await api.saveSystemPrompt(payload)
    showMessage('系统提示已保存')
    await refresh()
  }

  async function runAction(action: () => Promise<unknown>, success: string) {
    try {
      const result = await action()
      showMessage(typeof result === 'undefined' ? success : JSON.stringify(result))
      await refresh()
    } catch (error) {
      showMessage(error instanceof Error ? error.message : '操作失败', 'error')
    }
  }

  async function saveToolCallingConfig() {
    const input = window.prompt('保存 Tool Calling JSON', JSON.stringify(toolCalling, null, 2))
    if (!input) return
    JSON.parse(input)
    await api.saveToolCalling(input)
    showMessage('Tool Calling 已保存')
    await refresh()
  }

  async function saveContextConfig() {
    const input = window.prompt('保存 Context Management JSON', JSON.stringify(contextManagement, null, 2))
    if (!input) return
    JSON.parse(input)
    await api.saveContextManagement(input)
    showMessage('Context 配置已保存')
    await refresh()
  }

  async function exportData() {
    const result = await api.exportData()
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: 'application/json' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `chat2api-export-${Date.now()}.json`
    link.click()
    URL.revokeObjectURL(link.href)
    showMessage('导出完成')
  }

  async function importData() {
    const input = window.prompt('粘贴导入 JSON')
    if (!input) return
    const result = await api.importData(JSON.parse(input) as Record<string, unknown>)
    showMessage(JSON.stringify(result))
    await refresh()
  }

  function updateSettings(event: FormEvent) {
    event.preventDefault()
    saveConfig(config)
    showMessage('配置已保存')
    refresh()
  }

  return (
    <div className="flex min-h-screen bg-[radial-gradient(circle_at_top_left,#eef2ff_0,#f8fafc_38%,#f8fafc_100%)]">
      <aside className="fixed inset-y-0 left-0 z-20 flex w-72 flex-col bg-slate-950 text-white shadow-2xl shadow-slate-900/20">
        <div className="p-6">
          <div className="mb-8 flex items-center gap-3">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-indigo-500 via-violet-500 to-fuchsia-500 text-lg font-black shadow-lg shadow-indigo-500/30">C2A</div>
            <div><h1 className="text-lg font-bold">Chat2API</h1><p className="text-xs text-slate-400">三端分离管理后台</p></div>
          </div>
          <nav className="space-y-1">
            {tabs.map((item) => {
              const Icon = item.icon
              const active = tab === item.id
              return <button key={item.id} onClick={() => setTab(item.id)} className={`flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left text-sm font-medium transition ${active ? 'bg-white/15 text-white shadow-lg' : 'text-slate-400 hover:bg-white/10 hover:text-white'}`}><Icon size={18} />{item.label}</button>
            })}
          </nav>
        </div>
        <div className="mt-auto border-t border-white/10 p-6 text-xs text-slate-400"><div className="flex items-center gap-2"><span className={`h-2 w-2 rounded-full ${health ? 'bg-emerald-400' : 'bg-slate-600'}`} />{config.baseUrl}</div></div>
      </aside>

      <main className="ml-72 min-h-screen flex-1">
        <header className="sticky top-0 z-10 border-b border-slate-200/70 bg-white/70 px-8 py-5 backdrop-blur-xl">
          <div className="flex items-center justify-between">
            <div><h2 className="text-2xl font-bold text-slate-900">{currentTab.label}</h2><p className="mt-1 text-sm text-slate-500">{currentTab.desc}</p></div>
            <button className="btn-primary" onClick={refresh} disabled={loading}><RefreshCw size={16} className={loading ? 'animate-spin' : ''} />{loading ? '刷新中' : '刷新'}</button>
          </div>
        </header>

        <section className="p-8">
          {message && <div className={messageType === 'error' ? 'toast-error' : 'toast-info'}>{message}</div>}

          {tab === 'dashboard' && <div className="animate-fade-in space-y-6">
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
              <Stat title="服务状态" value={String(health?.status || 'unknown')} />
              <Stat title="已迁移 Provider" value={visibleProviders.length} />
              <Stat title="可见账号" value={visibleAccounts.length} />
              <Stat title="请求总数" value={String(statistics.totalRequests || 0)} />
            </div>
            <div className="grid gap-5 xl:grid-cols-2">
              <div className="card"><h3 className="mb-4 text-lg font-bold">可用 Provider</h3><div className="grid gap-3">{visibleProviders.map((provider) => <ProviderMini key={provider.id} provider={provider} />)}</div></div>
              <div className="card"><h3 className="mb-4 text-lg font-bold">最近请求</h3><div className="space-y-3">{logs.slice(-5).reverse().map((log) => <LogMini key={log.id} log={log} />)}</div></div>
            </div>
          </div>}

          {tab === 'providers' && <div className="animate-fade-in space-y-5">
            <div className="flex justify-between"><div><h3 className="text-lg font-bold">已迁移 Provider</h3><p className="text-sm text-slate-500">未迁移或跳过的 Provider 不展示。</p></div><button className="btn-primary" onClick={() => saveProviderJson()}>创建 Provider</button></div>
            <div className="grid gap-5 xl:grid-cols-2">{visibleProviders.map((provider) => <article className="card" key={provider.id}><ProviderHero provider={provider} /><div className="mt-5 flex flex-wrap gap-2"><button className="btn-ghost" onClick={() => saveProviderJson(provider)}><Edit3 size={15} />编辑</button><button className="btn-ghost" onClick={() => runAction(() => api.checkProvider(provider.id), '检查完成')}>检查状态</button><button className="btn-ghost" onClick={() => runAction(() => api.refreshProviderModels(provider.id), '模型刷新已触发')}>刷新模型</button><button className="btn-ghost" onClick={() => runAction(() => api.providerCredits(provider.id), '额度查询完成')}>额度</button><button className="btn-ghost" onClick={() => runAction(() => api.clearProviderChats(provider.id), '清空聊天完成')}>清空聊天</button><button className="btn-danger" onClick={() => deleteProvider(provider.id)}><Trash2 size={15} />删除</button></div></article>)}</div>
          </div>}

          {tab === 'accounts' && <TablePanel action={<button className="btn-primary" onClick={() => saveAccountJson()}>创建账号</button>}><table><thead><tr><th>名称</th><th>Provider</th><th>状态</th><th>今日使用</th><th>请求数</th><th>操作</th></tr></thead><tbody>{visibleAccounts.map((account) => <tr key={account.id}><td><b>{account.name}</b><br /><span className="text-xs text-slate-400">{account.email || account.id}</span></td><td>{providerMap.get(account.providerId)?.name || account.providerId}</td><td>{accountBadge(account.status)}</td><td>{account.todayUsed}/{account.dailyLimit || '∞'}</td><td>{account.requestCount}</td><td><div className="flex flex-wrap gap-2"><button className="btn-ghost" onClick={() => saveAccountJson(account)}>编辑</button><button className="btn-ghost" onClick={() => runAction(() => api.validateAccount(account.id), '校验完成')}>校验</button><button className="btn-danger" onClick={() => runAction(() => api.deleteAccount(account.id), '账号已删除')}>删除</button></div></td></tr>)}</tbody></table></TablePanel>}

          {tab === 'keys' && <TablePanel action={<button className="btn-primary" onClick={createKey}>创建 API Key</button>}><table><thead><tr><th>名称</th><th>Key</th><th>状态</th><th>使用次数</th><th>操作</th></tr></thead><tbody>{apiKeys.map((key) => <tr key={key.id}><td>{key.name}</td><td><code className="rounded-lg bg-slate-100 px-2 py-1 text-xs">{key.keyValue}</code></td><td>{key.enabled ? <span className="badge-success">enabled</span> : <span className="badge-neutral">disabled</span>}</td><td>{key.usageCount}</td><td><button className="btn-danger" onClick={() => runAction(() => api.deleteApiKey(key.id), 'API Key 已删除')}>删除</button></td></tr>)}</tbody></table></TablePanel>}

          {tab === 'models' && <TablePanel action={<button className="btn-primary" onClick={() => saveModelMappingJson()}>创建模型映射</button>}><table><thead><tr><th>请求模型</th><th>实际模型</th><th>Provider</th><th>账号</th><th>操作</th></tr></thead><tbody>{mappings.map((mapping) => <tr key={String(mapping.requestModel)}><td>{String(mapping.requestModel)}</td><td>{String(mapping.actualModel)}</td><td>{String(mapping.preferredProviderId || '-')}</td><td>{String(mapping.preferredAccountId || '-')}</td><td><div className="flex gap-2"><button className="btn-ghost" onClick={() => saveModelMappingJson(mapping)}>编辑</button><button className="btn-danger" onClick={() => runAction(() => api.deleteModelMapping(String(mapping.requestModel)), '模型映射已删除')}>删除</button></div></td></tr>)}</tbody></table></TablePanel>}

          {tab === 'sessions' && <TablePanel action={<button className="btn-danger" onClick={() => runAction(() => api.clearSessions(), '会话已清空')}>清空全部会话</button>}><table><thead><tr><th>ID</th><th>Provider</th><th>账号</th><th>模型</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody>{sessions.map((session) => <tr key={session.id}><td>{session.id}</td><td>{session.providerId || '-'}</td><td>{session.accountId || '-'}</td><td>{session.model || '-'}</td><td>{session.status}</td><td>{session.updatedAt}</td><td><button className="btn-danger" onClick={() => runAction(() => api.deleteSession(session.id), '会话已删除')}>删除</button></td></tr>)}</tbody></table></TablePanel>}

          {tab === 'prompts' && <div className="animate-fade-in space-y-5"><div className="flex flex-wrap gap-2"><button className="btn-ghost" onClick={saveToolCallingConfig}>保存 Tool Calling</button><button className="btn-ghost" onClick={saveContextConfig}>保存 Context</button><button className="btn-primary" onClick={() => saveSystemPromptJson()}>创建系统提示</button></div><div className="grid gap-5 xl:grid-cols-2"><ConfigBlock title="Tool Calling" value={toolCalling} /><ConfigBlock title="Context Management" value={contextManagement} /></div><TablePanel><table><thead><tr><th>名称</th><th>分类</th><th>启用</th><th>内置</th><th>内容</th><th>操作</th></tr></thead><tbody>{prompts.map((prompt) => <tr key={prompt.id}><td>{prompt.name}</td><td>{prompt.category || '-'}</td><td>{prompt.enabled ? '是' : '否'}</td><td>{prompt.builtin ? '是' : '否'}</td><td className="max-w-md truncate">{prompt.content}</td><td><div className="flex gap-2"><button className="btn-ghost" onClick={() => saveSystemPromptJson(prompt)}>编辑</button><button className="btn-danger" onClick={() => runAction(() => api.deleteSystemPrompt(prompt.id), '系统提示已删除')}>删除</button></div></td></tr>)}</tbody></table></TablePanel></div>}

          {tab === 'logs' && <TablePanel><table><thead><tr><th>时间</th><th>状态</th><th>模型</th><th>Provider</th><th>账号</th><th>耗时</th><th>错误</th></tr></thead><tbody>{logs.slice(-100).reverse().map((log) => <tr key={log.id}><td>{log.timestamp}</td><td>{log.statusCode} {log.status}</td><td>{log.model}</td><td>{log.providerId}</td><td>{log.accountId}</td><td>{log.latency}ms</td><td>{log.errorMessage || '-'}</td></tr>)}</tbody></table></TablePanel>}

          {tab === 'settings' && <form className="card max-w-3xl space-y-5" onSubmit={updateSettings}><label className="block text-sm font-medium text-slate-600">后端地址<input className="input-field mt-2" value={config.baseUrl} onChange={(event) => setConfig({ ...config, baseUrl: event.target.value })} /></label><label className="block text-sm font-medium text-slate-600">管理员用户名<input className="input-field mt-2" value={config.username} onChange={(event) => setConfig({ ...config, username: event.target.value })} /></label><label className="block text-sm font-medium text-slate-600">管理员密码<input className="input-field mt-2" type="password" value={config.password} onChange={(event) => setConfig({ ...config, password: event.target.value })} /></label><div className="flex flex-wrap gap-2"><button className="btn-primary" type="submit">保存设置</button><button className="btn-ghost" type="button" onClick={exportData}>导出数据</button><button className="btn-ghost" type="button" onClick={importData}>导入数据</button></div></form>}
        </section>
      </main>
    </div>
  )
}

function accountBadge(status: string) {
  const normalized = status.toLowerCase()
  if (normalized === 'active') return <span className="badge-success">active</span>
  if (normalized === 'error' || normalized === 'expired') return <span className="badge-danger">{status}</span>
  return <span className="badge-warning">{status}</span>
}

function Stat({ title, value }: { title: string; value: string | number }) {
  return <article className="stat-card"><p className="stat-label">{title}</p><strong className="stat-value">{value}</strong></article>
}

function ProviderMini({ provider }: { provider: Provider }) {
  return <div className="flex items-center justify-between rounded-2xl bg-slate-50 p-4"><div><b>{provider.name}</b><p className="text-xs text-slate-500">{provider.supportedModels?.join(' / ') || provider.id}</p></div>{provider.enabled ? <span className="badge-success">enabled</span> : <span className="badge-neutral">disabled</span>}</div>
}

function ProviderHero({ provider }: { provider: Provider }) {
  const gradient = provider.id === 'zai' ? 'from-emerald-500 to-teal-500' : 'from-violet-500 to-fuchsia-500'
  return <div><div className="flex items-start justify-between gap-4"><div className="flex items-center gap-4"><div className={`grid h-14 w-14 place-items-center rounded-2xl bg-gradient-to-br ${gradient} text-xl font-black text-white shadow-lg`}>{provider.name.slice(0, 1)}</div><div><h3 className="text-xl font-bold text-slate-900">{provider.name}</h3><p className="text-sm text-slate-500">{provider.id} / {provider.vendor}</p></div></div>{provider.enabled ? <span className="badge-success">enabled</span> : <span className="badge-neutral">disabled</span>}</div><p className="mt-4 break-all rounded-2xl bg-slate-50 p-3 text-sm text-slate-500">{provider.apiEndpoint}{provider.chatPath}</p><div className="mt-3 flex flex-wrap gap-2">{provider.supportedModels?.map((model) => <span key={model} className="badge-neutral">{model}</span>)}</div></div>
}

function LogMini({ log }: { log: RequestLog }) {
  const ok = log.status === 'success' || log.statusCode < 400
  return <div className="flex items-center justify-between rounded-2xl bg-slate-50 p-4"><div><b className="text-sm">{log.model || '-'}</b><p className="text-xs text-slate-500">{log.providerId || '-'} · {log.latency}ms</p></div><span className={ok ? 'badge-success' : 'badge-danger'}>{log.statusCode}</span></div>
}

function TablePanel({ children, action }: { children: React.ReactNode; action?: React.ReactNode }) {
  return <div className="animate-fade-in space-y-4">{action && <div className="flex justify-end">{action}</div>}<div className="table-wrp overflow-x-auto">{children}</div></div>
}

function ConfigBlock({ title, value }: { title: string; value: Record<string, unknown> }) {
  return <div className="card"><h3 className="mb-3 text-lg font-bold">{title}</h3><pre className="max-h-80 overflow-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-100">{JSON.stringify(value, null, 2)}</pre></div>
}

export default App
