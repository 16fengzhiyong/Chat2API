import { useEffect, useState } from 'react'
import { Plus, Trash2, Copy, Eye, EyeOff, RefreshCw } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Separator } from '@/components/ui/separator'
import { api } from '@/api'
import type { ApiKey } from '@/types'

function useToast() {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null)
  const toast = (text: string, ok = true) => {
    setMsg({ text, ok })
    setTimeout(() => setMsg(null), 3000)
  }
  return { msg, toast }
}

export function ApiKeys() {
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [loading, setLoading] = useState(true)
  const [apiKeyEnabled, setApiKeyEnabled] = useState(false)
  const [visibleIds, setVisibleIds] = useState<Set<string>>(new Set())
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const [creating, setCreating] = useState(false)
  const { msg, toast } = useToast()

  async function load() {
    setLoading(true)
    try {
      const [k, cfg] = await Promise.all([
        api.apiKeys(),
        api.getConfig('apiKeyEnabled').catch(() => ({ configValue: 'false' })) as Promise<{ configValue?: string }>,
      ])
      setKeys(k)
      setApiKeyEnabled((cfg as { configValue?: string }).configValue === 'true')
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function toggleGlobal(enabled: boolean) {
    try {
      await api.saveConfig('apiKeyEnabled', String(enabled))
      setApiKeyEnabled(enabled)
      toast(enabled ? 'API 密钥认证已启用' : 'API 密钥认证已禁用')
    } catch {
      toast('操作失败', false)
    }
  }

  async function toggleKey(key: ApiKey) {
    try {
      const updated = await api.updateApiKey(key.id, { enabled: !key.enabled })
      setKeys((prev) => prev.map((k) => (k.id === key.id ? updated : k)))
      toast(`密钥已${updated.enabled ? '启用' : '禁用'}`)
    } catch {
      toast('操作失败', false)
    }
  }

  async function deleteKey(id: string) {
    if (!confirm('确认删除此 API 密钥？')) return
    try {
      await api.deleteApiKey(id)
      setKeys((prev) => prev.filter((k) => k.id !== id))
      toast('密钥已删除')
    } catch {
      toast('删除失败', false)
    }
  }

  async function createKey() {
    if (!newName.trim()) return
    setCreating(true)
    try {
      const key = await api.createApiKey(newName.trim(), newDesc.trim() || undefined)
      setKeys((prev) => [...prev, key])
      setShowCreate(false)
      setNewName('')
      setNewDesc('')
      toast('API 密钥已创建')
    } catch {
      toast('创建失败', false)
    } finally {
      setCreating(false)
    }
  }

  function copyKey(value: string) {
    navigator.clipboard.writeText(value).then(() => toast('已复制到剪贴板'))
  }

  function toggleVisible(id: string) {
    setVisibleIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }

  function maskKey(value: string) {
    if (value.length <= 8) return '****'
    return value.slice(0, 8) + '...' + value.slice(-4)
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
          <h1 className="text-2xl font-bold">API 密钥</h1>
          <p className="text-muted-foreground text-sm">管理访问密钥和全局认证开关</p>
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="h-4 w-4 mr-2" />新建密钥
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">全局 API 密钥认证</CardTitle>
          <CardDescription>启用后，所有 /v1/* 接口请求必须携带有效的 API 密钥</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-3">
            <Switch checked={apiKeyEnabled} onCheckedChange={toggleGlobal} id="global-switch" />
            <Label htmlFor="global-switch">{apiKeyEnabled ? '已启用' : '已禁用'}</Label>
            {apiKeyEnabled && <Badge variant="default">认证开启</Badge>}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-base">密钥列表</CardTitle>
            <CardDescription>{keys.length} 个密钥</CardDescription>
          </div>
          <Button variant="ghost" size="sm" onClick={load} disabled={loading}>
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </CardHeader>
        <Separator />
        <CardContent className="p-0">
          {loading ? (
            <div className="p-4 space-y-3">
              {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-12" />)}
            </div>
          ) : keys.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">
              <p>暂无 API 密钥</p>
              <Button className="mt-4" onClick={() => setShowCreate(true)}>
                <Plus className="h-4 w-4 mr-2" />创建第一个密钥
              </Button>
            </div>
          ) : (
            <div className="divide-y">
              {keys.map((key) => (
                <div key={key.id} className="flex items-center gap-4 px-4 py-3">
                  <div className="flex-1 min-w-0 space-y-0.5">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm truncate">{key.name}</span>
                      <Badge variant={key.enabled ? 'default' : 'secondary'} className="text-xs">
                        {key.enabled ? '启用' : '禁用'}
                      </Badge>
                    </div>
                    <div className="flex items-center gap-1 text-xs text-muted-foreground font-mono">
                      <span>{visibleIds.has(key.id) ? key.keyValue : maskKey(key.keyValue)}</span>
                    </div>
                    {key.description && <p className="text-xs text-muted-foreground">{key.description}</p>}
                  </div>
                  <div className="flex items-center gap-1">
                    <span className="text-xs text-muted-foreground mr-2">用了 {key.usageCount} 次</span>
                    <Switch checked={key.enabled} onCheckedChange={() => toggleKey(key)} />
                    <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => toggleVisible(key.id)}>
                      {visibleIds.has(key.id) ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                    </Button>
                    <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => copyKey(key.keyValue)}>
                      <Copy className="h-3.5 w-3.5" />
                    </Button>
                    <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive" onClick={() => deleteKey(key.id)}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={showCreate} onOpenChange={setShowCreate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建 API 密钥</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>密钥名称 *</Label>
              <Input placeholder="例如：生产环境密钥" value={newName} onChange={(e) => setNewName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>描述（可选）</Label>
              <Input placeholder="简短说明用途" value={newDesc} onChange={(e) => setNewDesc(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreate(false)}>取消</Button>
            <Button onClick={createKey} disabled={creating || !newName.trim()}>
              {creating ? '创建中...' : '创建'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
