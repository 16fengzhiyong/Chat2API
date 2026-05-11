import { useEffect, useState } from 'react'
import { Search, RefreshCw, CheckCircle, XCircle, Users, ChevronRight, Trash2, Edit, RotateCcw, Zap, Plus, Settings } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { api } from '@/api'
import type { Provider, Account } from '@/types'

function useToast() {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)
  const toast = (text: string, ok = true) => { setMsg({ text, ok }); setTimeout(() => setMsg(null), 3000) }
  return { msg, toast }
}

function accountStatusBadgeClass(status: string) {
  switch (status.toUpperCase()) {
    case 'ACTIVE':
      return 'bg-green-100 text-green-700 border-green-200 hover:bg-green-100'
    case 'ERROR':
    case 'EXPIRED':
      return 'bg-red-100 text-red-700 border-red-200 hover:bg-red-100'
    case 'INACTIVE':
      return 'bg-slate-100 text-slate-700 border-slate-200 hover:bg-slate-100'
    default:
      return 'bg-amber-100 text-amber-700 border-amber-200 hover:bg-amber-100'
  }
}

function qwenSettings(provider: Provider) {
  const qwen = provider.settings?.qwen
  if (qwen && typeof qwen === 'object' && !Array.isArray(qwen)) {
    return qwen as Record<string, unknown>
  }
  return {}
}

function qwenThinkingMode(provider: Provider) {
  const value = String(qwenSettings(provider).thinkingMode ?? 'auto')
  return ['auto', 'thinking', 'fast'].includes(value) ? value : 'auto'
}

function qwenRecordMode(provider: Provider) {
  const value = String(qwenSettings(provider).recordMode ?? 'record')
  return value === 'local' ? 'local' : 'record'
}

function AccountPanel({ provider, onClose }: { provider: Provider; onClose: () => void }) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Account | null>(null)
  const [editName, setEditName] = useState('')
  const [editEmail, setEditEmail] = useState('')
  const [saving, setSaving] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      const all = await api.accounts()
      setAccounts(all.filter((a) => a.providerId === provider.id))
    } finally { setLoading(false) }
  }

  useEffect(() => { load() }, [provider.id])

  async function validate(id: string) {
    try {
      await api.validateAccount(id)
      await load()
      toast('账号验证完成')
    } catch { toast('验证失败', false) }
  }

  async function deleteAcc(id: string) {
    if (!confirm('确认删除此账号？')) return
    try {
      await api.deleteAccount(id)
      setAccounts((prev) => prev.filter((a) => a.id !== id))
      toast('账号已删除')
    } catch { toast('删除失败', false) }
  }

  async function saveEdit() {
    if (!editing) return
    setSaving(true)
    try {
      const updated = await api.updateAccount(editing.id, { name: editName, email: editEmail })
      setAccounts((prev) => prev.map((a) => (a.id === updated.id ? updated : a)))
      setEditing(null)
      toast('账号已更新')
    } catch { toast('更新失败', false) }
    finally { setSaving(false) }
  }

  function startEdit(acc: Account) {
    setEditing(acc)
    setEditName(acc.name)
    setEditEmail(acc.email ?? '')
  }

  return (
    <div className="space-y-4">
      {msg && (
        <div className={`fixed top-4 right-4 z-50 px-4 py-2 rounded-md text-sm text-white shadow-lg ${msg.ok ? 'bg-primary' : 'bg-destructive'}`}>
          {msg.text}
        </div>
      )}

      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={onClose} className="text-muted-foreground">
          ← 返回
        </Button>
        <h2 className="font-semibold">{provider.name} 的账号</h2>
        <Button variant="outline" size="sm" className="ml-auto" onClick={load} disabled={loading}>
          <RefreshCw className={`h-3.5 w-3.5 mr-1 ${loading ? 'animate-spin' : ''}`} />刷新
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-4 space-y-2">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-12" />)}</div>
          ) : accounts.length === 0 ? (
            <div className="p-8 text-center text-sm text-muted-foreground">
              <p>此提供商暂无账号</p>
              <p className="mt-1">请通过 Reporter 客户端上报账号</p>
            </div>
          ) : (
            <div className="divide-y">
              {accounts.map((acc) => (
                <div key={acc.id} className="flex items-center gap-4 px-4 py-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm">{acc.name}</span>
                      <Badge
                        variant="outline"
                        className={`text-xs ${accountStatusBadgeClass(acc.status)}`}
                      >
                        {acc.status}
                      </Badge>
                    </div>
                    {acc.email && <p className="text-xs text-muted-foreground">{acc.email}</p>}
                    <div className="flex gap-3 mt-0.5 text-xs text-muted-foreground">
                      <span>总请求: {acc.requestCount}</span>
                      <span>今日: {acc.todayUsed}{acc.dailyLimit ? `/${acc.dailyLimit}` : ''}</span>
                      {acc.lastUsed && <span>最后使用: {new Date(acc.lastUsed).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</span>}
                    </div>
                    {acc.errorMessage && <p className="text-xs text-destructive mt-0.5 truncate">{acc.errorMessage}</p>}
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => validate(acc.id)} title="验证账号">
                      <Zap className="h-3.5 w-3.5" />
                    </Button>
                    <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => startEdit(acc)} title="编辑账号">
                      <Edit className="h-3.5 w-3.5" />
                    </Button>
                    <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => deleteAcc(acc.id)} title="删除账号">
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>编辑账号</DialogTitle></DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>账号名称</Label>
              <Input value={editName} onChange={(e) => setEditName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Email / 备注</Label>
              <Input value={editEmail} onChange={(e) => setEditEmail(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>取消</Button>
            <Button onClick={saveEdit} disabled={saving}>{saving ? '保存中...' : '保存'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export function Providers() {
  const [providers, setProviders] = useState<Provider[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filterEnabled, setFilterEnabled] = useState<boolean | null>(null)
  const [activeProvider, setActiveProvider] = useState<Provider | null>(null)
  const [statusChecking, setStatusChecking] = useState<Set<string>>(new Set())
  const [refreshing, setRefreshing] = useState<Set<string>>(new Set())
  const [qwenEditing, setQwenEditing] = useState<Provider | null>(null)
  const [qwenThinking, setQwenThinking] = useState('auto')
  const [qwenRecord, setQwenRecord] = useState('record')
  const [qwenSaving, setQwenSaving] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      setProviders(await api.providers())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function toggleProvider(p: Provider) {
    try {
      const updated = await api.updateProvider(p.id, { enabled: !p.enabled })
      setProviders((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      toast(`${updated.name} 已${updated.enabled ? '启用' : '禁用'}`)
    } catch { toast('操作失败', false) }
  }

  async function checkStatus(id: string) {
    setStatusChecking((s) => new Set(s).add(id))
    try {
      await api.checkProvider(id)
      await load()
      toast('状态检查完成')
    } catch { toast('检查失败', false) }
    finally { setStatusChecking((s) => { const n = new Set(s); n.delete(id); return n }) }
  }

  async function refreshModels(id: string) {
    setRefreshing((s) => new Set(s).add(id))
    try {
      await api.refreshProviderModels(id)
      await load()
      toast('模型列表已刷新')
    } catch { toast('刷新失败', false) }
    finally { setRefreshing((s) => { const n = new Set(s); n.delete(id); return n }) }
  }

  async function clearChats(id: string) {
    if (!confirm('确认清除此提供商的所有会话？')) return
    try {
      await api.clearProviderChats(id)
      toast('会话已清除')
    } catch { toast('操作失败', false) }
  }

  function openQwenSettings(provider: Provider) {
    setQwenEditing(provider)
    setQwenThinking(qwenThinkingMode(provider))
    setQwenRecord(qwenRecordMode(provider))
  }

  async function saveQwenSettings() {
    if (!qwenEditing) return
    setQwenSaving(true)
    try {
      const settings = {
        ...(qwenEditing.settings || {}),
        qwen: {
          ...qwenSettings(qwenEditing),
          thinkingMode: qwenThinking,
          recordMode: qwenRecord,
        },
      }
      const updated = await api.updateProvider(qwenEditing.id, { ...qwenEditing, settings })
      setProviders((prev) => prev.map((p) => (p.id === updated.id ? updated : p)))
      setQwenEditing(null)
      toast('Qwen 设置已保存')
    } catch { toast('保存失败', false) }
    finally { setQwenSaving(false) }
  }

  const filtered = providers.filter((p) => {
    if (search && !p.name.toLowerCase().includes(search.toLowerCase()) && !p.vendor.toLowerCase().includes(search.toLowerCase())) return false
    if (filterEnabled !== null && p.enabled !== filterEnabled) return false
    return true
  })

  if (activeProvider) {
    return (
      <div className="space-y-4">
        <AccountPanel provider={activeProvider} onClose={() => setActiveProvider(null)} />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {msg && (
        <div className={`fixed top-4 right-4 z-50 px-4 py-2 rounded-md text-sm text-white shadow-lg ${msg.ok ? 'bg-primary' : 'bg-destructive'}`}>
          {msg.text}
        </div>
      )}

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">提供商管理</h1>
          <p className="text-muted-foreground text-sm">管理 AI 服务提供商和账号</p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />刷新
        </Button>
      </div>

      <div className="flex gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input className="pl-9" placeholder="搜索提供商..." value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="flex gap-2">
          <Button
            variant={filterEnabled === null ? 'default' : 'outline'}
            size="sm"
            onClick={() => setFilterEnabled(null)}
          >全部 ({providers.length})</Button>
          <Button
            variant={filterEnabled === true ? 'default' : 'outline'}
            size="sm"
            onClick={() => setFilterEnabled(filterEnabled === true ? null : true)}
          >
            <CheckCircle className="h-3.5 w-3.5 mr-1" />启用 ({providers.filter((p) => p.enabled).length})
          </Button>
          <Button
            variant={filterEnabled === false ? 'default' : 'outline'}
            size="sm"
            onClick={() => setFilterEnabled(filterEnabled === false ? null : false)}
          >
            <XCircle className="h-3.5 w-3.5 mr-1" />禁用 ({providers.filter((p) => !p.enabled).length})
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i}><CardContent className="pt-4"><Skeleton className="h-32" /></CardContent></Card>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <Card>
          <CardContent className="p-8 text-center text-sm text-muted-foreground">
            {search ? `未找到匹配"${search}"的提供商` : '暂无提供商'}
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {filtered.map((p) => (
            <Card key={p.id} className="flex flex-col">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <CardTitle className="text-base leading-none">{p.name}</CardTitle>
                      <Badge variant={p.enabled ? 'default' : 'secondary'} className="text-xs">
                        {p.enabled ? '启用' : '禁用'}
                      </Badge>
                    </div>
                    <div className="flex gap-2 text-xs text-muted-foreground flex-wrap">
                      <span>{p.vendor}</span>
                      <span>·</span>
                      <span>{p.type}</span>
                      {p.description && <><span>·</span><span className="truncate">{p.description}</span></>}
                    </div>
                  </div>
                  <Switch checked={p.enabled} onCheckedChange={() => toggleProvider(p)} />
                </div>
              </CardHeader>
              <Separator />
              <CardContent className="pt-3 pb-3 flex-1">
                {p.supportedModels?.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mb-3">
                    {p.supportedModels.slice(0, 4).map((m) => (
                      <Badge key={m} variant="outline" className="font-mono text-xs py-0">{m}</Badge>
                    ))}
                    {p.supportedModels.length > 4 && (
                      <Badge variant="outline" className="text-xs py-0">+{p.supportedModels.length - 4}</Badge>
                    )}
                  </div>
                )}
                <div className="flex gap-1.5 flex-wrap">
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs"
                    onClick={() => checkStatus(p.id)}
                    disabled={statusChecking.has(p.id)}
                  >
                    {statusChecking.has(p.id) ? <RefreshCw className="h-3 w-3 mr-1 animate-spin" /> : <Zap className="h-3 w-3 mr-1" />}
                    检查状态
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs"
                    onClick={() => refreshModels(p.id)}
                    disabled={refreshing.has(p.id)}
                  >
                    {refreshing.has(p.id) ? <RefreshCw className="h-3 w-3 mr-1 animate-spin" /> : <RotateCcw className="h-3 w-3 mr-1" />}
                    刷新模型
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs text-destructive hover:text-destructive"
                    onClick={() => clearChats(p.id)}
                  >
                    <Trash2 className="h-3 w-3 mr-1" />清除会话
                  </Button>
                  {p.vendor === 'qwen-ai' && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs"
                      onClick={() => openQwenSettings(p)}
                    >
                      <Settings className="h-3 w-3 mr-1" />Qwen 设置
                    </Button>
                  )}
                  <Button
                    variant="default"
                    size="sm"
                    className="h-7 text-xs ml-auto"
                    onClick={() => setActiveProvider(p)}
                  >
                    <Users className="h-3 w-3 mr-1" />账号管理
                    <ChevronRight className="h-3 w-3 ml-1" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <div className="rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
        <Plus className="h-4 w-4 mx-auto mb-1 opacity-40" />
        <p>新提供商需通过 Reporter 客户端上报账号后自动注册</p>
      </div>

      <Dialog open={!!qwenEditing} onOpenChange={(open) => !open && setQwenEditing(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>Qwen 设置</DialogTitle></DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>默认思考模式</Label>
              <Select value={qwenThinking} onValueChange={setQwenThinking}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="auto">自动模式</SelectItem>
                  <SelectItem value="thinking">思考模式</SelectItem>
                  <SelectItem value="fast">快速模式</SelectItem>
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">API 请求携带 enable_thinking 时会覆盖此默认值；未携带时使用这里的设置。</p>
            </div>
            <div className="space-y-2">
              <Label>对话记录模式</Label>
              <Select value={qwenRecord} onValueChange={setQwenRecord}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="record">有记录模式</SelectItem>
                  <SelectItem value="local">无记录模式</SelectItem>
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">有记录模式会在同一 sessionId 下复用 Qwen 会话，无记录模式使用本地临时会话。</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setQwenEditing(null)}>取消</Button>
            <Button onClick={saveQwenSettings} disabled={qwenSaving}>{qwenSaving ? '保存中...' : '保存'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
